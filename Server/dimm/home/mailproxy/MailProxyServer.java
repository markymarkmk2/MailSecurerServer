package dimm.home.mailproxy;

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
import dimm.home.mailproxy.Utilities.SwingWorker;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.StringTokenizer;






class ProxyEntry
{
    public static final int POP3 = 1;
    public static final int SMTP = 2;
    public static final int IMAP = 3;
    
    private String host;
    private int localPort;
    private int remotePort;
    private int protokoll;
    
    ProxyEntry( String _host, int l, int r, int p )
    {
        host = _host;
        localPort = l;
        remotePort = r;
        protokoll = p;
    }
    
    static public ProxyEntry create_pop3_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, POP3 );
    }
    static public ProxyEntry create_smtp_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, SMTP );
    }

    public

    String getHost()
    {
        return host;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public int getRemotePort()
    {
        return remotePort;
    }

    public int getProtokoll()
    {
        return protokoll;
    }
/*    
    static public ProxyEntry create_imap_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, IMAP );
    }
  */          
}
public class MailProxyServer extends WorkerParent
{
    public static final String NAME = "MailProxyServer";

    //private static MoreLogger logger; 				// class from log4j by Jocca Jocaf
    private static boolean m_Stop;					// stop the thread
    		// POP3Connection
    
    ArrayList<ProxyEntry> proxy_list;

    /**
     * Constructor
     * 
     * @param host Host name or IP address.
     * @param port Port number to listen.
     */
    public MailProxyServer()
    {        
        super(NAME);
        
        m_Stop = false;
        
        

    }
    
    void read_proxy_list() throws Exception
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        
        proxy_list = new ArrayList<ProxyEntry>();
        
        File f = new File( Main.PREFS_PATH + "proxy_list.txt" );
        
        if (!f.exists())
        {
            throw new Exception("No proxy list detected, missing file " + f.getAbsolutePath() );
        }
        FileReader fr = new FileReader( f );
        
        BufferedReader bis = new BufferedReader( fr );
        
        while( true )
        {
            
            String line = bis.readLine();
            if (line == null)
                break;
            
            line = line.trim().toLowerCase();
            
            if (line.length() == 0 || line.charAt(0) == '#')
                continue;
            
            StringTokenizer str = new StringTokenizer(line, " \t:,;");
            
            String protokoll = str.nextToken();
            if (!protokoll.equals("pop3") && !protokoll.equals("smtp") && !protokoll.equals("imap"))
                throw new Exception( "Invalid protokoll "  + protokoll );
            
            int local_port = Integer.parseInt(str.nextToken());
            
            String host = str.nextToken();
            
            int remote_port = Integer.parseInt(str.nextToken());

            if (protokoll.equals("pop3"))
            {
                proxy_list.add( ProxyEntry.create_pop3_entry(host, local_port, remote_port));
            }
            else if (protokoll.equals("smtp"))
            {
                proxy_list.add( ProxyEntry.create_smtp_entry(host, local_port, remote_port));
            }
            else
                throw new Exception( "Unsupported protokoll " + protokoll);            
        }
        bis.close();
        
    }

    public void go_pop(String host, int LocalPort, int RemotePort)
    {
        ServerSocket ss = null;

        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("JMailProxy is running for the host 'pop3://" + host +
                    ":" + RemotePort + "' on local port" + LocalPort);
           

            while (!m_Stop)
            {
                try
                {
                    POP3Connection m_POP3Connection = new POP3Connection(host, RemotePort);
                    Socket theSocket = ss.accept();
                    Main.info_msg("Connection accepted for the host '" + host + "'...");
                    
                    
                    m_POP3Connection.handleConnection(theSocket);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            Main.info_msg("stopping the host '" + host + "' on port " + LocalPort);
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Verify if this port is being used by other" + " programs.";
            Main.err_log(errmsg);
            Common.showError(errmsg);
        } 
        catch (java.io.IOException ex)
        {
            Main.err_log(ex.getMessage());
        }

        // close the server connection
        if (ss != null)
        {
            try
            {
                ss.close();
                ss = null;
            } catch (Exception e)
            {
                Main.err_log(e.getMessage());
            }
        }

    }  // go	

    public void go_smtp(String host, int LocalPort, int RemotePort)
    {
        ServerSocket ss = null;

        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("JMailProxy is running for the host 'smtp://" + host +
                    ":" + RemotePort + "' on local port" + LocalPort);

            SMTPConnection m_SMTPConnection = new SMTPConnection(host, RemotePort);

            while (!m_Stop)
            {
                try
                {
                    Socket theSocket = ss.accept();
                    Main.info_msg("Connection accepted for the host '" + host + "'...");
                    m_SMTPConnection.handleConnection(theSocket);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            Main.info_msg("stopping the host '" + host + "' on port " + LocalPort);
            
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Verify if this port is being used by other" + " programs.";
            Main.err_log(errmsg);
            Common.showError(errmsg);
        } 
        catch (java.io.IOException ex)
        {
            Main.err_log(ex.getMessage());
        }

        // close the server connection
        if (ss != null)
        {
            try
            {
                ss.close();
                ss = null;
            } catch (Exception e)
            {
                Main.err_log(e.getMessage());
            }
        }

    }  // go

    public static void StopServer()
    {
        m_Stop = true;
        POP3Connection.StopServer();
        SMTPConnection.StopServer();
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    
    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting " + proxy_list.size() + " proxy tasks" );
        
        for (int i = 0; i < proxy_list.size(); i++)
        {
            final ProxyEntry pe = proxy_list.get(i);
            
            SwingWorker worker = new SwingWorker()
            {
                public Object construct()
                {
                   if (pe.getProtokoll() == ProxyEntry.POP3)
                       go_pop( pe.getHost(), pe.getLocalPort(), pe.getRemotePort() );
                   else if (pe.getProtokoll() == ProxyEntry.SMTP)
                       go_smtp( pe.getHost(), pe.getLocalPort(), pe.getRemotePort() );


                    return null;
                }
            };

            worker.start();
        }
         
       return true;
    }

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        try
        {
            read_proxy_list();
        }
        catch (Exception exc)
        {
            Main.err_log("Cannot read proxy list: " + exc.getMessage());
            return false;
        }
        return true;
    }
} // POP3Server

