package keycloak.scim_user_spi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.UserModelDelegate;

import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class SCIMUserModelDelegate extends UserModelDelegate {

	private static final Logger logger = LogManager.getLogger(SCIMUserModelDelegate.class);

	private ComponentModel model;

	public SCIMUserModelDelegate(UserModel delegate, ComponentModel model) {
		super(delegate);
		this.model = model;
	}

	@Override
	public void setAttribute(String attr, List<String> values) {
		Scim scim = new Scim(model);
		if (scim.clientAuthLogin() == null) {
			logger.error("Login error");
		}

		Response resp = scim.updateUser(this.getUsername(), attr, values);
		if (resp.getStatus() != Status.OK.getStatusCode() &&
			resp.getStatus() != Status.NO_CONTENT.getStatusCode()) {
			logger.warn("Unexpected PUT status code returned");
			resp.close();
			return;
		}
		resp.close();
		super.setAttribute(attr, values);
	}

    @Override
    public void setSingleAttribute(String name, String value) {
        super.setSingleAttribute(name, value);
    }
	@Override
	public void setUsername(String username) {
		super.setUsername(username);
	}

	@Override
	public void setLastName(String lastName) {
		super.setLastName(lastName);
	}

	@Override
	public void setFirstName(String first) {
		super.setFirstName(first);
	}

	@Override
	public void setEmail(String email) {
		super.setFirstName(email);
	}
}
