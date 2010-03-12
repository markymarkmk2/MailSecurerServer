/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.index.SearchCall;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;

import java.net.Socket;
import java.util.ArrayList;



class MailFolderCache
{
    ArrayList<MailFolder> browse_mail_folders;
    String user;

    public MailFolderCache(String user )
    {
        browse_mail_folders = new ArrayList<MailFolder>();
        this.user = user;
    }

}
/**
 *
 * @author mw
 */
public class IMAPBrowser extends WorkerParentChild
{

    ServerSocket sock;
    MandantContext m_ctx;
    String host;
    int port;
    final ArrayList<MWImapServer> srv_list;
    ArrayList<MailFolderCache> folder_cache;


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
 
/*
 *


 * */
    public void set_search_results( SearchCall sc, String user, String pwd )
    {
        synchronized(srv_list)
        {
            log_debug("Adding " + sc.get_result_cnt() + " results to IMAP account ");
            for (int i = 0; i < srv_list.size(); i++)
            {
                MWImapServer mWImapServer = srv_list.get(i);
                if (mWImapServer.get_konto() == null)
                    continue;

                if (user.compareTo( mWImapServer.get_konto().get_username()) == 0 &&
                    pwd.compareTo( mWImapServer.get_konto().get_pwd()) == 0)
                {
                    try
                    {
                        if (mWImapServer.get_folder() == null)
                            continue;

                        if (mWImapServer.get_folder().getKey().startsWith(MailFolder.QRYTOKEN))
                        {
                            mWImapServer.get_folder().add_new_mail_resultlist(m_ctx, sc);
                            mWImapServer.has_searched = true;
                        }
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
    }



    public IMAPBrowser( MandantContext m_ctx, String host, int port ) throws IOException
    {
        this.m_ctx = m_ctx;
        this.host = host;
        this.port = port;
        log_debug(Main.Txt("Opening_socket"));

        sock = new ServerSocket(port, 0, InetAddress.getByName(host));

        srv_list = new ArrayList<MWImapServer>();
        folder_cache = new ArrayList<MailFolderCache>();
    }

    void log_debug( String s )
    {
        System.out.println(s);
//        LogManager.err_log(s);
//        LogManager.debug_msg(s);
    }

    void log_debug( String s, Exception e )
    {
        LogManager.err_log(s, e);
//        LogManager.debug_msg(s, e);
    }
    
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

        while (!do_finish)
        {
            try
            {
                log_debug(Main.Txt("Accepting_new_connection"));
                Socket cl = sock.accept();

                MWImapServer mwimap = new MWImapServer(this, m_ctx, cl, true);
                synchronized(srv_list)
                {
                    srv_list.add(mwimap);
                }
                mwimap.start();

                // WAIT FOR NEXT ACCEPT TO PREVENT DENIAL OF SERVICE
                sleep_seconds(1);

            }
            catch (Exception iOException)
            {
                if (!do_finish)
                    iOException.printStackTrace();
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
    public Object get_db_object()
    {
        return m_ctx.getMandant();
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

   

    @Override
    public String get_name()
    {
        return "IMAPBrowser";
    }

    MailFolder get_cached_folder( String user, String key)
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            for (int j = 0; j < entry.browse_mail_folders.size(); j++)
            {
                MailFolder folder = entry.browse_mail_folders.get(j);
                if (folder.key.equals(key))
                    return folder;
            }
        }
        return null;
    }

    void add_to_folder_cache( MailFolder folder, String user )
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            entry.browse_mail_folders.add( folder);
            return;
        }
        MailFolderCache entry = new MailFolderCache( user);
        entry.browse_mail_folders.add(folder);
        folder_cache.add(entry);
    }

    

}

