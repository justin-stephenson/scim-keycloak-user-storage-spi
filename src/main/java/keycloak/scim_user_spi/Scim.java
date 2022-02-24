package keycloak.scim_user_spi;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.keycloak.component.ComponentModel;

import keycloak.scim_user_spi.schemas.SCIMSearchRequest;
import keycloak.scim_user_spi.schemas.SCIMUser;
import keycloak.scim_user_spi.schemas.SCIMUser.Resource;


public class Scim {
	public enum Method {
		GET,
		DELETE,
		PUT,
		POST
	}

	private static final Logger logger = LogManager.getLogger(Scim.class);

	private ComponentModel model;
	private SCIMUser user;
	public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
	public static final String SCHEMA_API_MESSAGES_SEARCHREQUEST = "urn:ietf:params:scim:api:messages:2.0:SearchRequest";

	String csrf;
	String session_id;

	public Scim(ComponentModel model) {
		this.model = model;
	}

	private String buildURL(String endpoint, Boolean admin) {
		String server = model.getConfig().getFirst("scimurl");

		if (admin) {
			/* http://127.0.0.1:8000/$endpoint */
			return String.format("http://%s%s", server, endpoint);
		}
		String baseurl = String.format("http://%s/scim/v2/", server);
		/* http://127.0.0.1:8000/scim/v2/$endpoint */
		String finalurl= baseurl.concat(endpoint);

		return finalurl;
	}

