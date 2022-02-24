Testing done with django-scim2 server

#### Initial Authentication

This plugin authenticates with a CSRF token similar to [How to cURL an Authenticated Django App?](https://stackoverflow.com/questions/21306515/how-to-curl-an-authenticated-django-app)

#### User functionality
-   Lookup of users - Implemented
-   User Authentication - Implemented only using plaintext password properties file
-   Search and view users in management console - Implemented, currently supports only exact match search by userName
-   Add new users - Implemented
-   Delete users - Implemented
-   Modify User Attributes - Implemented, modifying username not yet supported
-   Automated/Manual Sync from SCIM to Keycloak - Not implemented

####  Groups functionality
-   Current behavior: When a federated SCIM user logs in, this user's groups are added into keycloak.

####  Setup (bare metal local install):

- Deploy keycloak plugin
~~~
	KEYCLOAK_PATH=/path/to/keycloak/keycloak-13.0.1 ./redeploy-plugin.sh
~~~

- Download and run [keycloak](https://github.com/keycloak/keycloak#getting-started)

- Login to Keycloak Admin Console (http://keycloak-server:8080)

- Under User Federation -> Add the `scim` provider

- In the provider settings, provide

  * a SCIM Server URL (`scimserver.example.com:8081`).
  * Django username and password

- Click **Save**. You should see a notice that the provider has been created.

#### Implementation
* This plugin performs JAX_RS HTTP communication equivalent to the following `curl` commands:

* Create user (POST)
~~~
    $ curl -b cookies.txt -X POST -d @create_scim_user_jstephenson.json "http://127.0.0.1:8000/scim/v2/Users"
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

* Query users with username filter (POST):
~~~
$ curl -b cookies.txt -e http://127.0.0.1:8000/admin/login/?next=/admin/ -X POST -d @filter_jstephen.json "http://127.0.0.1:8000/scim/v2/Users/.search"
~~~

    * Where `filter_jstephen.json` is
    ~~~
    {
        "schemas": ["urn:ietf:params:scim:api:messages:2.0:SearchRequest"],
        "filter": "userName eq \"jstephen\""
    }
    ~~~
* Retrieve user info, where $id is the id field returned from the user creation request (ex: 6) (GET)
~~~
    $ curl -b cookies.txt http://localhost:8080/scim/v2/Users/$id
~~~
* Retrieve complete user list:
~~~
	$ curl -b cookies.txt -X GET "http://127.0.0.1:8000/scim/v2/Users"
~~~
* Update user -- email, firstname, lastname
1. Search by username
2. Get users ID
3. PUT with complete set of user (updated) attributes
~~~
$ curl -b cookies.txt -X PUT -d @create_scim_updated_user_jstephenson.json "http://127.0.0.1:8000/scim/v2/Users/$id"
~~~

* Where `create_scim_user_updated.json` is
~~~
$ {
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "jstephenson@example.com",
  "name":
  {
    "givenName": "justinnn",
    "middleName": "mmm",
    "familyName": "stephensonnn"
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
~~~

* Delete user (DELETE)
~~~
$ curl -c cookies.txt -b cookies.txt -vvv -X DELETE http://127.0.0.1:8000/scim/v2/Users/$id
~~~