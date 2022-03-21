package keycloak.scim_user_spi;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.component.ComponentModel;

import org.keycloak.broker.provider.util.SimpleHttp;

import keycloak.scim_user_spi.schemas.SCIMSearchRequest;
import keycloak.scim_user_spi.schemas.SCIMUser;


public class Scim {
	private static final Logger logger = Logger.getLogger(Scim.class);

	private ComponentModel model;
	public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
	public static final String SCHEMA_API_MESSAGES_SEARCHREQUEST = "urn:ietf:params:scim:api:messages:2.0:SearchRequest";

	String session_id;
	String username;
	String password;
	CloseableHttpClient httpclient;
	Boolean logged_in = false;

	public Scim(ComponentModel model) {
		this.model = model;
	}

	public Integer csrfAuthLogin() {
		String url = "";
		String loginPage = "";
		SimpleHttp.Response response = null;

		/* Create cookie store */
		CookieStore cookieStore = new BasicCookieStore();

		/* Create client */
		CloseableHttpClient httpclient = HttpClients.custom()
				.setDefaultCookieStore(cookieStore)
				.setDefaultRequestConfig(RequestConfig.custom()
						.setRedirectsEnabled(false)
						.setCookieSpec(CookieSpecs.STANDARD)
						.build()).build();
		this.httpclient = httpclient;

		/* Get inputs */
		String server = model.getConfig().getFirst("scimurl");
		String username = model.getConfig().getFirst("loginusername");
		String password = model.getConfig().getFirst("loginpassword");

		/* Get Location redirect url */
		url = String.format("http://%s%s", server, "/admin/");

		try {
			response = SimpleHttp.doGet(url, this.httpclient).asResponse();

			loginPage = response.getFirstHeader("Location");
			response.close();
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		/* Execute GET to get initial csrftoken */
		url = String.format("http://%s%s", server, loginPage);

		try {
			response = SimpleHttp.doGet(url, this.httpclient).asResponse();
			response.close();
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}


		/* Store the Response csrftoken cookie */
		String csrf = null;

		for (Cookie co: cookieStore.getCookies()) {
			if (co.getName().contains("csrftoken")) {
				csrf = co.getValue();
				cookieStore.addCookie(co);
			}
		}

		/* Perform login POST */
		HashMap<String, String> headers = new HashMap<String, String>();

		headers.put("X-CSRFToken", csrf);

		try {
			response = SimpleHttp.doPost(url, this.httpclient).header("X-CSRFToken", csrf).param("username",  username).param("password",  password).asResponse();
			response.close();
		} catch (Exception e) {
			logger.error("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}

		/* Store the Response sessionid and new csrftoken cookie */
		for (Cookie co: cookieStore.getCookies()) {
			if (co.getName().contains("csrftoken") || co.getName().contains("sessionid")) {
				cookieStore.addCookie(co);
			}
		}

		this.logged_in = true;
		return 0;

	}

	public <T> SimpleHttp.Response clientRequest(String endpoint, String method, T entity) throws Exception {
		SimpleHttp.Response response = null;

		if (this.logged_in == false) {
			this.csrfAuthLogin();
		}

		/* Build URL */
		String server = model.getConfig().getFirst("scimurl");
		String endpointurl = String.format("http://%s/scim/v2/%s", server, endpoint);

		logger.infov("Sending {0} request to {1}", method.toString(), endpointurl);

		try {
			switch (method) {
			case "GET":
				response = SimpleHttp.doGet(endpointurl, this.httpclient).asResponse();
				break;
			case "DELETE":
				response = SimpleHttp.doDelete(endpointurl, this.httpclient).asResponse();
				break;
			case "POST":
				response = SimpleHttp.doPost(endpointurl, this.httpclient).json(entity).asResponse();
				break;
			case "PUT":
				response = SimpleHttp.doPut(endpointurl, this.httpclient).json(entity).asResponse();
				break;
			default:
				logger.warn("Unknown HTTP method, skipping");
				break;
			}
		} catch (Exception e) {
			throw new Exception();
		}

		/* Caller is responsible for executing .close() */
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
		logger.infov("filter: {0}", filter);
		logger.infov("Schema: {0}",  SCHEMA_API_MESSAGES_SEARCHREQUEST);

		return search;
	}

	private SCIMUser getUserByAttr(String username, String attribute) {
		SCIMSearchRequest newSearch = setupSearch(username, attribute);

		String usersSearchUrl = "Users/.search";
		SCIMUser user = null;

		SimpleHttp.Response response;
		try {
			response = clientRequest(usersSearchUrl, "POST", newSearch);
			user = response.asJson(SCIMUser.class);
			response.close();
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

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

	public SimpleHttp.Response deleteUser(String username) {
		SCIMUser userobj = getUserByUsername(username);
		SCIMUser.Resource user = userobj.getResources().get(0);

		String userIdUrl = String.format("Users/%s", user.getId());

		SimpleHttp.Response response;
		try {
			response = clientRequest(userIdUrl, "DELETE", null);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

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

	public SimpleHttp.Response createUser(String username) {
		String usersUrl = "Users";

		SCIMUser.Resource newUser = setupUser(username);

		SimpleHttp.Response response;
		try {
			response = clientRequest(usersUrl, "POST", newUser);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			return null;
		}

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

	public SimpleHttp.Response updateUser(String username, String attr, List<String> values) {
		logger.info(String.format("Updating %s attribute for %s", attr, username));
		/* Get existing user */
		Scim scim = new Scim(model);

		if (scim.csrfAuthLogin() == null) {
			logger.error("Error during login");
		}

		SCIMUser userobj = getUserByUsername(username);
		SCIMUser.Resource user = userobj.getResources().get(0);

		/* Modify attributes */
		setUserAttr(user, attr, values.get(0));

		/* Update user in SCIM */
		String modifyUrl = String.format("Users/%s", user.getId());

		SimpleHttp.Response response;
		try {
			response = clientRequest(modifyUrl, "PUT", user);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

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