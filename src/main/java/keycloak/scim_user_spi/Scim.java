package keycloak.scim_user_spi;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.keycloak.component.ComponentModel;

import keycloak.scim_user_spi.SCIMUser.Resource;


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

	public Scim(ComponentModel model) {
		this.model = model;
	}


	public Response clientRequest(String url, Method method, Entity<Resource> entity) {
		Client client = ClientBuilder.newClient();
		Response response = null;

		String server = model.getConfig().getFirst("scimurl");
		String baseurl = String.format("http://%s/scim/v2/", server);
		String scimurl = baseurl.concat(url);

		logger.info(String.format("Sending request to [%s]", scimurl));

		WebTarget target = client.target(scimurl);
		switch (method) {
		case GET:
			response = target.request().get();
			break;
		case DELETE:
			response = target.request().delete();
			break;
		case POST:
			response = target.request().post(entity);
			break;
		case PUT:
			response = target.request().put(entity);
			break;
		}

		client.close();
		return response;
	}

	private SCIMUser getUserByAttr(String username, String attribute) {
		String userGetUrl = String.format("Users?count=1&filter=%s+eq+%s&startIndex=1", attribute, username);
		Response response = clientRequest(userGetUrl, Method.GET, null);

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

	public Response deleteUser(String username, String id) {
		String userIdUrl = String.format("Users/%s", id);

		Response response = clientRequest(userIdUrl, Method.DELETE, null);

		response.close();

		return response;
	}

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
		user.setActive("true");
		user.setGroups(groups);

		name.setGivenName("");
		name.setMiddleName("");
		name.setFamilyName("");
		user.setName(name);

		email.setPrimary(true);
		email.setType("work");
		email.setValue(username);
		emails.add(email);
		user.setEmails(emails);

		return user;
	}

	public Response createUser(String username) {
		String usersUrl = "Users";

		SCIMUser.Resource newUser = setupUser(username);
		Entity<Resource> payload = Entity.entity(newUser, "application/scim+json");

		Response response = clientRequest(usersUrl, Method.POST, payload);

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
		// Get existing user
		Scim scim = new Scim(model);

		SCIMUser userobj = this.getUserByUsername(username);
		SCIMUser.Resource user = userobj.getResources().get(0);

		// Modify attributes
		setUserAttr(user, attr, values.get(0));

		// Update user in SCIM
		String modifyUrl = String.format("Users/%s", user.getId());
		Entity<Resource> payload = Entity.entity(user, "application/scim+json");

		Response response = clientRequest(modifyUrl, Method.PUT, payload);

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