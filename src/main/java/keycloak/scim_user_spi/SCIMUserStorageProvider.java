/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keycloak.scim_user_spi;

import org.jboss.logging.Logger;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.ImportedUserValidation;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import org.keycloak.broker.provider.util.SimpleHttp;

import keycloak.scim_user_spi.schemas.SCIMError;
import keycloak.scim_user_spi.schemas.SCIMUser;

import org.keycloak.storage.user.UserQueryProvider;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;

/**
 * @author <a href="mailto:jstephen@redhat.com">Justin Stephenson</a>
 * @version $Revision: 1 $
 */
public class SCIMUserStorageProvider implements
UserStorageProvider,
UserLookupProvider.Streams,
CredentialInputValidator,
UserRegistrationProvider,
UserQueryProvider.Streams,
ImportedUserValidation
{
	protected KeycloakSession session;
	protected ComponentModel model;
	protected Scim scim;
	private static final Logger logger = Logger.getLogger(SCIMUserStorageProvider.class);
	protected final Set<String> supportedCredentialTypes = new HashSet<>();

	public SCIMUserStorageProvider(KeycloakSession session, ComponentModel model, Scim scim) {
		this.session = session;
		this.model = model;
		this.scim = scim;

		supportedCredentialTypes.add(PasswordCredentialModel.TYPE);
	}

	@Override
	public UserModel getUserByEmail(RealmModel realm, String email) {
		return null;
	}

	@Override
	public UserModel getUserById(RealmModel realm, String id) {
		StorageId storageId = new StorageId(id);
		String username = storageId.getExternalId();
		return getUserByUsername(realm, username);
	}

	@Override
	public UserModel getUserByUsername(RealmModel realm, String username) {
		UserModel user = session.userLocalStorage().getUserByUsername(realm,  username);
		if (user != null) {
			logger.info("User already exists in keycloak");
			return user;
		} else {
			return createUserInKeycloak(realm, username);
		}
	}

	protected UserModel createUserInKeycloak(RealmModel realm, String username) {
		Scim scim = this.scim;

		SCIMUser scimuser = scim.getUserByUsername(username);
		if (scimuser.getTotalResults() == 0) {
			return null;
		}
		UserModel user = session.userLocalStorage().addUser(realm,  username);
		user.setEmail(scim.getEmail(scimuser));
		user.setFirstName(scim.getFirstName(scimuser));
		user.setLastName(scim.getLastName(scimuser));
		user.setFederationLink(model.getId());
		user.setEnabled(scim.getActive(scimuser));

		for (String name : scim.getGroupsList(scimuser)) {
			Stream<GroupModel> groupsStream = session.groupLocalStorage().searchForGroupByNameStream(realm, name, null, null);
			GroupModel group = groupsStream.findFirst().orElse(null);

			if (group == null) {
				logger.infov("No group found, creating group: {0}", name);
				group = session.groups().createGroup(realm, name);
			}
			user.joinGroup(group);
		}

		logger.infov("Creating SCIM user {0} in keycloak", username);
		return new SCIMUserModelDelegate(user, model);
	}

	@Override
	public void close() {

	}

	public Set<String> getSupportedCredentialTypes() {
		return new HashSet<String>(this.supportedCredentialTypes);
	}

	// CredentialInputValidator methods
	@Override
	public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
		return getSupportedCredentialTypes().contains(credentialType);
	}

	@Override
	public boolean supportsCredentialType(String credentialType) {
		return getSupportedCredentialTypes().contains(credentialType);
	}

	@Override
	public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
		if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) return false;

		UserCredentialModel cred = (UserCredentialModel)input;
		/* The password can either be validated locally in keycloak (tried first)
		   or in the SCIM server */
		if (session.userCredentialManager().isConfiguredLocally(realm, user, input.getType())) {
			logger.debugv("Local password validation for {0}", user.getUsername());
			// return false in order to fallback to the next validator
			return false;
		} else {
			logger.debugv("Delegated password validation for {0}", user.getUsername());
			Scim scim = this.scim;
			return scim.isValid(user.getUsername(), input.getChallengeResponse());
		}
	}

	// ImportedUserValidation methods
	@Override
	public UserModel validate(RealmModel realm, UserModel local) {
		Scim scim = this.scim;

		SCIMUser scimuser = scim.getUserByUsername(local.getUsername());
		String fname = scim.getFirstName(scimuser);
		String lname = scim.getLastName(scimuser);
		String email = scim.getEmail(scimuser);

		if (!local.getFirstName().equals(fname)) {
			local.setFirstName(fname);
		}
		if (!local.getLastName().equals(lname)) {
			local.setLastName(lname);
		}
		if (!local.getEmail().equals(email)) {
			local.setEmail(email);
		}

		return new SCIMUserModelDelegate(local, model);
	}

	// UserRegistrationProvider methods
	@Override
	public UserModel addUser(RealmModel realm, String username) {
		Scim scim = this.scim;

		SimpleHttp.Response resp = scim.createUser(username);

		try {
			if (resp.getStatus() != HttpStatus.SC_CREATED) {
				logger.warn("Unexpected create status code returned");
				SCIMError error = resp.asJson(SCIMError.class);
				logger.warn(error.getDetail());
				resp.close();
				return null;
			}
			resp.close();
		} catch (IOException e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		return createUserInKeycloak(realm, username);
	}

	@Override
	public boolean removeUser(RealmModel realm, UserModel user) {
		logger.infov("Removing user: {0}", user.getUsername());
		Scim scim = this.scim;

		SimpleHttp.Response resp = scim.deleteUser(user.getUsername());
		Boolean status = false;
		try {
			status = resp.getStatus() == HttpStatus.SC_NO_CONTENT;
			resp.close();
		} catch (IOException e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}
		return status;
	}

	// UserQueryProvider methods
	//
	// FIXME: Add support for substring filter "co" matches
	// Only supports searching by exact/complete userName, not partial

	// FIXME: Add support for email, fname, lastname searching
	// Only supports searching by userName (not email, firstname, lastname)
	// update: Partial support added in performSearch(), searchForUser map method needs to
	// be updated, tested

	// FIXME: handle firstResult, maxResults
	private Stream<UserModel> performSearch(RealmModel realm, String search) {
		List<UserModel> users = new LinkedList<>();
		Scim scim = this.scim;

		SCIMUser scimuser = scim.getUserByUsername(search);
		if (scimuser.getTotalResults() > 0) {
			logger.info("User found by username!");
			if (session.userLocalStorage().getUserByUsername(realm, search) == null) {
				UserModel user = getUserByUsername(scim.getUserName(scimuser), realm);
				users.add(user);
			} else {
				logger.info("User exists!");
			}

			return users.stream();
		}

		//		results = scim.getUserByEmail(search);
		//		if (results > 0) {
		//			logger.info("User found by email!");
		//	        UserModel user = getUserByUsername(scim.getUserName(), realm);
		//	        users.add(user);
		//	        return users;
		//		}
		//
		//		results = scim.getUserByFirstName(search);
		//		if (results > 0) {
		//			logger.info("User found by first name!");
		//	        UserModel user = getUserByUsername(scim.getUserName(), realm);
		//	        users.add(user);
		//	        return users;
		//		}
		//
		//		results = scim.getUserByLastName(search);
		//		if (results > 0) {
		//			logger.info("User found by last name!");
		//	        UserModel user = getUserByUsername(scim.getUserName(), realm);
		//	        users.add(user);
		//	        return users;
		//		}

		return users.stream();
	}

	// FIXME: Add support for managing federated users? Or mass import (see LDAP federation plugin)
	// See https://www.keycloak.org/docs/latest/server_admin/#user-management
	@Override
	public Stream<UserModel> getGroupMembersStream(RealmModel arg0, GroupModel arg1, Integer arg2, Integer arg3) {
		return Stream.empty();
	}

	@Override
	public int getUsersCount(RealmModel realm) {
		Scim scim = this.scim;

		SCIMUser user = null;
		SimpleHttp.Response response;
		try {
			response = scim.clientRequest("/Users", "GET", null);
			user = response.asJson(SCIMUser.class);
			response.close();
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		return user.getTotalResults();
	}

	@Override
	public Stream<UserModel> getUsersStream(RealmModel arg0, Integer arg1, Integer arg2) {
		return Stream.empty();
	}

	@Override
	public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
		return Stream.empty();
	}

	@Override
	public Stream<UserModel> searchForUserStream(RealmModel realm, String search, Integer firstResult, Integer maxResults) {
		return performSearch(realm, search);
	}

	@Override
	public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult,
			Integer maxResults) {
		/* only supports searching by username */
		String usernameSearchString = params.get("username");
		if (usernameSearchString == null) return Stream.empty();
		return searchForUserStream(realm, usernameSearchString, firstResult, maxResults);
	}

}
