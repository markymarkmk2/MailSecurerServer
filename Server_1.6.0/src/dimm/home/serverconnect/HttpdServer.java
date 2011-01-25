/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.serverconnect;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ServerSocketFactory;
import javax.xml.ws.soap.SOAPBinding;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.soap.SOAPFactory;
import javax.xml.ws.Endpoint;



 class ServerSocketFactoryWrapper extends ServerSocketFactory
 {

     protected ServerSocketFactory ssf;

     public ServerSocketFactoryWrapper()
     {

         ssf = ServerSocketFactory.getDefault();
     }

     public ServerSocketFactoryWrapper( ServerSocketFactory sf )
     {

         ssf = sf;
     }

     @Override
     public ServerSocket createServerSocket( int port )
             throws IOException
     {
         ServerSocket s = ssf.createServerSocket(port);
         s.setPerformancePreferences(0, 2, 2);
         return s;
     }

     @Override
     public ServerSocket createServerSocket( int port, int backlog )
             throws IOException
     {
         ServerSocket s = ssf.createServerSocket(port, backlog);
         s.setPerformancePreferences(0, 2, 2);
         return s;
     }

     @Override
     public ServerSocket createServerSocket( int port, int backlog,
             InetAddress ifAddress )
             throws IOException
     {
         ServerSocket s = ssf.createServerSocket(port, backlog, ifAddress);
         s.setPerformancePreferences(0, 2, 2);
         return s;
     }


 }
/**
 *
 * @author mw
 */
class MyHandler implements HttpHandler
{
    String start_path;

    public MyHandler( String start_path )
    {
        this.start_path = start_path;
    }


    public static final String CMD_QUERY = "/query";

