package dimm.home.workers;

/**
 * MailProxyServer - POP3 Proxy Server for Spam Assassin.
 * 
 * <p>It listens for POP3 connections.
 *  
 *  
 * @version 1.00, 05/04/21
 * @author Jocca Jocaf
 *
 */
import home.shared.hibernate.Proxy;
import dimm.home.importmail.SMTPConnection;
import dimm.home.importmail.POP3Connection;
import dimm.home.importmail.ProxyConnection;
import dimm.home.importmail.ProxyEntry;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import home.shared.license.LicenseTicket;
import home.shared.mail.EncodedMailOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;






public class MailProxyServer extends ListWorkerParent
{
    public static final String NAME = "MailProxyServer";
    private static final int MAX_THREADS = 50;

    private static final int MAX_QUEUE = 150;


    final ArrayList<ProxyConnection> connection_list;
    ExecutorService connect_thread_pool;


    class NamedThreadFactory implements  ThreadFactory
{

    String name;


    public NamedThreadFactory( String name )
    {
        this.name = name;
    }


    @Override
    public Thread newThread( Runnable r )
    {
        String thr_name = name;

        Thread thr = new Thread(r, thr_name);
        return thr;
    }

}

    /**
     * Constructor
     * 
     * @param host Host name or IP address.
     * @param port Port number to listen.
     */
    public MailProxyServer()
    {        
        super(NAME);
        
  //      m_Stop = false;
        connection_list = new ArrayList<ProxyConnection>();

        connect_thread_pool = Main.get_control().getThreadWatcher().create_blocking_thread_pool("MailProxyServer", MAX_THREADS, MAX_QUEUE);
        //Executors.newFixedThreadPool(MAX_THREADS, new NamedThreadFactory("MailProxyServer"));
//        connect_thread_pool = Executors.newFixedThreadPool(MAX_THREADS, new NamedThreadFactory("MailProxyServer"));
    }

   
    ServerSocket getServerSocket(int serverPort, String server_ip) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException, URISyntaxException
    {
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

        SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) sslContext.getServerSocketFactory();

