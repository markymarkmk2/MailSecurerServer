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
import home.shared.mail.EncodedMailOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
        
        ServerSocket pop3_ss = null;
        String host = pe.get_proxy().getRemoteServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();
        try
        {
            pop3_ss = new ServerSocket(LocalPort);
            // 1 second timeout
            pop3_ss.setSoTimeout(1000);

            pe.set_server_sock( pop3_ss );

            Main.info_msg("MailProxy is running for the host 'pop3://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
           

            while (!isShutdown() && !pe.is_finished())
            {
                try
                {
                    pop3_ss.setSoTimeout(1000);
                    Socket theSocket = pop3_ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the pop3 host '" + host + "' on local port " + pe.get_proxy().getLocalPort());

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
            if (!pe.is_finished())
                Main.err_log(ex.getMessage());
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
                Main.err_log(e.getMessage());
            }
        }

    }  // go	

    
    void go_smtp(ProxyEntry pe)
    {
        ServerSocket smtp_ss = null;
        
        String host = pe.get_proxy().getRemoteServer();
        int LocalPort = pe.get_proxy().getLocalPort();
        int RemotePort = pe.get_proxy().getRemotePort();

        try
        {
            smtp_ss = new ServerSocket(LocalPort);
            // 1 second timeout
            smtp_ss.setSoTimeout(1000);
            Main.info_msg("MailProxy is running for the host 'smtp://" + host +
                    ":" + RemotePort + "' on local port " + LocalPort);
            pe.set_server_sock( smtp_ss );

            

            while (!isShutdown() && !pe.is_finished())
            {
                try
                {
                    Socket theSocket = smtp_ss.accept();
                    Main.debug_msg( 2, "Connection accepted for the smtp host '" + host + "' on local port " + pe.get_proxy().getLocalPort());

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
        if (smtp_ss != null)
        {
            try
            {
                smtp_ss.close();
                smtp_ss = null;
            } catch (Exception e)
            {
                Main.err_log(e.getMessage());
            }
        }

    }  // go

    @Override
    public void setShutdown( boolean shutdown )
    {
        super.setShutdown(shutdown);
        if (!shutdown)
            return;
        
   

        for (int i = 0; i < child_list.size(); i++)
        {
            final ProxyEntry pe = (ProxyEntry)child_list.get(i);
            pe.finish();
        }

        LogicControl.sleep(1100);

        while (!idle_worker.finished())
        {
            LogicControl.sleep(1000);
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
        Main.debug_msg(1, NAME + " is starting " + child_list.size() + " tasks" );
        
        if (!Main.get_control().is_licensed())
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
            
            SwingWorker worker = new SwingWorker()
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
    
    public static BufferedOutputStream get_rfc_stream( File rfc_dump, boolean encoded)
    {

        BufferedOutputStream bos = null;
        try
        {
            if (rfc_dump.exists())
            {
                Main.err_log_warn("Removing existing rfc_dump file " + rfc_dump.getName());
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

