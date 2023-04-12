Testing done with django-scim2 server

#### Initial Authentication

This plugin authenticates with a CSRF token using the java Apache HTTP client equivalent of
~~~
COOKIEJAR="cookies.txt"
rm -f $COOKIEJAR
curl -c $COOKIEJAR http://127.0.0.1:8000/admin/login/?next=/admin/ -vvv

DJANGO_TOKEN="$(grep csrftoken $COOKIEJAR | sed 's/^.*csrftoken\s*//')"
curl -b $COOKIEJAR -c $COOKIEJAR http://127.0.0.1:8000/admin/login/?next=/admin/ -H "X-CSRFToken: $DJANGO_TOKEN" -d "username=admin&password=redhat" -X POST -vvv

curl -c $COOKIEJAR -b $COOKIEJAR -X GET "http://127.0.0.1:8000/scim/v2/Users" -vvv
~~~

#### User functionality
-   Lookup of users :heavy_check_mark:
-   User Authentication :heavy_check_mark:
-   Search and view users in management console :heavy_check_mark:
    - currently only exact match search by userName
-   Add new users :heavy_check_mark:
-   Delete users :heavy_check_mark:
-   Rename User :heavy_check_mark:
    - Email must also be renamed (unique), or in keycloak realm settings set Login with email "Off" and Duplicate Emails "On"
-   Modify User Attributes :heavy_check_mark:
-   Automated/Manual Sync of SCIM users and local Keycloak users - :x:

####  Groups functionality
-   Current behavior: When a federated SCIM user logs in, this user's groups are added into keycloak.

####  Setup (bare metal local install):

- Deploy keycloak plugin
~~~
KEYCLOAK_PATH=/path/to/keycloak/keycloak-17.0.0 sh -x ./redeploy-plugin.sh
~~~

- Download and run [keycloak](https://github.com/keycloak/keycloak#getting-started)

- Login to Keycloak Admin Console (http://keycloak-server:8080)

- Add new `demo` realm

- Under User Federation -> Add the `scim` provider

- In the provider settings, provide

  * a SCIM Server URL (`scimserver.example.com:8000`).
  * Django username and password

- Click **Save**. You should see a notice that the provider has been created.

#### Implementation
* This plugin performs Apache HTTP communication equivalent to the following `curl` commands:

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
$ curl -b cookies.txt -X POST -d @filter_testuser1.json "http://127.0.0.1:8000/scim/v2/Users/.search"
~~~

    * Where `filter_testuser1.json` is
    ~~~
    {
        "schemas": ["urn:ietf:params:scim:api:messages:2.0:SearchRequest"],
        "filter": "userName eq \"testuser1\""
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
$ curl -b cookies.txt -vvv -X DELETE http://127.0.0.1:8000/scim/v2/Users/$id
~~~

#### Troubleshooting

* Check expected output with curl commands above, use `tcpdump` and compare with http filter.

* Start keycloak with option `--log-level=INFO,org.apache.http.wire:debug` to enable http wire tracing