        SSLServerSocket ssl_sock = (SSLServerSocket) sslserversocketfactory.createServerSocket(serverPort, 5, InetAddress.getByName(server_ip) );
        return ssl_sock;
    }


    

    void go_pop( ProxyEntry pe )
    {
        
        ServerSocket pop3_ss = null;
        String local_host = pe.get_proxy().getLocalServer();
        String host = pe.get_proxy().getRemoteServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();
        try
        {
            String protocol = "pop3";
            if (pe.isSSL())
            {
                pop3_ss = getServerSocket( LocalPort, local_host );
                protocol = "pop3s";
            }
            else
            {
                pop3_ss = new ServerSocket(LocalPort, 5, InetAddress.getByName(local_host));
            }
            // 1 second timeout
            pop3_ss.setSoTimeout(1000);

            pe.set_server_sock( pop3_ss );

             LogManager.msg_proxy( LogManager.LVL_INFO, "MailProxy is running for the host '" + protocol + "://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
           

            while (!isShutdown() && !pe.is_finished())
            {
                try
                {
                    pop3_ss.setSoTimeout(1000);
                    Socket theSocket = pop3_ss.accept();
                    LogManager.msg_proxy( LogManager.LVL_DEBUG,  "Connection accepted for the pop3 host '" + host + "' on local port " + pe.get_proxy().getLocalPort());

                    if (pe.is_finished())
                    {
                        pop3_ss.close();
                        pop3_ss = null;
                        break;
                    }

                    POP3Connection m_POP3Connection = new POP3Connection( pe, theSocket );
                    
                    if (isShutdown())
                    {
                        m_POP3Connection.closeConnections();
                        break;
                    }
                    
                    synchronized(connection_list)
                    {
                        connection_list.add(m_POP3Connection);
                    }
                    
                    m_POP3Connection.handleConnection(connect_thread_pool);

                }
                catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            LogManager.msg_proxy( LogManager.LVL_INFO, "stopping the pop3 proxy for host '" + host + "' on local port " + LocalPort);
        }
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Check if this port is being used by other programs.";
            LogManager.msg_proxy( LogManager.LVL_ERR, errmsg);
            this.setStatusTxt("Not listening, port in use");
            this.setGoodState(false);
            
            //Common.showError(errmsg);
        } 
        catch (Exception ex)
        {
            if (!pe.is_finished())
            {
                String errmsg = "The System could not open the Socket on the local port " + LocalPort +". " + ex.getMessage();
                LogManager.msg_proxy( LogManager.LVL_ERR, errmsg, ex);
                this.setStatusTxt("Not listening, cannot open port");
                this.setGoodState(false);

               
            }
        }

        // close the server connection
        if (pop3_ss != null)
        {
            try
            {
                pop3_ss.close();
                pop3_ss = null;
            } catch (Exception e)
            {
                LogManager.msg_proxy( LogManager.LVL_ERR, e.getMessage());
            }
        }

    }  // go	

    
    void go_smtp(ProxyEntry pe)
    {
        ServerSocket smtp_ss = null;
        
        String host = pe.get_proxy().getRemoteServer();
        String local_host = pe.get_proxy().getLocalServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();

        try
        {
            String protocol = "smtp";
            if (pe.isSSL())
            {
                smtp_ss = getServerSocket( LocalPort, local_host );
                protocol = "smtps";
            }
            else
            {
                smtp_ss = new ServerSocket(LocalPort, 5, InetAddress.getByName(local_host));
            }
            // 1 second timeout
            smtp_ss.setSoTimeout(1000);
            LogManager.msg_proxy( LogManager.LVL_INFO, "MailProxy is running for the host '" + protocol + "://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
            pe.set_server_sock( smtp_ss );

            

            while (!isShutdown() && !pe.is_finished())
            {
                try
                {
                    Socket theSocket = smtp_ss.accept();
                    LogManager.msg_proxy( LogManager.LVL_DEBUG,  "Connection accepted for the smtp host '" + host + "' on local port " + pe.get_proxy().getLocalPort());

                    if (pe.is_finished())
                    {                       
                        break;
                    }

                    SMTPConnection m_SMTPConnection = new SMTPConnection(pe, theSocket);
                    
                    if (isShutdown())
                    {
                        m_SMTPConnection.closeConnections();
                        break;
                    }
                        
                    synchronized(connection_list)
                    {
                        connection_list.add(m_SMTPConnection);
                    }
                    
                    
                    m_SMTPConnection.handleConnection(connect_thread_pool);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            LogManager.msg_proxy( LogManager.LVL_INFO, "stopping the smtp proxy for host '" + host + "' on local port " + LocalPort);
            
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Check if this port is being used by other programs.";
            LogManager.msg_proxy( LogManager.LVL_ERR, errmsg);
            this.setStatusTxt("Not listening, port in use");
            this.setGoodState(false);
            
            //Common.showError(errmsg);
        } 
        catch (Exception ex)
        {
            if (!pe.is_finished())
            {
                String errmsg = "The System could not open the Socket on the local port " + LocalPort +". " + ex.getMessage();
                LogManager.msg_proxy( LogManager.LVL_ERR, errmsg);
                this.setStatusTxt("Not listening, cannot open port");
                this.setGoodState(false);

                LogManager.msg_proxy( LogManager.LVL_ERR, ex.getMessage());
            }
        }

        // close the server connection
        if (smtp_ss != null)
        {
            try
            {
                smtp_ss.close();
                smtp_ss = null;
            } catch (Exception e)
            {
                LogManager.msg_proxy( LogManager.LVL_ERR, e.getMessage());
            }
        }

    }  // go

    @Override
    public void setShutdown( boolean shutdown )
    {
        super.setShutdown(shutdown);
        if (!shutdown)
            return;

        connect_thread_pool.shutdown();


        for (int i = 0; i < child_list.size(); i++)
        {
            final ProxyEntry pe = (ProxyEntry)child_list.get(i);
            pe.finish();
        }

        try
        {
            connect_thread_pool.awaitTermination(5, TimeUnit.SECONDS);

            connect_thread_pool.shutdownNow();
            connect_thread_pool.awaitTermination(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex)
        {
            connect_thread_pool.shutdownNow();
            try
            {
                connect_thread_pool.awaitTermination(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException interruptedException)
            {
            }
        }
        
        

        if (idle_worker != null)
        {
            while (!idle_worker.finished())
            {
                LogicControl.sleep(1000);
            }
        }
    }


/*
    public void StopServer()
    {
        setShutdown(true);
        
    }
*/
  
    boolean is_pop3( Proxy pe )
    {
        if (pe.getType().toUpperCase().compareTo("POP3") == 0)
            return true;

        return false;
    }

    boolean is_smtp( Proxy pe )
    {
        if (pe.getType().toUpperCase().compareTo("SMTP") == 0)
            return true;

        return false;
    }

    
    @Override
    public boolean start_run_loop()
    {
        LogManager.msg_proxy( LogManager.LVL_DEBUG,  NAME + " is starting " + child_list.size() + " tasks" );
        
        if (!Main.get_control().is_licensed(LicenseTicket.PRODUCT_BASE))
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }
               
        for (int i = 0; i < child_list.size(); i++)
        {
            final ProxyEntry pe = (ProxyEntry)child_list.get(i);
            if (pe.is_started())
                continue;
            
            BackgroundWorker worker = new BackgroundWorker(NAME)
            {
                @Override
                public Object construct()
                {
                   if (is_pop3(pe.get_proxy()))
                       go_pop( pe );
                   else if (is_smtp(pe.get_proxy()))
                       go_smtp( pe );

                   pe.set_started(false);
                   return null;
                }
            };

            worker.start();
            pe.set_started(true);
        }

        if (!is_started)
        {
            idle_worker = new BackgroundWorker(NAME + ".Idle")
            {
                @Override
                public Object construct()
                {
                    do_idle();

                    return null;
                }
            };

            idle_worker.start();
            is_started = true;
        }
        
       this.setStatusTxt("Running");
       this.setGoodState(true);        
       return true;
    }
    
    @Override
    void do_idle()
    {                
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);
            
            // CLEAN UP LIST OF FINISHED CONNECTIONS
            synchronized(connection_list)
            {                                
                for (int i = 0; i < connection_list.size(); i++)
                {
                    ProxyConnection m = connection_list.get(i);
                    if (m.is_timeout())
                    {
                        LogManager.msg_proxy( LogManager.LVL_WARN, "Removing dead connection to " + m.get_proxy().get_proxy().getRemoteServer());
                        m.closeConnections();
                    }

                    if (!m.is_connected())
                    {                        
                        connection_list.remove(m);
                        i--;
                    }
                }
                if (this.isGoodState())
                {
                    if (connection_list.size() > 0)
                        this.setStatusTxt("Proxy is connected to " + connection_list.size() + " client(s)" );
                    else
                        this.setStatusTxt("");
                }
            }
        }
        finished = true;
    }
    
    public static BufferedOutputStream get_rfc_stream( File rfc_dump, boolean encoded)
    {

        BufferedOutputStream bos = null;
        try
        {
            if (rfc_dump.exists())
            {
                LogManager.msg_proxy( LogManager.LVL_WARN, "Removing existing rfc_dump file " + rfc_dump.getName());
                rfc_dump.delete();
            }

            OutputStream os = new FileOutputStream(rfc_dump);
            if (encoded)
                os = new EncodedMailOutputStream(os);
            bos = new BufferedOutputStream(os);

            rfc_dump.getFreeSpace();
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            LogManager.msg_proxy( LogManager.LVL_ERR, "Cannot open rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

            try
            {
                if (bos != null)
                {
                    bos.close();
                }
                if (rfc_dump != null && rfc_dump.exists())
                {
                    rfc_dump.delete();
                }
            }
            catch (Exception exce)
            {
            }
            bos = null;

        }
        return bos;
    }


} // POP3Server

