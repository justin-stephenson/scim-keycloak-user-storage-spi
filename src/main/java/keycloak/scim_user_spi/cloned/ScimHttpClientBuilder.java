package keycloak.scim_user_spi.cloned;

import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.EnvUtil;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.connections.httpclient.ProxyMappings;
import org.keycloak.truststore.TruststoreProvider;
import org.keycloak.models.KeycloakSession;
import static org.keycloak.utils.StringUtil.isBlank;

// This class is based on org.keycloak.connections.httpclient.DefaultHttpClientFactory
// to respect Keycloak settings for truststore, proxy, etc.

public class ScimHttpClientBuilder {

    private static final Logger logger = Logger.getLogger(ScimHttpClientBuilder.class);

    private static final String configScope = "keycloak.connectionsHttpClient.default.";
    
    private static final String HTTPS_PROXY = "https_proxy";
    private static final String HTTP_PROXY = "http_proxy";
    private static final String NO_PROXY = "no_proxy";

    private static boolean getBooleanConfigWithSysPropFallback(Config.Scope config, String key, boolean defaultValue) {
        Boolean value = config.getBoolean(key);
        if (value == null) {
            String s = System.getProperty(configScope + key);
            if (s != null) {
                return Boolean.parseBoolean(s);
            }
        }
        return value != null ? value : defaultValue;
    }
    
    private static String getEnvVarValue(String name) {
        String value = System.getenv(name.toLowerCase());
        if (isBlank(value)) {
            value = System.getenv(name.toUpperCase());
        }
        return value;
    }


    public static CloseableHttpClient createHttpClient(KeycloakSession session, CookieStore cookieStore) throws RuntimeException {
        Config.Scope config = Config.scope("connectionsHttpClient", "default");

        long socketTimeout = config.getLong("socket-timeout-millis", 5000L);
        long establishConnectionTimeout = config.getLong("establish-connection-timeout-millis", -1L);
        int maxPooledPerRoute = config.getInt("max-pooled-per-route", 64);
        int connectionPoolSize = config.getInt("connection-pool-size", 128);
        long connectionTTL = config.getLong("connection-ttl-millis", -1L);
        boolean reuseConnections = config.getBoolean("reuse-connections", true);
        long maxConnectionIdleTime = config.getLong("max-connection-idle-time-millis", 900000L);
        String clientKeystore = config.get("client-keystore");
        String clientKeystorePassword = config.get("client-keystore-password");
        String clientPrivateKeyPassword = config.get("client-key-password");
        boolean disableTrustManager = config.getBoolean("disable-trust-manager", false);
        
        boolean expectContinueEnabled = getBooleanConfigWithSysPropFallback(config, "expect-continue-enabled", false);
        boolean resuseConnections = getBooleanConfigWithSysPropFallback(config, "reuse-connections", true);
        
        // optionally configure proxy mappings
        // direct SPI config (e.g. via standalone.xml) takes precedence over env vars
        // lower case env vars take precedence over upper case env vars
        ProxyMappings proxyMappings = ProxyMappings.valueOf(config.getArray("proxy-mappings"));
        if (proxyMappings == null || proxyMappings.isEmpty()) {
            logger.debug("Trying to use proxy mapping from env vars");
            String httpProxy = getEnvVarValue(HTTPS_PROXY);
            if (isBlank(httpProxy)) {
                httpProxy = getEnvVarValue(HTTP_PROXY);
            }
            String noProxy = getEnvVarValue(NO_PROXY);
            
            logger.debugf("httpProxy: %s, noProxy: %s", httpProxy, noProxy);
            proxyMappings = ProxyMappings.withFixedProxyMapping(httpProxy, noProxy);
        }
        
        // The following declaration is different from original by overriding the customizeHttpClientBuilder method
        ClonedHttpClientBuilder builder = new ClonedHttpClientBuilder() {
            @Override
            protected void customizeHttpClientBuilder(org.apache.http.impl.client.HttpClientBuilder builder) {
                builder
				  .setDefaultCookieStore(cookieStore)
				  .setDefaultRequestConfig(RequestConfig.custom()
				    .setRedirectsEnabled(false)
				    .setCookieSpec(CookieSpecs.STANDARD)
				    .build()
				  );
            }
        };
        
        builder.socketTimeout(socketTimeout, TimeUnit.MILLISECONDS)
          .establishConnectionTimeout(establishConnectionTimeout, TimeUnit.MILLISECONDS)
          .maxPooledPerRoute(maxPooledPerRoute)
          .connectionPoolSize(connectionPoolSize)
          .reuseConnections(reuseConnections)
          .connectionTTL(connectionTTL, TimeUnit.MILLISECONDS)
          .maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS)
          .disableCookies(false)    // This intentionally does not respect disableCookies settings in Keycloak
          .proxyMappings(proxyMappings)
          .expectContinueEnabled(expectContinueEnabled)
          .reuseConnections(resuseConnections);
        
        TruststoreProvider truststoreProvider = session.getProvider(TruststoreProvider.class);
        boolean disableTruststoreProvider = truststoreProvider == null || truststoreProvider.getTruststore() == null;
        
        if (disableTruststoreProvider) {
            logger.warn("TruststoreProvider is disabled");
        } else {
            builder.hostnameVerification(ClonedHttpClientBuilder.HostnameVerificationPolicy.valueOf(truststoreProvider.getPolicy().name()));
            try {
                builder.trustStore(truststoreProvider.getTruststore());
            } catch (Exception e) {
                throw new RuntimeException("Failed to load truststore", e);
            }
        }
        
        if (disableTrustManager) {
            logger.warn("TrustManager is disabled");
            builder.disableTrustManager();
        }
        
        if (clientKeystore != null) {
            clientKeystore = EnvUtil.replace(clientKeystore);
            try {
                KeyStore clientCertKeystore = KeystoreUtil.loadKeyStore(clientKeystore, clientKeystorePassword);
                builder.keyStore(clientCertKeystore, clientPrivateKeyPassword);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load keystore", e);
            }
        }
        
        return builder.build();
    }

}