    @Override
    public void handle( HttpExchange t ) throws IOException
    {
        String path = t.getRequestURI().getPath();
        InputStream is = t.getRequestBody();
        StringBuffer sb = new StringBuffer();
        System.out.println("P: " + path);

        File f = new File(start_path + path );
        if (f.exists())
        {
            byte[] data = new byte[(int)f.length()];
            
            FileInputStream fis = new FileInputStream(f);
            
            fis.read(data);
            
            fis.close();
            
           
            
            t.sendResponseHeaders(200, data.length);
            OutputStream os = t.getResponseBody();
            os.write(data);
            os.close();
            return;
        }
        if (is != null)
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
        System.out.println("IN: " + sb.toString());

        String response = path;
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




public class HttpdServer
{

    HttpsServer server;
    String path;
    int port;

    public HttpdServer( int _port, String _p )
    {
        port = _port;
        path = _p;
    }

    public HttpdServer( int _port )
    {
        port = _port;
        File f = new File("httpdocs");
        if (!f.exists())
        {
            f.mkdir();
        }

        path = f.getAbsolutePath();
    }

    public HttpdServer()
    {
        this(8040);
    }
    public void stop_httpd()
    {
        if (server == null)
            return;
        server.stop(1);
        server = null;
    }

    public void start_httpd()
    {
        try
        {
            server = HttpsServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new MyHandler(path));
//            server.createContext("/");
            server.setExecutor(null); // creates a default executor


    SSLContext      sslContext = SSLContext.getInstance("TLS");
        char[] password = "mailsecurer".toCharArray();

        /*
         * Allocate and initialize a KeyStore object.
         */
        KeyStore ks = KeyToolHelper.load_keystore(/*syskeystore*/ false);

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


        sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(), null);

            server.setHttpsConfigurator(new HttpsConfigurator(sslContext)
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
            LogManager.msg_comm( LogManager.LVL_ERR,  "start_httpd failed", ex);
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
        char[] password = "mailsecurer".toCharArray();

        /*
         * Allocate and initialize a KeyStore object.
         */
        KeyStore ks = KeyToolHelper.load_keystore(/*syskeystore*/ false);

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


        sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(), null);



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
    public void start_regular(String ip, String port) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException, ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        
        System.setProperty("com.sun.net.httpserver.HttpServerProvider", "dimm.net.httpserver.MyHttpServerProvider");

        HttpServerProvider pr = HttpServerProvider.provider();

        HttpServer hs = pr.createHttpServer(new InetSocketAddress( ip, Integer.parseInt(port)), 0 );

        HttpContext hc = hs.createContext("/1234" );
        

        // create and publish an endpoint
        m_arrayBlockingQueue = new ArrayBlockingQueue(10000);
        m_executor = new ThreadPoolExecutor(5, 50, 15L, TimeUnit.SECONDS, m_arrayBlockingQueue);

        

        MWWebService calculator = new MWWebService();
        Endpoint endpoint = Endpoint.create(calculator);
 
        //Endpoint endpoint = Endpoint.publish("http://" + ip + ":" + port + "/1234", calculator);
        endpoint.setExecutor(m_executor);

        endpoint.publish(hc);

        HttpHandler hd = hc.getHandler();
        hs.start();

        
        SOAPBinding binding = (SOAPBinding)endpoint.getBinding();
        binding.setMTOMEnabled(true);
        SOAPFactory sf = binding.getSOAPFactory();
        javax.xml.ws.spi.Provider pro = javax.xml.ws.spi.Provider.provider();
        //com.sun.xml.internal.ws.spi.ProviderImpl pi = (com.sun.xml.internal.ws.spi.ProviderImpl)pr;

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

    /*
     *
     *
     *
package samples.services;

import org.apache.axiom.om.OMText;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.attachments.Attachments;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.wsdl.WSDLConstants;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.namespace.QName;
import java.io.*;

    public class MTOMSwASampleService
     {

    private static final int BUFFER = 2048;

    public OMElement uploadFileUsingMTOM(OMElement request) throws Exception
     {

    OMText binaryNode = (OMText) request.
    getFirstChildWithName(new QName("http://www.apache-synapse.org/test", "request")).
    getFirstChildWithName(new QName("http://www.apache-synapse.org/test", "image")).
    getFirstOMChild();
    DataHandler dataHandler = (DataHandler) binaryNode.getDataHandler();
    InputStream is = dataHandler.getInputStream();

    File tempFile = File.createTempFile("mtom-", ".gif");
    FileOutputStream fos = new FileOutputStream(tempFile);
    BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);

    byte data[] = new byte[BUFFER];
    int count;
    while ((count = is.read(data, 0, BUFFER)) != -1) {
    dest.write(data, 0, count);
    }

    dest.flush();
    dest.close();
    System.out.println("Wrote MTOM content to temp file : " + tempFile.getAbsolutePath());

    OMFactory factory = request.getOMFactory();
    OMNamespace ns = factory.createOMNamespace("http://www.apache-synapse.org/test", "m0");
    OMElement payload  = factory.createOMElement("uploadFileUsingMTOMResponse", ns);
    OMElement response = factory.createOMElement("response", ns);
    OMElement image    = factory.createOMElement("image", ns);

    FileDataSource fileDataSource = new FileDataSource(tempFile);
    dataHandler = new DataHandler(fileDataSource);
    OMText textData = factory.createOMText(dataHandler, true);
    image.addChild(textData);
    response.addChild(image);
    payload.addChild(response);

    MessageContext outMsgCtx = MessageContext.getCurrentMessageContext()
    .getOperationContext().getMessageContext(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
    outMsgCtx.setProperty(
    org.apache.axis2.Constants.Configuration.ENABLE_MTOM,
    org.apache.axis2.Constants.VALUE_TRUE);

    return payload;
    }

    public OMElement uploadFileUsingSwA(OMElement request) throws Exception {

    String imageContentId = request.
    getFirstChildWithName(new QName("http://www.apache-synapse.org/test", "request")).
    getFirstChildWithName(new QName("http://www.apache-synapse.org/test", "imageId")).
    getText();

    MessageContext msgCtx   = MessageContext.getCurrentMessageContext();
    Attachments attachment  = msgCtx.getAttachmentMap();
    DataHandler dataHandler = attachment.getDataHandler(imageContentId);
    File tempFile = File.createTempFile("swa-", ".gif");
    FileOutputStream fos = new FileOutputStream(tempFile);
    dataHandler.writeTo(fos);
    fos.flush();
    fos.close();
    System.out.println("Wrote SwA attachment to temp file : " + tempFile.getAbsolutePath());

    MessageContext outMsgCtx = msgCtx.getOperationContext().
    getMessageContext(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
    outMsgCtx.setProperty(
    org.apache.axis2.Constants.Configuration.ENABLE_SWA,
    org.apache.axis2.Constants.VALUE_TRUE);

    OMFactory factory = request.getOMFactory();
    OMNamespace ns = factory.createOMNamespace("http://www.apache-synapse.org/test", "m0");
    OMElement payload  = factory.createOMElement("uploadFileUsingSwAResponse", ns);
    OMElement response = factory.createOMElement("response", ns);
    OMElement imageId  = factory.createOMElement("imageId", ns);

    FileDataSource fileDataSource = new FileDataSource(tempFile);
    dataHandler = new DataHandler(fileDataSource);
    imageContentId = outMsgCtx.addAttachment(dataHandler);
    imageId.setText(imageContentId);
    response.addChild(imageId);
    payload.addChild(response);

    return payload;
    }

    public void oneWayUploadUsingMTOM(OMElement element) throws Exception {

    OMText binaryNode = (OMText) element.getFirstOMChild();
    DataHandler dataHandler = (DataHandler) binaryNode.getDataHandler();
    InputStream is = dataHandler.getInputStream();

    File tempFile = File.createTempFile("mtom-", ".gif");
    FileOutputStream fos = new FileOutputStream(tempFile);
    BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);

    byte data[] = new byte[BUFFER];
    int count;
    while ((count = is.read(data, 0, BUFFER)) != -1) {
    dest.write(data, 0, count);
    }

    dest.flush();
    dest.close();
    System.out.println("Wrote to file : " + tempFile.getAbsolutePath());
    }
    }
*
     * */
}
