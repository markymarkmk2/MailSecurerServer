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
        ArrayList<MailFolder> update_list = new ArrayList<MailFolder>();
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
                        // WE SET EACH DIFFERENT FOLDER WITH NEW CONTENTS AND SET FLAG FOR EACH CONNECTION
                        // FOLDERS ARE SHRED BETWEEN CONNECTIONS
                        MailFolder folder = mWImapServer.get_selected_folder();
                        if (folder != null && folder.key.equals( MailFolder.QRYTOKEN))
                        {
                            if (!update_list.contains(folder))
                            {
                                folder.add_new_mail_resultlist(m_ctx, sc);
                                update_list.add(folder);
                            }
                            mWImapServer.set_has_searched( true );
                        }
                        // ADD ONLKY ONE RESULT TO EACH FOLDER
                        MailFolder qry_folder = get_cached_folder(user, MailFolder.QRYTOKEN);
                        if (qry_folder != null)
                        {
                            if (!update_list.contains(qry_folder))
                            {
                                qry_folder.add_new_mail_resultlist(m_ctx, sc);
                                update_list.add(qry_folder);
                            }
                            mWImapServer.set_has_searched( true );                            
                        }
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
        update_list.clear();
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

    void update_to_folder_cache( MailFolder new_folder, String user )
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;


            // REPLACE
            for (int j = 0; j < entry.browse_mail_folders.size(); j++)
            {
                MailFolder folder = entry.browse_mail_folders.get(j);
                if (folder.key.equals(new_folder.key))
                {
                    if (folder != new_folder)
                    {
                        entry.browse_mail_folders.remove(folder);
                        entry.browse_mail_folders.add(new_folder);
                    }
                    return;
                }
            }
            entry.browse_mail_folders.add( new_folder);
            return;
        }
        MailFolderCache entry = new MailFolderCache( user);
        entry.browse_mail_folders.add(new_folder);
        folder_cache.add(entry);
    }

    

}

