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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.StringTokenizer;






public class MailProxyServer extends WorkerParent
{
    public static final String NAME = "MailProxyServer";
    private static final String PROXYFILE = "proxy_list.txt";

    //private static MoreLogger logger; 				// class from log4j by Jocca Jocaf
    private static boolean m_Stop;					// stop the thread
    		// POP3Connection
    
    ArrayList<ProxyEntry> proxy_list;
    
    ArrayList<MailConnection> connection_list;
    
    SwingWorker idle_worker;

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
        connection_list = new ArrayList<MailConnection>();
        
        

    }
    
    static public void write_proxy_str(String txt) throws IOException
    {
        File f = new File( Main.PREFS_PATH + PROXYFILE );
        
        FileWriter fw = new FileWriter( f );
        
        BufferedWriter bw = new BufferedWriter( fw );
        
        bw.write(txt + "\n");

        bw.close();                        
    }
    
    static public ArrayList<ProxyEntry> read_proxy_list() throws Exception
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        
        ArrayList<ProxyEntry> p_list = new ArrayList<ProxyEntry>();
        
        File f = new File( Main.PREFS_PATH + PROXYFILE );
        
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
                p_list.add( ProxyEntry.create_pop3_entry(host, local_port, remote_port));
            }
            else if (protokoll.equals("smtp"))
            {
                p_list.add( ProxyEntry.create_smtp_entry(host, local_port, remote_port));
            }
            else if (protokoll.equals("imap"))
            {
                p_list.add( ProxyEntry.create_imap_entry(host, local_port, remote_port));
            }
            else
                throw new Exception( "Unsupported protokoll " + protokoll);            
        }
        bis.close();
        
        return p_list;
        
    }

    void go_pop( ProxyEntry pe )
    {
        ServerSocket ss = null;
        
        String host = pe.getHost();
        int LocalPort = pe.getLocalPort();
        int RemotePort = pe.getRemotePort();
        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("BettyMailProxy is running for the host 'pop3://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
           

            while (!m_Stop)
            {
                try
                {
                    
                    Socket theSocket = ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the host '" + host + "' on local port " + pe.getLocalPort());
                    
                    POP3Connection m_POP3Connection = new POP3Connection( pe );
                    
                    if (m_Stop)
                    {
                        m_POP3Connection.closeConnections();
                        break;
                    }
                    
                    synchronized(connection_list)
                    {
                        connection_list.add(m_POP3Connection);
                    }
                    
                    m_POP3Connection.handleConnection(theSocket);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            Main.info_msg("stopping the pop3 proxy for host '" + host + "' on local port " + LocalPort);
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Check if this port is being used by other programs.";
            Main.err_log(errmsg);
            this.setStatusTxt("Not listening, port in use");
            this.setGoodState(false);
            
            //Common.showError(errmsg);
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

    void go_smtp(ProxyEntry pe)
    {
        ServerSocket ss = null;
        String host = pe.getHost();
        int LocalPort = pe.getLocalPort();
        int RemotePort = pe.getRemotePort();

        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("BettyMailProxy is running for the host 'smtp://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);

            

            while (!m_Stop)
            {
                try
                {
                    Socket theSocket = ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the host '" + host + "' on local port " + pe.getLocalPort());
                    
                    SMTPConnection m_SMTPConnection = new SMTPConnection( pe);
                    
                    if (m_Stop)
                    {
                        m_SMTPConnection.closeConnections();
                        break;
                    }
                        
                    synchronized(connection_list)
                    {
                        connection_list.add(m_SMTPConnection);
                    }
                    
                    
                    m_SMTPConnection.handleConnection(theSocket);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            Main.info_msg("stopping the smtp proxy for host '" + host + "' on local port " + LocalPort);
            
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Check if this port is being used by other programs.";
            Main.err_log(errmsg);
            this.setStatusTxt("Not listening, port in use");
            this.setGoodState(false);
            
            //Common.showError(errmsg);
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

    
    void go_imap( ProxyEntry pe )
    {
        ServerSocket ss = null;
        
        String host = pe.getHost();
        int LocalPort = pe.getLocalPort();
        int RemotePort = pe.getRemotePort();
        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("BettyMailProxy is running for the host 'imap://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
           

            while (!m_Stop)
            {
                try
                {
                    
                    Socket theSocket = ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the host '" + host + "' on local port " + pe.getLocalPort());
                    
                    IMAPConnection con = new IMAPConnection( pe );
                    
                    if (m_Stop)
                    {
                        con.closeConnections();
                        break;
                    }
                    
                    synchronized(connection_list)
                    {
                        connection_list.add(con);
                    }
                    
                    con.handleConnection(theSocket);

                } catch (SocketTimeoutException ste)
                {
                // timeout triggered
                // do nothing
                }
            }

            Main.info_msg("stopping the imap proxy for host '" + host + "' on local port " + LocalPort);
        } 
        catch (java.net.BindException be)
        {
            String errmsg = "The System could not bind the Socket on the local port " + LocalPort + " for the host '" + host + "'. Check if this port is being used by other programs.";
            Main.err_log(errmsg);
            this.setStatusTxt("Not listening, port in use");
            this.setGoodState(false);
            
            //Common.showError(errmsg);
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

    
    public void StopServer()
    {
        m_Stop = true;
        POP3Connection.StopServer();
        SMTPConnection.StopServer();
        IMAPConnection.StopServer();
                
        LogicControl.sleep(1100);
        
        while (!idle_worker.finished())
        {
            LogicControl.sleep(1000);
        }
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
        
        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }
       
        m_Stop = false;
        
        for (int i = 0; i < proxy_list.size(); i++)
        {
            final ProxyEntry pe = proxy_list.get(i);
            
            SwingWorker worker = new SwingWorker()
            {
                public Object construct()
                {
                   if (pe.getProtokoll() == ProxyEntry.POP3)
                       go_pop( pe );
                   else if (pe.getProtokoll() == ProxyEntry.SMTP)
                       go_smtp( pe );
/*                   else if (pe.getProtokoll() == ProxyEntry.IMAP)
                       go_imap( pe );
*/

                    return null;
                }
            };

            worker.start();
        }

        idle_worker = new SwingWorker()
        {
            public Object construct()
            {
                do_idle();

                return null;
            }
        };

        idle_worker.start();
        
       this.setStatusTxt("Running");
       this.setGoodState(true);        
       return true;
    }
    
    void do_idle()
    {                
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);
            
            // CLEAN UP LIST OF FINISHED CONNECTIONS
            synchronized(connection_list)
            {
                if ( m_Stop && connection_list.isEmpty())
                    break;
                
                
                for (int i = 0; i < connection_list.size(); i++)
                {
                    MailConnection m = connection_list.get(i);
                    if (!m.is_connected())
                    {
                        connection_list.remove(m);
                        i--;
                    }
                }
                if (this.isGoodState())
                {
                    if (connection_list.size() > 0)
                        this.setStatusTxt("Connected to " + connection_list.size() + " client(s)" );
                    else
                        this.setStatusTxt("");
                }
            }
        }
    }
    
    public String get_proxy_status_txt()
    {
        StringBuffer stb = new StringBuffer();
        
        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < proxy_list.size(); i++)
        {
            ProxyEntry pe = proxy_list.get(i);
            stb.append("PXPT"); stb.append(i); stb.append(":"); stb.append(pe.getProtokollStr());
            stb.append(" PXPR"); stb.append(i); stb.append(":"); stb.append(pe.getRemotePort() );
            stb.append(" PXPL"); stb.append(i); stb.append(":"); stb.append(pe.getLocalPort() );
            stb.append(" PXIN"); stb.append(i); stb.append(":"); stb.append(pe.getInstanceCnt()  );            
            stb.append(" PXHO"); stb.append(i); stb.append(":'"); stb.append(pe.getHost() + "' " );
        }
        
        return stb.toString();
    }

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        try
        {
            proxy_list = read_proxy_list();
        }
        catch (Exception exc)
        {
            Main.err_log("Cannot read proxy list: " + exc.getMessage());
            this.setStatusTxt("Proxylist is empty");
            this.setGoodState(false);
            
            return false;
        }
        return true;
    }
} // POP3Server

