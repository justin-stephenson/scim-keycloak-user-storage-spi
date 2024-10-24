/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.timer.TimerProvider;
import org.keycloak.timer.ScheduledTask;
import keycloak.scim_user_spi.SCIMUserStorageProvider;


/**
 * @author <a href="mailto:jstephen@redhat.com">Justin Stephenson</a>
 * @version $Revision: 1 $
 */

public class checkDomainCreated implements ScheduledTask {
    private static final Logger logger = Logger.getLogger(checkDomainCreated.class);

    private final Scim scim;
    private final ComponentModel config;
    private final RealmModel realm;

    public checkDomainCreated(Scim scim, ComponentModel config, RealmModel realm) {
        this.scim = scim;
        this.config = config;
        this.realm = realm;
    }

    public void run(KeycloakSession session) {
        Boolean created = scim.domainsCreated();
        logger.infov("Intgdomain check created result is {0}", created);
        if (created) {
            String cenabled = null;
            logger.infov("Setting enabled to true");
            config.getConfig().putSingle("enabled", Boolean.toString(true));
            cenabled = config.getConfig().getFirst("enabled");
            realm.updateComponent(config);
            logger.infov("Enabled value is now {0}", cenabled);
        } else {
            logger.errorv("Unexpected response when checking domain creation");
        }
    }
}

