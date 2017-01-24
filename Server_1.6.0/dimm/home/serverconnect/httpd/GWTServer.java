/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.serverconnect.httpd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.security.KeyStore;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

/*
 * import org.mortbay.jetty.Connector;
 * import org.mortbay.jetty.Server;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;
 *
 */

public class GWTServer
{
    private static final Logger LOG = Logger.getLogger(GWTServer.class);

    String[] EXCLUDE_CIPHERS = {"SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_RSA_WITH_DES_CBC_SHA",
        "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
        // https://www.eclipse.org/jetty/documentation/current/configuring-ssl.html
        // Exclude all old, insecure or anonymous cipher suites:
        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
        ".*NULL.*", ".*RC4.*", ".*MD5.*", ".*DES.*", ".*DSS.*"
    };
    private String INTERNAL_KEYSTORE = "/home/shared/Utilities/ms.keystore";

    private class SimpleSslContextFactory extends SslContextFactory {

        String keyPwd;

        public SimpleSslContextFactory( String keyPwd ) {
            this.keyPwd = keyPwd;
        }

        @Override
        protected KeyStore loadKeyStore() throws Exception {
            // Gibt es eine Keystore Datei ?
            KeyStore ks = super.loadKeyStore(); //To change body of generated methods, choose Tools | Templates.
            if (ks != null) {
                return ks;
            }

            // Ansontsen aus Jar laden
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream keystoreStream = SimpleSslContextFactory.class.getClassLoader().getResourceAsStream(INTERNAL_KEYSTORE); // note, not getSYSTEMResourceAsStream
            ks.load(keystoreStream, keyPwd.toCharArray());

            return ks;
        }
    }

    private Server jetty_server;


    public GWTServer() {
    }

    void initSsl( Server server, int port, String keystore, String keypwd ) throws IOException {
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(port);
        http_config.setOutputBufferSize(32768);
        http_config.setRequestHeaderSize(8192);
        http_config.setResponseHeaderSize(8192);
        http_config.setSendServerVersion(true);
        http_config.setSendDateHeader(false);

        SslContextFactory sslContextFactory = new SslContextFactory(keypwd);
        if (new File(keystore).exists()) {
            LOG.debug("Loading keystore from file " + keystore);
            sslContextFactory.setKeyStorePath(keystore);
            sslContextFactory.setTrustStorePath(keystore);
        }
        else {
            LOG.debug("Loading builtin keystore");
            Resource keystoreRsrc = Resource.newResource(SimpleSslContextFactory.class.getClassLoader().getResource(INTERNAL_KEYSTORE));
            sslContextFactory.setKeyStoreResource(keystoreRsrc);
            sslContextFactory.setTrustStoreResource(keystoreRsrc);
        }
        sslContextFactory.setKeyStorePassword(keypwd);
        sslContextFactory.setKeyManagerPassword(keypwd);
        sslContextFactory.setTrustStorePassword(keypwd);
        sslContextFactory.setTrustAll(true);

        sslContextFactory.setExcludeCipherSuites(EXCLUDE_CIPHERS);
        sslContextFactory.setExcludeProtocols("SSLv3");

        // SSL HTTP Configuration
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        // SSL Connector
        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https_config));
        sslConnector.setPort(port);

        server.addConnector(sslConnector);
    }

    void initStandard( Server server, int port ) {
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(port);
        http_config.setOutputBufferSize(32768);
        http_config.setRequestHeaderSize(32 * 1024);
        http_config.setResponseHeaderSize(32 * 1024);
        http_config.setSendServerVersion(true);
        http_config.setSendDateHeader(false);

        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
        http.setPort(port);
        http.setIdleTimeout(30000);
        server.addConnector(http);
    }


    public void join_server() {
        try {
            if (jetty_server != null) {
                jetty_server.join();
            }
        }
        catch (Exception exception) {
            LOG.error("Cannot Join HTTP Servers ", exception);
        }
    }


    public void start( int port, String war_path, String keystoreFile, String keystorePassword, boolean ssl ) throws Throwable    {

        // Create an embedded Jetty server on port 8080
       // Setup Threadpool
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(100);
        threadPool.setName("Web" + ((ssl) ? "Ssl" : ""));

        if (ssl) {
            jetty_server = new Server(threadPool);
            initSsl(jetty_server, port, keystoreFile, keystorePassword);
        }
        else {
            jetty_server = new Server(threadPool);
            initStandard(jetty_server, port);
        }



        // Create a handler for processing our GWT app
        WebAppContext handler = new WebAppContext();
        handler.setContextPath("/");
        
        handler.setWar(war_path);
        File t = new File ("wartemp");
        if (!t.exists())
            t.mkdir();
        handler.setTempDirectory( t );

        // Add it to the server
        jetty_server.setHandler(handler);



        try {
            jetty_server.start();
        }
        catch (BindException exception) {
            LOG.error("Adresse bereits vergeben " + jetty_server.toString(), exception);

        }
        catch (Exception exception) {
            LOG.error("Cannot Start HTTP Server " + jetty_server.toString(), exception);

        }
    }

    public void stop()
    {
        try {
            if (jetty_server != null) {
                jetty_server.setStopTimeout(500);
                jetty_server.stop();
                jetty_server.join();
            }
        }
        catch (Exception exception) {
            LOG.error("Cannot stop HTTP Servers ", exception);

        }
    }
}
