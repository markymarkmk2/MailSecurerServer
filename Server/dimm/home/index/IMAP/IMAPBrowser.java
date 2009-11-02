/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import SK.gnome.dwarf.ServiceException;
import SK.gnome.dwarf.config.XMLConfiguration;
import SK.gnome.dwarf.config.XMLConfigurationException;
import SK.gnome.dwarf.log.FileLogger;
import SK.gnome.dwarf.log.LogServer;
import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.auth.MailPermission;
import SK.gnome.dwarf.mail.store.ACLStore;
import SK.gnome.dwarf.main.MainServer;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;



/**
 *
 * @author mw
 */
public class IMAPBrowser implements WorkerParentChild
{

    ServerSocket sock;
    MandantContext m_ctx;
    String host;
    int port;
//    final ArrayList<SKImapServer> srv_list;
    final ArrayList<MWImapServer> srv_list;
    private boolean started;
    private boolean finished;


    static int get_folder_validity( SKMailFolder fld )
    {
        return fld.uid_validity;
    }

    public String getHost()
    {
        return host;
    }

    public MandantContext get_ctx()
    {
        return m_ctx;
    }

    public int getPort()
    {
        return port;
    }


    public IMAPBrowser( MandantContext m_ctx, String host, int port ) throws IOException
    {
        this.m_ctx = m_ctx;
        this.host = host;
        this.port = port;
        log_debug(Main.Txt("Opening_socket"));


        sock = new ServerSocket(port, 0, InetAddress.getByName(host));

        do_finish = false;
        srv_list = new ArrayList<MWImapServer>();


    }

    private void log_debug( String s )
    {
        LogManager.debug_msg(s);
    }

    private void log_debug( String s, Exception e )
    {
        LogManager.debug_msg(s, e);
    }
    boolean do_finish;

    @Override
    public void finish()
    {
        do_finish = true;
        try
        {
            if (sock != null)
                sock.close();
            sock = null;
            
            synchronized (srv_list)
            {
                for (int i = 0; i < srv_list.size(); i++)
                {
                    MWImapServer imapServer = srv_list.get(i);
                    imapServer.close();
                }
            }
        }
        catch (IOException ex)
        {
        }
    }

    @Override
    public void run_loop()
    {
        started = true;

        log_debug(Main.Txt("Going_to_accept"));
        while (!do_finish)
        {
            try
            {
                Socket cl = sock.accept();

                MWImapServer mwimap = new MWImapServer(m_ctx, cl, true);
                srv_list.add(mwimap);
                mwimap.start();

            }
            catch (IOException iOException)
            {
                iOException.printStackTrace();
            }
        }
/*
        try
        {
            XMLConfiguration.setVerbosity(false);

            // create new XMLConfiguration instance and read the given XML configuration data

            XMLConfiguration cfg = new XMLConfiguration("dwarf/main.xml");

            // create new MainServer instance according to the given XML configuration

            MainServer server = (MainServer) cfg.getService();
            List src = server.getServices();

 

            // initialize the server

            server.init(null);

            LogServer logServer = (LogServer)server.getService("Log Server");
            FileLogger fl = (FileLogger)logServer.getService("File Logger");
            fl.setLevels("error");

            // start the server

            server.start();

            SKImapServer is = (SKImapServer) server.getService("IMAP4 Server");
 

            try
            {
                ACLStore acl_store = is.getACLStore("joe");
                acl_store.addPermission("joe", new MailPermission("*", "lrs"));
                acl_store.addPermission("joe", new MailPermission("*.*", "lrs"));
            }
            catch (MailException mailException)
            {
                mailException.printStackTrace();
            }

        }
        catch (XMLConfigurationException xMLConfigurationException)
        {
            xMLConfigurationException.printStackTrace();
        }
        catch (ServiceException serviceException)
        {
            serviceException.printStackTrace();
        }
*/
        /*
        MainServer mainServer = new MainServer("Main Server");
        mainServer.setLogFacility("server");

        SKImapServer srv = new SKImapServer(m_ctx, null, false);
        try
        {
        mainServer.addService(srv);
        mainServer.init(null);

        //            srv.init(null);
        //           srv.start();
        }
        catch (Exception serviceException)
        {
        serviceException.printStackTrace();
        }

        synchronized (srv_list)
        {
        srv_list.add(srv);
        }*/

        while (!do_finish)
        {
            try
            {
                LogicControl.sleep(1000);
            }
            catch (Exception e)
            {
                log_debug(Main.Txt("Unexpected_exception"), e);
            }
        }
        finished = true;
    }

    @Override
    public void idle_check()
    {
        synchronized (srv_list)
        {
            for (int i = 0; i < srv_list.size(); i++)
            {
                MWImapServer sr = srv_list.get(i);
                if (!sr.isAlive())
                {
                    srv_list.remove(i);
                    try
                    {
                        sr.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                    i = -1;
                    continue;
                }
            }
        }
    }

    public int getInstanceCnt()
    {
        int r = 0;
        synchronized (srv_list)
        {
            r = srv_list.size();
        }
        return r;
    }

    @Override
    public boolean is_started()
    {
        return started;
    }

    @Override
    public boolean is_finished()
    {
        return finished;
    }

    @Override
    public Object get_db_object()
    {
        return m_ctx.getMandant();
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

    

}

