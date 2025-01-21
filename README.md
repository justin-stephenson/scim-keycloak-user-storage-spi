#### IMPORTANT

This plugin is moving to the [keycloak](https://github.com/keycloak/keycloak/tree/main/federation/ipatuura), it is now built as part of keycloak
as an experimental feature. Please file issues and PRs against the keycloak repo, this repo will no longer be kept up to date.

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

#### Plugin communication

This SCIM client plugin is designed to communicate with the [ipa-tuura](https://github.com/freeipa/ipa-tuura) bridge service, which provide endpoints for SCIMv2, Administrative, authentication, and credentials validation requests.

The plugin enables use of the [Keycloak TrustStore](https://www.keycloak.org/server/keycloak-truststore) to act as a client trust store, acting as a client and establishing TLS connections with external services, This enables the
plugin to communicate over HTTPS to the [ipa-tuura](https://github.com/freeipa/ipa-tuura) bridge.

When a user lookup is performed in keycloak, Keycloak loads [user storage plugin interfaces](https://www.keycloak.org/docs/latest/server_development/#_provider_capability_interfaces) for any federated user storage plugins configured on the server.
From this interface, the [getUserByUsername](https://www.keycloak.org/docs-api/25.0.0/javadocs/org/keycloak/storage/user/UserLookupProvider.html#getUserByUsername(org.keycloak.models.RealmModel,java.lang.String)) method is executed.
This SCIM plugin initializes `Apache HTTP components` HTTPClient object to perform initial login authentication to the SCIM server URL provided in the user storage configuration.

  1) HTTP GET request sent to the SCIM server `/admin/` page, retrieving the Location header which gives the URL login page to send the next request
  2) HTTP GET request to the login page, retrieving the initial csrftoken cookie
  3) HTTP POST to URL login page providing csrftoken in the 'X-CSRFToken' header, along with username + password data to authenticate this user
  4) Store the updated csrf cookie into the `HTTPClient` cookie store, this is needed to send and receive authenticated state in subsequent calls to server endpoints

Then once logged in, the plugin `HTTPClient` queries for SCIM resources to the scim server following [RFC7644](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.3). In the user lookup case, this is done by sending a HTTP POST request to the `scim/v2/Users/.search` endpoint  with the `application/scim+json` content type data `userName eq \"testuser\` as the filter string. If the user exists, the server responds with HTTP status code 200 and includes the result in the body of the response.

For new user creations, the plugin sends HTTP POST to the create new resource SCIM endpoint with HTTP body, a JSON representation of the user.

    POST /Users  HTTP/1.1
    Host: example.com
    Accept: application/scim+json
    Content-Type: application/scim+json
    Content-Length: ...

    {
    "schemas" : ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName" : "testuser"
    "externalId" : "testuser",
    "name" : {
        "familyName" : "user",
        "formatted" : "testuser",
        "givenName" : "test"
    },
    "emails":[{
        "primary" : true,
        "type" : "work",
        "value" : "testuser@ipa.test"
    }],
    }

Password validation sends the HTTP POST request to ipa-tuura endpoint `/creds/simple_pwd` ipa-tuura endpoint, where actual validation is performed on the server side. The plugin retrieves a successful, or failure HTTP response code which is returned to the keycloak login operation.

In Kerberos environments, GSSAPI authentication enables users with a valid kerberos ticket to login transparently through the keycloak client browser flow. The plugin implements the [CredentialAuthentication interface](
https://www.keycloak.org/docs-api/25.0.1/javadocs/org/keycloak/credential/CredentialAuthentication.html#authenticate(org.keycloak.models.RealmModel,org.keycloak.credential.CredentialInput)) to support this.
With proper web browser configuration, Keycloak retrieves the Kerberos token from the `Authorization: Negotiate` HTTP header, the plugin then sends the kerberos principal name and extracted token to the server `/bridge/login_kerberos/` endpoint.
This endpoint validates the kerberos credentials server-side and if successful, sets the `REMOTE_USER` in the header response. This endpoint uses [cookie based sessions](https://github.com/gssapi/mod_auth_gssapi?tab=readme-ov-file#gssapiusesessions) to avoid re-authentication attempts for every request. 

The plugin configuration shows optional [fields](https://github.com/justin-stephenson/scim-keycloak-user-storage-spi/blob/main/src/main/java/keycloak/scim_user_spi/SCIMUserStorageProviderFactory.java#L57) which can be provided for the `ipa-tuura` Administrative endpoint, This allows you to add and remove integration domains and perform client enrollment of the bridge service in the integration domain. It supports integration with FreeIPA, LDAP and Active Directory.