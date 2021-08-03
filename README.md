Testing done with https://github.com/andreihava-okta/sample-node-scim-server

User functionality
-   Lookup of users - Implemented
-   User Authentication - Implemented only using plaintext password properties file
-   Search and view users in management console - Implemented, currently supports only search by userName
-   Add/Remove users - Implemented
-   Modify User Attributes - Implemented, modifying username not yet supported

Groups functionality
-   Current implemented behavior: When a federated SCIM user logs in, this user's groups are added into keycloak.
