package keycloak.scim_user_spi.cloned;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.keycloak.common.enums.HostnameVerificationPolicy;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import org.keycloak.connections.httpclient.ProxyMappings;
import org.keycloak.connections.httpclient.ProxyMappingsAwareRoutePlanner;

/**
 * Abstraction for creating HttpClients. Allows SSL configuration.
 * 
 * Cloned from org.keycloak.connections.httpclient.HttpClientBuilder
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
class ClonedHttpClientBuilder {


    /**
     * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
     * @version $Revision: 1 $
     */
    private static class PassthroughTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

    protected KeyStore truststore;
    protected KeyStore clientKeyStore;
    protected String clientPrivateKeyPassword;
    protected boolean disableTrustManager;
    protected HostnameVerificationPolicy policy = HostnameVerificationPolicy.WILDCARD;
    protected SSLContext sslContext;
    protected int connectionPoolSize = 128;
    protected int maxPooledPerRoute = 64;
    protected long connectionTTL = -1;
    protected boolean reuseConnections = true;
    protected TimeUnit connectionTTLUnit = TimeUnit.MILLISECONDS;
    protected long maxConnectionIdleTime = 900000;
    protected TimeUnit maxConnectionIdleTimeUnit = TimeUnit.MILLISECONDS;
    protected HostnameVerifier verifier = null;
    protected long socketTimeout = -1;
    protected TimeUnit socketTimeoutUnits = TimeUnit.MILLISECONDS;
    protected long establishConnectionTimeout = -1;
    protected TimeUnit establishConnectionTimeoutUnits = TimeUnit.MILLISECONDS;
    protected boolean disableCookies = false;
    protected ProxyMappings proxyMappings;
    protected boolean expectContinueEnabled = false;

    /**
     * Socket inactivity timeout
     *
     * @param timeout
     * @param unit
     * @return
     */
    public ClonedHttpClientBuilder socketTimeout(long timeout, TimeUnit unit)
    {
        this.socketTimeout = timeout;
        this.socketTimeoutUnits = unit;
        return this;
    }

    /**
     * When trying to make an initial socket connection, what is the timeout?
     *
     * @param timeout
     * @param unit
     * @return
     */
    public ClonedHttpClientBuilder establishConnectionTimeout(long timeout, TimeUnit unit)
    {
        this.establishConnectionTimeout = timeout;
        this.establishConnectionTimeoutUnits = unit;
        return this;
    }

    public ClonedHttpClientBuilder connectionTTL(long ttl, TimeUnit unit) {
        this.connectionTTL = ttl;
        this.connectionTTLUnit = unit;
        return this;
    }

    public ClonedHttpClientBuilder reuseConnections(boolean reuseConnections) {
        this.reuseConnections = reuseConnections;
        return this;
    }

    public ClonedHttpClientBuilder maxConnectionIdleTime(long maxConnectionIdleTime, TimeUnit unit) {
        this.maxConnectionIdleTime = maxConnectionIdleTime;
        this.maxConnectionIdleTimeUnit = unit;
        return this;
    }

    public ClonedHttpClientBuilder maxPooledPerRoute(int maxPooledPerRoute) {
        this.maxPooledPerRoute = maxPooledPerRoute;
        return this;
    }

    public ClonedHttpClientBuilder connectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }

    /**
     * Disable trust management and hostname verification. <i>NOTE</i> this is a security
     * hole, so only set this option if you cannot or do not want to verify the identity of the
     * host you are communicating with.
     */
    public ClonedHttpClientBuilder disableTrustManager() {
        this.disableTrustManager = true;
        return this;
    }

    /**
     * Disable cookie management.
     */
    public ClonedHttpClientBuilder disableCookies(boolean disable) {
        this.disableCookies = disable;
        return this;
    }

    /**
     * SSL policy used to verify hostnames
     *
     * @param policy
     * @return
     */
    public ClonedHttpClientBuilder hostnameVerification(HostnameVerificationPolicy policy) {
        this.policy = policy;
        return this;
    }


    public ClonedHttpClientBuilder sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public ClonedHttpClientBuilder trustStore(KeyStore truststore) {
        this.truststore = truststore;
        return this;
    }

    public ClonedHttpClientBuilder keyStore(KeyStore keyStore, String password) {
        this.clientKeyStore = keyStore;
        this.clientPrivateKeyPassword = password;
        return this;
    }

    public ClonedHttpClientBuilder keyStore(KeyStore keyStore, char[] password) {
        this.clientKeyStore = keyStore;
        this.clientPrivateKeyPassword = new String(password);
        return this;
    }

    public ClonedHttpClientBuilder proxyMappings(ProxyMappings proxyMappings) {
        this.proxyMappings = proxyMappings;
        return this;
    }

    public ClonedHttpClientBuilder expectContinueEnabled(boolean expectContinueEnabled) {
        this.expectContinueEnabled = expectContinueEnabled;
        return this;
    }

    static class VerifierWrapper implements X509HostnameVerifier {
        protected HostnameVerifier verifier;

        VerifierWrapper(HostnameVerifier verifier) {
            this.verifier = verifier;
        }

        @Override
        public void verify(String host, SSLSocket ssl) throws IOException {
            if (!verifier.verify(host, ssl.getSession())) throw new SSLException("Hostname verification failure");
        }

        @Override
        public void verify(String host, X509Certificate cert) throws SSLException {
            throw new SSLException("This verification path not implemented");
        }

        @Override
        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
            throw new SSLException("This verification path not implemented");
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return verifier.verify(s, sslSession);
        }
    }

    public CloseableHttpClient build() {
        X509HostnameVerifier verifier = null;
        if (this.verifier != null) verifier = new VerifierWrapper(this.verifier);
        else {
            switch (policy) {
                case ANY:
                    verifier = new AllowAllHostnameVerifier();
                    break;
                case WILDCARD:
                    verifier = new BrowserCompatHostnameVerifier();
                    break;
                case STRICT:
                    verifier = new StrictHostnameVerifier();
                    break;
            }
        }
        try {
            SSLConnectionSocketFactory sslsf = null;
            SSLContext theContext = sslContext;
            if (disableTrustManager) {
                theContext = SSLContext.getInstance("TLS");
                theContext.init(null, new TrustManager[]{new PassthroughTrustManager()},
                        new SecureRandom());
                verifier = new AllowAllHostnameVerifier();
                sslsf = new SSLConnectionSocketFactory(theContext, verifier);
            } else if (theContext != null) {
                sslsf = new SSLConnectionSocketFactory(theContext, verifier);
            } else if (clientKeyStore != null || truststore != null) {
                theContext = createSslContext("TLS", clientKeyStore, clientPrivateKeyPassword, truststore, null);
                sslsf = new SSLConnectionSocketFactory(theContext, verifier);
            } else {
                final SSLContext tlsContext = SSLContext.getInstance("TLS");
                tlsContext.init(null, null, null);
                sslsf = new SSLConnectionSocketFactory(tlsContext, verifier);
            }

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout((int) establishConnectionTimeout)
                    .setSocketTimeout((int) socketTimeout)
                    .setExpectContinueEnabled(expectContinueEnabled).build();

            org.apache.http.impl.client.HttpClientBuilder builder = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setSSLSocketFactory(sslsf)
                    .setMaxConnTotal(connectionPoolSize)
                    .setMaxConnPerRoute(maxPooledPerRoute)
                    .setConnectionTimeToLive(connectionTTL, connectionTTLUnit);

            if (!reuseConnections) {
                builder.setConnectionReuseStrategy(new NoConnectionReuseStrategy());
            }

            if (proxyMappings != null && !proxyMappings.isEmpty()) {
                builder.setRoutePlanner(new ProxyMappingsAwareRoutePlanner(proxyMappings));
            }

            if (maxConnectionIdleTime > 0) {
                // Will start background cleaner thread
                builder.evictIdleConnections(maxConnectionIdleTime, maxConnectionIdleTimeUnit);
            }

            if (disableCookies) builder.disableCookieManagement();

            if (!reuseConnections) {
                builder.setConnectionReuseStrategy(new NoConnectionReuseStrategy());
            }

            // The following line and respective method are the only differences to the original class
            customizeHttpClientBuilder(builder);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void customizeHttpClientBuilder(org.apache.http.impl.client.HttpClientBuilder builder) {
    }

    private SSLContext createSslContext(
            final String algorithm,
            final KeyStore keystore,
            final String keyPassword,
            final KeyStore truststore,
            final SecureRandom random)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        return SSLContexts.custom()
                        .useProtocol(algorithm)
                        .setSecureRandom(random)
                        .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                        .loadTrustMaterial(truststore)
                        .build();
    }

}
