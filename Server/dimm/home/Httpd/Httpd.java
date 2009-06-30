/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.Httpd;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.ws.Endpoint;

/**
 *
 * @author mw
 */
class MyHandler implements HttpHandler
{

    public static final String CMD_QUERY = "/query";

    @Override
    public void handle( HttpExchange t ) throws IOException
    {
        String path = t.getRequestURI().getPath();
        InputStream is = t.getRequestBody();
        StringBuffer sb = new StringBuffer();

        if (is != null && is.available() > 0)
        {
            BufferedInputStream bsi = new BufferedInputStream(is);
            byte[] buff = new byte[4096];

            while (true)
            {
                int rlen = bsi.read(buff);
                if (rlen == -1)
                {
                    break;
                }
                sb.append(new String(buff, 0, rlen));
            }
        }

        String response = "This is the response";
        if (path.startsWith(CMD_QUERY))
        {
            response = "Query was " + path;
        }

        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}

public class Httpd
{

    HttpsServer server;
    String path;
    int port;

    public Httpd( int _port, String _p )
    {
        port = _port;
        path = _p;
    }

    public Httpd( int _port )
    {
        port = _port;
        File f = new File("httpdocs");
        if (!f.exists())
        {
            f.mkdir();
        }

        path = f.getAbsolutePath();
    }

    public Httpd()
    {
        this(8080);
    }

    public void start_httpd()
    {
        try
        {
            server = HttpsServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(null); // creates a default executor

            char[] passphrase = "passphrase".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("testkeys"), passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            server.setHttpsConfigurator(new HttpsConfigurator(ssl)
            {

                @Override
                public void configure( HttpsParameters params )
                {

                    // get the remote address if needed
                    InetSocketAddress remote = params.getClientAddress();

                    SSLContext c = getSSLContext();

                    // get the default parameters
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    if (remote.getHostName().equals("127.0.0.1"))
                    {
                    }

                    params.setSSLParameters(sslparams);
                    // statement above could throw IAE if any params invalid.
                    // eg. if app has a UI and parameters supplied by a user.

                }
            });

            server.start();
        }
        catch (Exception ex)
        {
            Logger.getLogger(Httpd.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private static ArrayBlockingQueue m_arrayBlockingQueue = null;
    private static ThreadPoolExecutor m_executor = null;
    /**
     * @param args the command line arguments
     */
    public void start_ssl() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException
    {

// keytool -genkey -keyalg RSA -keystore jaxws.keystore

// The code I've got working to serve my HTTPS webservice is as follows - it's a bit rough and ready as I haven't tidied it up yet, but you should get the idea:

        MWWebService calculator = new MWWebService();
        Endpoint endpoint = Endpoint.create(/*"http://localhost:8050/1234",*/ calculator);
        HttpsServer s1 =  HttpsServer.create(new InetSocketAddress(8050), 0);
		SSLContext      sslContext = SSLContext.getInstance("TLS");
        /*
         * Allocate and initialize a KeyStore object.
         */
        char[] password = "123456".toCharArray();

			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream fis = new FileInputStream("jaxws.keystore");
			ks.load(fis, password);
			/*
			 * Allocate and initialize a KeyManagerFactory.
			 */
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, password);
			/*
			 * Allocate and initialize a TrustManagerFactory.
			 */
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);

			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);


        final SSLEngine m_engine = sslContext.createSSLEngine();

        s1.setHttpsConfigurator(new HttpsConfigurator(sslContext)
        {
            @Override
            public void configure(HttpsParameters params) {

                params.setCipherSuites(m_engine.getEnabledCipherSuites());
                params.setProtocols(m_engine.getEnabledProtocols());
            }
        });
		s1.start();

		HttpContext s = s1.createContext("/1234");
		endpoint.publish(s);

        
        // create and publish an endpoint
        m_arrayBlockingQueue = new ArrayBlockingQueue(10000);
        m_executor = new ThreadPoolExecutor(5, 50, 15L, TimeUnit.SECONDS, m_arrayBlockingQueue);

        endpoint.setExecutor(m_executor);

       // MWWebService calculator = new MWWebService();
        //Endpoint endpoint = Endpoint.publish("https://localhost:8050/1234", calculator);
        //endpoint.publish(s);

    }
    public void start_regular() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException
    {
        // create and publish an endpoint
        m_arrayBlockingQueue = new ArrayBlockingQueue(10000);
        m_executor = new ThreadPoolExecutor(5, 50, 15L, TimeUnit.SECONDS, m_arrayBlockingQueue);

        MWWebService calculator = new MWWebService();
        Endpoint endpoint = Endpoint.publish("http://192.168.1.145:8050/1234", calculator);
        endpoint.setExecutor(m_executor);

    }


    void test()
    {
        System.setProperty("javax.net.ssl.trustStore", "jxws.ts");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

/*

        long now = System.currentTimeMillis() / 1000 + 14*24*3600;

        MWWebServiceService service =
                new MWWebServiceService();
        dimm.home.httpd.MWWebService port = service.getMWWebServicePort();

        // TODO initialize WS operation arguments here
        int arg0 = 4;
        int arg1 = 6;
        int result = 0;
        // TODO process result here
        for (int i = 0; i < 1000; i++)
        {
            result = port.minus(arg0, arg1);
   //         result = port.multiply(result , 2);
        }

        System.out.println(Thread.currentThread().toString() + " | Result = " + result);

        String m1 = "abcdf ";
        TestString m2 = new TestString();

        m2.setS("blah");

        String ret = port.stringConcat(m1, m2);

        System.out.println(Thread.currentThread().toString() + " | Result = " + ret);
  */
    }

}
