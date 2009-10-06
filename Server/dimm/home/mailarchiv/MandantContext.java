/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.index.IndexManager;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import dimm.home.workers.HotfolderServer;
import dimm.home.workers.IMAPBrowserServer;
import dimm.home.workers.MailBoxFetcherServer;
import dimm.home.workers.MailProxyServer;
import dimm.home.workers.MilterServer;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.ImapFetcher;
import home.shared.hibernate.Milter;
import home.shared.hibernate.Proxy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author mw
 */
public class MandantContext
{
    private MandantPreferences prefs;
    private Mandant mandant;
    private ArrayList<Vault> vaultArray;
    private TempFileHandler tempFileHandler;
    private TCPCallConnect tcp_conn;
    private IndexManager index_manager;
    
    
    

    public MandantContext(  MandantPreferences _prefs, Mandant _m )
    {
        prefs = _prefs;
        mandant = _m;
        vaultArray = new ArrayList<Vault>();
        tempFileHandler = new TempFileHandler( this );
    }

   
    public Mandant getMandant()
    {
        return mandant;
    }

    void reload_mandant(Mandant m)
    {
        prefs.read_props();
        mandant = m;
    }


    public TempFileHandler getTempFileHandler()
    {
        return tempFileHandler;
    }
    

    public DiskArchive get_da_by_id( long id )
    {
        Iterator <DiskArchive> it = getMandant().getDiskArchives().iterator();

        while( it.hasNext() )
        {
            DiskArchive da =  it.next();
            if (da.getId() == id)
                return da;
        }
        return null;
    }
    public DiskVault get_vault_by_da_id( long id )
    {
        getVaultArray().iterator();

        for (int i = 0; i < vaultArray.size(); i++)
        {
            Vault vault = vaultArray.get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault)vault;
                if (dv.get_da().getId() == id)
                    return dv;
            }
        }
        return null;
    }
    
    public void build_vault_list()
    {
        Iterator <DiskArchive> it = getMandant().getDiskArchives().iterator();

        while( it.hasNext() )
        {
            DiskArchive da =  it.next();
            getVaultArray().add( new DiskVault( this, da ));
        }
    }
    private void delete_vault_list()
    {
        getVaultArray().clear();
    }


    
    public ArrayList<Vault> getVaultArray()
    {
        return vaultArray;
    }

    public MandantPreferences getPrefs()
    {
        return prefs;
    }

    public static boolean has_trail_slash( String path)
    {
        int len = path.length();
        if (len > 0)
        {
            char lc = path.charAt(len - 1);
            if (lc == '/' || lc == '\\')
                return true;
        }
        return false;
    }
    public static String add_trail_slash( String path)
    {
        if (has_trail_slash(path))
            return path;

        return path + "/";
    }
    public static String del_trail_slash( String path)
    {
        if (!has_trail_slash(path))
            return path;

        return path.substring(0, path.length() - 1);
    }

    public File get_tmp_path()
    {
        String path = prefs.get_prop(MandantPreferences.TEMPFILEDIR, Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH));

        path = add_trail_slash(path);

        File tmp_path = new File( path + mandant.getId() );

        if (tmp_path.exists() == false)
            tmp_path.mkdirs();

        return tmp_path;
    }

    void set_tcp_call_connect( TCPCallConnect _tcp_conn )
    {
        tcp_conn = _tcp_conn;
    }

    public TCPCallConnect get_tcp_call_connect()
    {
        return tcp_conn;
    }

    void set_index_manager( IndexManager idx_util )
    {
        index_manager = idx_util;
        
    
    }
    public IndexManager get_index_manager()
    {
        return index_manager;
    }

    public void flush_index()
    {
        for (int i = 0; i < vaultArray.size(); i++)
        {
            Vault vault = vaultArray.get(i);
            vault.flush();
        }        
    }



    void initialize_mandant(  )
    {

        ArrayList<WorkerParent> worker_list = Main.get_control().get_worker_list();

        // ATTACH COMM
        TCPCallConnect tcp_conn = new TCPCallConnect(this);
        worker_list.add(tcp_conn);
        set_tcp_call_connect( tcp_conn );

        // ATTACH INDEXMANAGER
        IndexManager idx_util = new IndexManager(this, /*MailHeadervariable*/null, /*index_attachments*/ true);
        set_index_manager( idx_util );

        idx_util.initialize();
        worker_list.add(idx_util);


        // BUILD VAULT LIST FROM DISKARRAYS
        build_vault_list();


        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = Main.get_control().get_milter_server();
        while (milter_it.hasNext())
        {
            try
            {
                ms.add_milter(milter_it.next());
            }
            catch (IOException ex)
            {
                ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                ms.setGoodState(false);
                LogManager.err_log_fatal(ms.getStatusTxt(), ex);
            }
        }

        Set<Proxy> proxies = getMandant().getProxies();
        Iterator<Proxy> proxy_it = proxies.iterator();

        MailProxyServer ps = Main.get_control().get_mail_proxy_server();
        while (proxy_it.hasNext())
        {
             ps.add_proxy(proxy_it.next());
        }

        Set<Hotfolder> hfs = getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        HotfolderServer hf_server = Main.get_control().get_hf_server();
        while (hf_it.hasNext())
        {
             hf_server.add_hfolder(hf_it.next());
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = Main.get_control().get_mb_fetcher_server();
        while (if_it.hasNext())
        {
             mb_fetcher_server.add_fetcher(if_it.next());
        }

        if ( getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = Main.get_control().get_imap_browser_server();
            try
            {
                ibs.add_browser(this, null, getMandant().getImap_port());
            }
            catch (IOException ex)
            {
                LogManager.err_log_fatal(Main.Txt("Cannot_start_IMAP_server_for") + " " + getMandant().getName(), ex);
            }
        }

    }



    void teardown_mandant( )
    {
        // REMOVE COMM
        ArrayList<WorkerParent> worker_list = Main.get_control().get_worker_list();


        worker_list.remove( tcp_conn );
        tcp_conn.setShutdown(true);

        // REMOVE INDEXMANAGER
       
        worker_list.remove(index_manager);
        index_manager.setShutdown(true);




        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = Main.get_control().get_milter_server();
        while (milter_it.hasNext())
        {
             ms.remove_milter(milter_it.next());
        }

        Set<Proxy> proxies = getMandant().getProxies();
        Iterator<Proxy> proxy_it = proxies.iterator();

        MailProxyServer ps = Main.get_control().get_mail_proxy_server();
        while (proxy_it.hasNext())
        {
             ps.remove_proxy(proxy_it.next());
        }

        Set<Hotfolder> hfs = getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        HotfolderServer hf_server = Main.get_control().get_hf_server();
        while (hf_it.hasNext())
        {
             hf_server.remove_hfolder(hf_it.next());
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = Main.get_control().get_mb_fetcher_server();
        while (if_it.hasNext())
        {
             mb_fetcher_server.remove_fetcher(if_it.next());
        }

        if ( getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = Main.get_control().get_imap_browser_server();
            ibs.remove_browser(this, null, getMandant().getImap_port());
        }
        // REMOVE VAULT LIST FROM DISKARRAYS
        delete_vault_list();


    }




}
