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
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;






public class MailProxyServer extends ListWorkerParent
{
    public static final String NAME = "MailProxyServer";

    final ArrayList<ProxyConnection> connection_list;

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
    }
    

    void go_pop( ProxyEntry pe )
    {
        ServerSocket ss = null;
        
        String host = pe.get_proxy().getRemoteServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();
        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("MailProxy is running for the host 'pop3://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
           

            while (!isShutdown() && !pe.get_finish())
            {
                try
                {
                    ss.setSoTimeout(1000);
                    Socket theSocket = ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the pop3 host '" + host + "' on local port " + pe.get_proxy().getLocalPort());
                    
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
                    
                    m_POP3Connection.handleConnection();

                }
                catch (SocketTimeoutException ste)
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
        String host = pe.get_proxy().getRemoteServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();

        try
        {
            ss = new ServerSocket(LocalPort);
            // 1 second timeout
            ss.setSoTimeout(1000);
            Main.info_msg("MailProxy is running for the host 'smtp://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);

            

            while (!isShutdown() && !pe.get_finish())
            {
                try
                {
                    Socket theSocket = ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the smtp host '" + host + "' on local port " + pe.get_proxy().getLocalPort());
                    
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
                    
                    
                    m_SMTPConnection.handleConnection();

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

        
    
    public void StopServer()
    {
        for (int i = 0; i < child_list.size(); i++)
        {
            final ProxyEntry pe = (ProxyEntry)child_list.get(i);
            pe.finish();
        }
        setShutdown(true);
        
        LogicControl.sleep(1100);
        
        while (!idle_worker.finished())
        {
            LogicControl.sleep(1000);
        }
    }

  
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
        Main.debug_msg(1, NAME + " is starting " + child_list.size() + " tasks" );
        
        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }
       
        setShutdown(false);
        
        for (int i = 0; i < child_list.size(); i++)
        {
            final ProxyEntry pe = (ProxyEntry)child_list.get(i);
            
            SwingWorker worker = new SwingWorker()
            {
                @Override
                public Object construct()
                {
                   if (is_pop3(pe.get_proxy()))
                       go_pop( pe );
                   else if (is_smtp(pe.get_proxy()))
                       go_smtp( pe );

                    return null;
                }
            };

            worker.start();
        }

        idle_worker = new SwingWorker()
        {
            @Override
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
    }
    
    public static BufferedOutputStream get_rfc_stream( File rfc_dump)
    {

        BufferedOutputStream bos = null;
        try
        {
            if (rfc_dump.exists())
            {
                Main.err_log_warn("Removing existing rfc_dump file " + rfc_dump.getName());
                rfc_dump.delete();
            }

            FileOutputStream fos = new FileOutputStream(rfc_dump);
            bos = new BufferedOutputStream(fos);

            rfc_dump.getFreeSpace();
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            Main.err_log_fatal("Cannot open rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

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