	public Integer clientAuthLogin() {
		String username = model.getConfig().getFirst("loginusername");
		String pw = model.getConfig().getFirst("loginpassword");

		/* Get login url */
		Response response;
		try {
			response = clientRequest("/admin/", Scim.Method.GET, null, true);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		String loginPage = response.getLocation().toString();

		response.close();

		/* Retrieve csrf cookie from server */
		Response login;
		try {
			login = clientRequest(loginPage, Scim.Method.GET, null, true);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
		Map<String, NewCookie> cookies;
		cookies = login.getCookies();

		this.csrf = cookies.get("csrftoken").getValue();

		login.close();
		cookies.clear();

		Form form = new Form().param("csrfmiddlewaretoken",  this.csrf).param("username", username).param("password", pw);

		Entity<Form> payload = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);

		/* Perform login POST with data, csrf and sessionid cookies are returned */
		Response login_response;
		try {
			login_response = clientRequest(loginPage, Method.POST, payload, true);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		/* Bad username/password */
		cookies = login_response.getCookies();
		if (cookies.get("csrftoken") == null || cookies.get("sessionid") == null) {
			return null;
		}

		this.csrf = cookies.get("csrftoken").getValue();
		this.session_id = cookies.get("sessionid").getValue();

		login_response.close();

		return 0;
	}

	/* Expect Entity<Form> or Entity<Resource>, Form is used for initial auth */
	public <T> Response clientRequest(String endpoint, Method method, Entity<T> entity, Boolean admin) throws Exception {
		Client client = ClientBuilder.newClient();
		Response response = null;
		String header = "";

		String endpointurl = buildURL(endpoint, admin);

		logger.info(String.format("Sending %s request to [%s]", method.toString(), endpointurl));

		WebTarget target = client.target(endpointurl).property(endpoint, endpointurl);

		Builder builder = target.request();

		/* Add cookies */
		if (this.csrf != null && this.session_id != null) {
			header = String.format("csrftoken=%s; sessionid=%s", this.csrf, this.session_id);
		} else if (this.csrf != null) {
			header = String.format("csrftoken=%s", this.csrf);
		} else if (this.session_id != null) {
			header = String.format("sessionid=%s", this.session_id);
		}

		try {
			switch (method) {
			case GET:
				response = builder.header("Cookie", header).get();
				break;
			case DELETE:
				response = builder.header("Cookie", header).delete();
				break;
			case POST:
				response = builder.header("Cookie", header).post(entity);
				break;
			case PUT:
				response = builder.header("Cookie", header).put(entity);
				break;
			}
		} catch (Exception e) {
			throw new Exception();
		}

		return response;
	}

	private SCIMSearchRequest setupSearch(String username, String attribute) {
		List<String> schemas = new ArrayList<String>();
		SCIMSearchRequest search = new SCIMSearchRequest();
		String filter;

		schemas.add(SCHEMA_API_MESSAGES_SEARCHREQUEST);
		search.setSchemas(schemas);

		filter = String.format("%s eq \"%s\"", attribute, username);
		search.setFilter(filter);
		logger.info(String.format("filter: %s", filter));
		logger.info(String.format("Schema: %s",  SCHEMA_API_MESSAGES_SEARCHREQUEST));

		return search;
	}

	private SCIMUser getUserByAttr(String username, String attribute) {
		SCIMSearchRequest newSearch = setupSearch(username, attribute);
		Entity<SCIMSearchRequest> payload = Entity.entity(newSearch, "application/scim+json");

		String usersSearchUrl = "Users/.search";

		Response response;
		try {
			response = clientRequest(usersSearchUrl, Method.POST, payload, false);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		SCIMUser user = response.readEntity(SCIMUser.class);

		response.close();
		return user;
	}

	public SCIMUser getUserByUsername(String username) {
		String attribute = "userName";
		return getUserByAttr(username, attribute);
	}

	public SCIMUser getUserByEmail(String username) {
		String attribute = "emails.value";
		return getUserByAttr(username, attribute);
	}

	public SCIMUser getUserByFirstName(String username) {
		String attribute = "name.givenName";
		return getUserByAttr(username, attribute);
	}

	public SCIMUser getUserByLastName(String username) {
		String attribute = "name.familyName";
		return getUserByAttr(username, attribute);
	}

	public Response deleteUser(String username) {
		SCIMUser userobj = getUserByUsername(username);
		SCIMUser.Resource user = userobj.getResources().get(0);

		String userIdUrl = String.format("Users/%s", user.getId());

		Response response;
		try {
			response = clientRequest(userIdUrl, Method.DELETE, null, false);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		response.close();

		return response;
	}

	/* Keycloak UserRegistrationProvider addUser() method only provides username as input,
	 * here we provide mostly dummy values which will be replaced by actual user input via
	 * appropriate setter methods once in the returned UserModel
	 */
	private SCIMUser.Resource setupUser(String username) {
		SCIMUser.Resource user = new SCIMUser.Resource();
		SCIMUser.Resource.Name name = new SCIMUser.Resource.Name();
		SCIMUser.Resource.Email email = new SCIMUser.Resource.Email();
		List<String> schemas = new ArrayList<String>();
		List<SCIMUser.Resource.Email> emails = new ArrayList<SCIMUser.Resource.Email>();
		List<SCIMUser.Resource.Group> groups = new ArrayList<SCIMUser.Resource.Group>();

		schemas.add(SCHEMA_CORE_USER);
		user.setSchemas(schemas);
		user.setUserName(username);
		user.setActive(true);
		user.setGroups(groups);


		name.setGivenName("");
		name.setMiddleName("");
		name.setFamilyName("");
		user.setName(name);

		email.setPrimary(true);
		email.setType("work");
		email.setValue("dummy@example.com");
		emails.add(email);
		user.setEmails(emails);

		return user;
	}

	public Response createUser(String username) {
		String usersUrl = "Users";

		SCIMUser.Resource newUser = setupUser(username);

		Entity<Resource> payload = Entity.entity(newUser, "application/scim+json");

		Response response;
		try {
			response = clientRequest(usersUrl, Method.POST, payload, false);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		response.close();
		return response;
	}

	private void setUserAttr(SCIMUser.Resource user, String attr, String value) {
		SCIMUser.Resource.Name name = user.getName();
		SCIMUser.Resource.Email email = new SCIMUser.Resource.Email();
		List<SCIMUser.Resource.Email> emails = new ArrayList<SCIMUser.Resource.Email>();

		switch (attr) {
		case "firstName":
			name.setGivenName(value);
			user.setName(name);
			break;
		case "lastName":
			name.setFamilyName(value);
			user.setName(name);
			break;
		case "email":
			email.setValue(value);
			emails.add(email);
			user.setEmails(emails);
			break;
		case "userName":
			// FIXME: Support changing username?
			break;
		default:
			logger.info("Unknown user attribute to set: " + attr);
			break;
		}
	}

	public Response updateUser(String username, String attr, List<String> values) {
		logger.info(String.format("Updating %s attribute for %s", attr, username));
		/* Get existing user */
		Scim scim = new Scim(model);
		if (scim.clientAuthLogin() == null) {
			logger.error("Login error");
		}

		SCIMUser userobj = getUserByUsername(username);
		SCIMUser.Resource user = userobj.getResources().get(0);

		/* Modify attributes */
		setUserAttr(user, attr, values.get(0));

		/* Update user in SCIM */
		String modifyUrl = String.format("Users/%s", user.getId());
		Entity<Resource> payload = Entity.entity(user, "application/scim+json");

		Response response;
		try {
			response = clientRequest(modifyUrl, Method.PUT, payload, false);
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		response.close();

		return response;
	}

	public boolean getActive(SCIMUser user) {
		return Boolean.valueOf(user.getResources().get(0).getActive());
	}

	public String getEmail(SCIMUser user) {
		return user.getResources().get(0).getEmails().get(0).getValue();
	}

	public String getFirstName(SCIMUser user) {
		return user.getResources().get(0).getName().getGivenName();
	}

	public String getLastName(SCIMUser user) {
		return user.getResources().get(0).getName().getFamilyName();
	}

	public String getUserName(SCIMUser user) {
		return user.getResources().get(0).getUserName();
	}

	public String getId(SCIMUser user) {
		return user.getResources().get(0).getId();
	}

	public List<String> getGroupsList(SCIMUser user) {
		List<SCIMUser.Resource.Group> groups = new ArrayList<SCIMUser.Resource.Group>();
		List<String> groupnames = new ArrayList<String>();
		groups = user.getResources().get(0).getGroups();

		for (int i = 0; i < groups.size(); i++) {
			logger.info("Retrieving group: " + groups.get(i).getDisplay());
			groupnames.add(groups.get(i).getDisplay());
		}

		return groupnames;
	}
}