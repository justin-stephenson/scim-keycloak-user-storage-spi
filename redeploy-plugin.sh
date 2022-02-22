#!/bin/bash
kcpath=${KEYCLOAK_PATH:-""}
[ -z "$kcpath" ] && echo "Error: unset KEYCLOAK_PATH variable" && exit 1
pluginpath="$kcpath/providers/"
mvn clean package && cp ./target/scim-user-spi-0.0.1-SNAPSHOT.jar $pluginpath
#$kcpath/bin/kc.sh build
