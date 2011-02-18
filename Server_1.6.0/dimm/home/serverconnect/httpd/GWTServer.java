/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.serverconnect.httpd;

import java.io.File;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.webapp.WebAppContext;

public class GWTServer
{
    

    Server server;

    public void start( int port, String war_path, String keystoreFile, String keystorePassword ) throws Throwable
    {

        // Create an embedded Jetty server on port 8080
        server = new Server();
        SslSocketConnector connector = new SslSocketConnector();
        connector.setPort(port);
        connector.setKeyPassword(keystorePassword);
        if (!keystoreFile.equals(""))
        {
            connector.setKeystore(keystoreFile);
        }
        
        server.setConnectors(new Connector[]
        {
            connector
        });


        // Create a handler for processing our GWT app
        WebAppContext handler = new WebAppContext();
        handler.setContextPath("/");
        
        handler.setWar(war_path);
        File t = new File ("wartemp");
        if (!t.exists())
            t.mkdir();
        handler.setTempDirectory( t );

        // Add it to the server
        server.setHandler(handler);



        // Other misc. options
        //server.setThreadPool(new QueuedThreadPool(20));

        // And start it up
        server.start();
        //server.join();
    }

    public void stop()
      {
        try
          {
              server.stop();
              server.join();
          }
          catch (Exception exception)
          {
          }
    }
}
