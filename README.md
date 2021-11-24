Testing done with https://github.com/andreihava-okta/sample-node-scim-server

#### User functionality
-   Lookup of users - Implemented
-   User Authentication - Implemented only using plaintext password properties file
-   Search and view users in management console - Implemented, currently supports only exact match search by userName
-   Add/Remove users - Implemented - sample-node-scim-server only provides Deactivate user API
-   Modify User Attributes - Implemented, modifying username not yet supported
-   Automated/Manual Sync from SCIM to Keycloak - Not implemented

####  Groups functionality
-   Current behavior: When a federated SCIM user logs in, this user's groups are added into keycloak.

####  Setup:

- Deploy keycloak plugin
~~~
	KEYCLOAK_PATH=/path/to/keycloak/keycloak-13.0.1 ./redeploy-plugin.sh
~~~

- Download and run [keycloak](https://github.com/keycloak/keycloak#getting-started)

- Login to Keycloak Admin Console (http://keycloak-server:8080)

- Under User Federation -> Add the `scim` provider

- In the provider settings, provide a SCIM Server URL (`scimserver.example.com:8081`).

- Click **Save**. You should see a notice that the provider has been created.

#### Implementation
* This plugin performs JAX_RS HTTP communication which can be manually tested with `curl`:

* Create user
~~~
    $ curl 'http://localhost:8080/scim/v2/Users' -d @create_scim_user.json -H "Content-Type: application/scim+json"
~~~

    * Where `create_scim_user.json` is:
    ~~~
	 {
	  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
	  "userName": "jstephenson@example.com",
	  "name":
	  {
	    "givenName": "justin",
	    "middleName": "m",
	    "familyName": "stephenson"
	  },
	  "emails":
	  [{
	    "primary": true,
	    "value": "jstephenson@example.com",
	    "type": "work"
	  }],
	  "displayName": "jstephenson",
	  "externalId": "extId",
	  "groups": [],
	  "active": true
	}
	~~~

* Query users with username filter:
~~~
    $ curl -vvv -k "localhost:8081/scim/v2/Users?count=1&filter=userName+eq+"jstephenson@example.com"&startIndex=1" | json_pp
~~~
* Retrieve user info, where $id is the id field returned from the user creation request (ex: 5a8a9281-821f-4d56-b206-af86c4e94893)
~~~
    $ curl 'http://localhost:8080/scim/v2/Users/$id' | json_pp
~~~
* Retrieve complete user list:
~~~
	$ curl -vvv -k "localhost:8081/scim/v2/Users?count=100&startIndex=1" | json_pp
~~~
* Update user -- email, firstname, lastname
1. Search by username
2. Get users ID
3. PUT with complete set of user (updated) attributes
~~~
$ curl- X PUT -vvv 'http://localhost:8081/scim/v2/Users/2a11aa50-ebb9-11eb-a32b-6b720700a082' -d @create_scim_user_updated.json -H "Content-Type: application/scim+json"
~~~
