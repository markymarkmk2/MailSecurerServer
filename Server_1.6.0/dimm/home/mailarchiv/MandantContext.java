/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Notification.Notification;
import home.shared.SQL.UserSSOEntry;
import dimm.home.auth.GenericRealmAuth;
import dimm.home.importmail.HotFolderImport;
import dimm.home.importmail.MailBoxFetcher;
import dimm.home.importmail.MilterImporter;
import dimm.home.importmail.ProxyEntry;
import dimm.home.importmail.SMTPImporter;
import dimm.home.index.IMAP.IMAPServer;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.index.IndexManager;
import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.serverconnect.httpd.GWTServer;
import dimm.home.vault.BackupScript;
import dimm.home.vault.DiskSpaceHandler;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import dimm.home.vault.DiskVault;
import dimm.home.vault.ReIndexContext;
import dimm.home.vault.Vault;
import dimm.home.workers.BackupServer;
import dimm.home.workers.HotfolderServer;
import dimm.home.workers.IMAPBrowserServer;
import dimm.home.workers.MailBoxFetcherServer;
import dimm.home.workers.MailProxyServer;
import dimm.home.workers.MilterServer;
import dimm.home.workers.SMTPListener;
import home.shared.CS_Constants;
import home.shared.Utilities.SizeStr;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.Backup;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.ImapFetcher;
import home.shared.hibernate.Milter;
import home.shared.hibernate.Proxy;
import home.shared.hibernate.Role;
import home.shared.hibernate.RoleOption;
import home.shared.hibernate.SmtpServer;
import home.shared.mail.CryptAESOutputStream;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author mw
 */
public class MandantContext
{

    private static final long MIN_TMP_FREE_SPACE = 100000000l;  // 100 MB
    private static final long LOW_TMP_FREE_SPACE = 10000000000l; // 10GB

    private MandantPreferences prefs;
    private Mandant mandant;
    private ArrayList<Vault> vaultArray;
    private TempFileHandler tempFileHandler;
    private TCPCallConnect tcp_conn;
    private IndexManager index_manager;
    private IMAPServer imap_browser;
    ArrayList<WorkerParent> worker_list;
    private ReIndexContext rctx;
    long next_reinit_importbuffer;
    private boolean shutdown;
    ThreadPoolWatcher thread_watcher;

    GWTServer httpd_server;

    public MandantContext( MandantPreferences _prefs, Mandant _m )
    {
        prefs = _prefs;
        mandant = _m;
        vaultArray = new ArrayList<Vault>();
        tempFileHandler = new TempFileHandler(this);
        worker_list = new ArrayList<WorkerParent>();

        next_reinit_importbuffer = System.currentTimeMillis() + 5 * 60 * 1000; // CLEAN IMPORTBUFFER EVERY 1h

        thread_watcher = new ThreadPoolWatcher(mandant.getName());
    }

    public Mandant getMandant()
    {
        return mandant;
    }

    public ThreadPoolWatcher getThreadWatcher()
    {
        return thread_watcher;
    }


    void reload_mandant( Mandant m )
    {
        prefs.read_props();
        mandant = m;
    }
    public int get_int_flags()
    {
        if (mandant.getFlags() == null || mandant.getFlags().length() == 0)
            return 0;

        try
        {
            return Integer.parseInt(mandant.getFlags());
        }
        catch (NumberFormatException numberFormatException)
        {
            return 0;
        }
    }
    public boolean test_flag( int f)
    {
        int iflags = get_int_flags();
        return  ( (iflags & f) == f);
    }
    public int get_port()
    {
        int port = prefs.get_int_prop(MandantPreferences.PORT, Main.get_base_port() + 1 + mandant.getId());
        return port;
    }
    public String get_ip()
    {
        String ip = getPrefs().get_prop(MandantPreferences.SERVER_IP, Main.get_base_ip() );
        return ip;
    }
    
    public int get_httpd_port()
    {
        int port = 0;
        if (test_flag(CS_Constants.MA_HTTPS_ENABLE))
        {
            if (test_flag(CS_Constants.MA_HTTPS_OWN))
            {
                port = prefs.get_int_prop(MandantPreferences.HTTPD_PORT, Main.get_httpd_port() + 1 + mandant.getId());
            }
            else
            {
                port = Main.get_httpd_port();
            }
        }
        return port;
    }
    public boolean needs_smtp_auth()
    {
        return !test_flag(CS_Constants.MA_NO_SMTP_AUTH);
    }


    public TempFileHandler getTempFileHandler()
    {
        return tempFileHandler;
    }

    public DiskArchive get_da_by_id( long id )
    {
        Iterator<DiskArchive> it = getMandant().getDiskArchives().iterator();

        while (it.hasNext())
        {
            DiskArchive da = it.next();
            if (da.getId() == id)
            {
                return da;
            }
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
                DiskVault dv = (DiskVault) vault;
                if (dv.get_da().getId() == id)
                {
                    return dv;
                }
            }
        }
        return null;
    }
    DiskSpaceHandler last_dsh = null;

    public DiskSpaceHandler get_dsh( int ds_idx )
    {
        if (last_dsh != null && last_dsh.getDs().getId() == ds_idx)
        {
            return last_dsh;
        }

        for (int i = 0; i < getVaultArray().size(); i++)
        {
            Vault vault = getVaultArray().get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault) vault;
                dv.get_dsh_list();
                for (int j = 0; j < dv.get_dsh_list().size(); j++)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
                    if (dsh.getDs().getId() == ds_idx)
                    {
                        return dsh;
                    }
                }
            }
        }
        return null;
    }

    public Vault get_vault_for_ds_idx( int ds_idx )
    {

        for (int i = 0; i < getVaultArray().size(); i++)
        {
            Vault vault = getVaultArray().get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault) vault;
                dv.get_dsh_list();
                for (int j = 0; j < dv.get_dsh_list().size(); j++)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
                    if (dsh.getDs().getId() == ds_idx)
                    {
                        return dv;
                    }
                }
            }
        }
        return null;
    }

    public void build_vault_list()
    {
        Iterator<DiskArchive> it = getMandant().getDiskArchives().iterator();

        while (it.hasNext())
        {
            DiskArchive da = it.next();
            try
            {
                int flags = Integer.parseInt(da.getFlags());
                if ((flags & CS_Constants.DA_DISABLED) == CS_Constants.DA_DISABLED)
                {
                    continue;
                }
            }
            catch (NumberFormatException numberFormatException)
            {
            }

            getVaultArray().add(new DiskVault(this, da));
        }
    }

    private void delete_vault_list()
    {
        for (Iterator<Vault> it = vaultArray.iterator(); it.hasNext();)
        {
            Vault vault = it.next();
            try
            {
                vault.flush();
                vault.close();
            }
            catch (Exception indexException)
            {
                LogManager.msg_archive(LogManager.LVL_ERR, Main.Txt("Error while closing vault " + vault.get_name()), indexException);
            }
        }
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

    public static boolean has_trail_slash( String path )
    {
        int len = path.length();
        if (len > 0)
        {
            char lc = path.charAt(len - 1);
            if (lc == '/' || lc == '\\')
            {
                return true;
            }
        }
        return false;
    }

    public static String add_trail_slash( String path )
    {
        if (has_trail_slash(path))
        {
            return path;
        }

        return path + "/";
    }

    public static String del_trail_slash( String path )
    {
        if (!has_trail_slash(path))
        {
            return path;
        }

        return path.substring(0, path.length() - 1);
    }

    public File get_tmp_path()
    {
        String path = prefs.get_prop(MandantPreferences.TEMPFILEDIR, Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH));

        path = add_trail_slash(path);

        File tmp_path = new File(path + mandant.getId());

        if (tmp_path.exists() == false)
        {
            tmp_path.mkdirs();
        }

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
            try
            {
                Vault vault = vaultArray.get(i);
                vault.flush();
            }
            catch (IndexException ex)
            {
                LogManager.msg_index(LogManager.LVL_ERR, Main.Txt("Index_error_while_flushing_index"), ex);
            }
            catch (VaultException ex)
            {
                LogManager.msg_index(LogManager.LVL_ERR, Main.Txt("Vault_error_while_flushing_index"), ex);
            }
            catch (Exception ex)
            {
                LogManager.msg_index(LogManager.LVL_ERR, Main.Txt("Unknown_error_while_flushing_index"), ex);
            }
        }
    }

    public void start_run_loop()
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            if (!worker_list.get(i).start_run_loop())
            {
                LogManager.msg_system(LogManager.LVL_ERR, Main.Txt("Cannot_start_runloop_for_Worker") + " " + worker_list.get(i).getName());
            }
        }
    }

    boolean test_flag( String flag_str, int fl )
    {
        try
        {
            int flags = Integer.parseInt(flag_str);
            return ((flags & fl) == CS_Constants.IMF_DISABLED);
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return false;
    }

    void initialize_mandant( LogicControl control )
    {
        // ATTACH COMM IF NOT ALREADY DONE
        if (tcp_conn == null)
        {
            tcp_conn = new TCPCallConnect(this);
            worker_list.add(tcp_conn);
        }
        //set_tcp_call_connect( tcp_conn );

        // ATTACH INDEXMANAGER
        IndexManager idx_util = new IndexManager(this, /*MailHeadervariable*/ null, /*index_attachments*/ true);
        set_index_manager(idx_util);

        idx_util.initialize();
        worker_list.add(idx_util);


        // BUILD VAULT LIST FROM DISKARRAYS
        build_vault_list();

        // BUILD CACHE SEARCHERLIST
        try
        {
            idx_util.create_hash_searcher_list();
        }
        catch (Exception ex)
        {
            LogManager.msg_system(LogManager.LVL_ERR, Main.Txt("Cannot_create_has_searcher_list") + " " + getMandant().getName(), ex);
        }
        

        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = control.get_milter_server();
        while (milter_it.hasNext())
        {
            Milter milter = milter_it.next();
            if (test_flag(milter.getFlags(), CS_Constants.ML_DISABLED))
            {
                continue;
            }

            try
            {
                ms.add_child(new MilterImporter(milter));
            }
            catch (IOException ex)
            {
                ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                ms.setGoodState(false);
                LogManager.msg_system(LogManager.LVL_ERR,  ms.getStatusTxt(), ex);
            }
        }

        Set<Proxy> proxies = getMandant().getProxies();
        Iterator<Proxy> proxy_it = proxies.iterator();

        MailProxyServer ps = control.get_mail_proxy_server();
        while (proxy_it.hasNext())
        {
            ps.add_child(new ProxyEntry(proxy_it.next()));
        }

        Set<Hotfolder> hfs = getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        HotfolderServer hf_server = control.get_hf_server();
        while (hf_it.hasNext())
        {
            hf_server.add_child(new HotFolderImport(hf_it.next()));
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = control.get_mb_fetcher_server();
        while (if_it.hasNext())
        {
            ImapFetcher imf = if_it.next();
            if (test_flag(imf.getFlags(), CS_Constants.IMF_DISABLED))
            {
                continue;
            }

            MailBoxFetcher child = MailBoxFetcher.mailbox_fetcher_factory(imf);
            mb_fetcher_server.add_child(child);
        }

        Set<Backup> bas = getMandant().getBackups();
        Iterator<Backup> ba_it = bas.iterator();

        BackupServer ba_server = control.get_ba_server();
        while (ba_it.hasNext())
        {
            ba_server.add_child(new BackupScript(ba_it.next()));
        }


        Set<SmtpServer> smtps = getMandant().getSmtpServers();
        Iterator<SmtpServer> smtp_it = smtps.iterator();

        SMTPListener smtp_server = control.get_smtp_listener();
        while (smtp_it.hasNext())
        {
            SmtpServer smtp = smtp_it.next();
            if (test_flag(smtp.getFlags(), CS_Constants.SL_DISABLED))
            {
                continue;
            }
            smtp_server.add_child(new SMTPImporter(smtp));
        }

        if (getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = control.get_imap_browser_server();
            try
            {
                boolean imap_ssl = test_flag(CS_Constants.MA_IMAP_SSL);

                LogManager.msg_imaps(LogManager.LVL_INFO, "Starting " + (imap_ssl ? "SSL-" : "") + "IMAP-Server for " + getMandant().getName() + " on " + getMandant().getImap_host() + ":" + getMandant().getImap_port());


                imap_browser = new IMAPServer(this, getMandant().getImap_host(), getMandant().getImap_port(), imap_ssl);
                ibs.add_child(imap_browser);
            }
            catch (IOException ex)
            {
                LogManager.msg_imaps(LogManager.LVL_ERR,Main.Txt("Cannot_start_IMAP_server_for") + " " + getMandant().getName() + ": " + ex.getMessage());
            }
        }
        if (test_flag(CS_Constants.MA_HTTPS_ENABLE))
        {
            if ( test_flag(CS_Constants.MA_HTTPS_OWN))
            {
                GWTServer gwt = new GWTServer();
                try
                {
                    int httpd_port = get_httpd_port();
                    File war = new File("war", "MSWebApp.war");
                    if (war.exists())
                    {
                        LogManager.msg_system( LogManager.LVL_INFO, "Starting WebClient");
                        gwt.start(httpd_port, "war/MSWebApp.war",KeyToolHelper.get_ms_keystore().getAbsolutePath(), KeyToolHelper.get_ms_keystorepass());
                    }
                    else
                    {
                        LogManager.msg_system( LogManager.LVL_INFO, "No WebClient found at " + war.getAbsolutePath());
                    }
                }
                catch (Throwable throwable)
                {
                    System.out.println(throwable.getLocalizedMessage());
                    LogManager.printStackTrace(throwable);
                }
            }
            else
            {
                // CHECK IF WE NEED TO START A FORMERLY UNSTARTED HTTPDS
                control.fireup_httpds();
            }
        }

        String key = "1234567890123456789012345";
        try
        {
            ByteArrayOutputStream byos = new ByteArrayOutputStream();
            CryptAESOutputStream cos = new CryptAESOutputStream(byos, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), key);
            String s = cos.toString();
            cos.close();
        }
        catch (Exception exc)
        {
            Notification.throw_notification(mandant, Notification.NF_FATAL_ERROR, Main.Txt("Please_install_the_java_security_extension") );
            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_SYSTEM,"Testing key length " + key.length() + " NOK: " + exc.getMessage());
        }

    }

    void setShutdown( boolean b )
    {
        shutdown = b;
        for (int i = 0; i < worker_list.size(); i++)
        {
            worker_list.get(i).setShutdown(b);
        }
    }
    public boolean isShutdown()
    {
        return shutdown;
    }

    public boolean wait_for_shutdown( int secs )
    {

        while (secs-- > 0)
        {
            boolean ok = true;
            for (int i = 0; i < worker_list.size(); i++)
            {
                WorkerParent wp = worker_list.get(i);
                if (!(wp instanceof TCPCallConnect))
                {
                    if (!wp.isFinished())
                    {
                        ok = false;
                    }
                }
            }
            if (ok)
            {
                return true;
            }

            LogicControl.sleep(1000);
        }
        return false;
    }

    void teardown_mandant()
    {
        clear_sso_cache();

        if (httpd_server != null)
            httpd_server.stop();


        for (int i = 0; i < worker_list.size(); i++)
        {
            WorkerParent wp = worker_list.get(i);
            if (!(wp instanceof TCPCallConnect))
            {
                wp.setShutdown(true);
            }
        }

        thread_watcher.shutdown_thread_pools(5000);
        // REMOVE INDEXMANAGER
        index_manager.setShutdown(true);

        // DO NOT REMOVE COMM; WE ARE IN COMM RIGHT NOW
      /*  tcp_conn.setShutdown(true);
        worker_list.remove( tcp_conn );*/


        wait_for_shutdown(5);

        worker_list.remove(index_manager);


        if (worker_list.size() > 1)
        {
            LogManager.msg_system(LogManager.LVL_ERR, Main.Txt("Workerlist is not empty") + " " + getMandant().getName());
            worker_list.clear();
        }


        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = Main.get_control().get_milter_server();
        while (milter_it.hasNext())
        {
            ms.remove_child(milter_it.next());
        }

        Set<SmtpServer> smtps = getMandant().getSmtpServers();
        Iterator<SmtpServer> smtp_it = smtps.iterator();

        SMTPListener smtp_server = Main.control.get_smtp_listener();
        while (smtp_it.hasNext())
        {
            smtp_server.remove_child(smtp_it.next());
        }




        Set<Proxy> proxies = getMandant().getProxies();
        Iterator<Proxy> proxy_it = proxies.iterator();

        MailProxyServer ps = Main.get_control().get_mail_proxy_server();
        while (proxy_it.hasNext())
        {
            ps.remove_child(proxy_it.next());
        }

        Set<Hotfolder> hfs = getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        HotfolderServer hf_server = Main.get_control().get_hf_server();
        while (hf_it.hasNext())
        {
            hf_server.remove_child(hf_it.next());
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = Main.get_control().get_mb_fetcher_server();
        while (if_it.hasNext())
        {
            mb_fetcher_server.remove_child(if_it.next());
        }

        Set<Backup> bas = getMandant().getBackups();
        Iterator<Backup> ba_it = bas.iterator();

        BackupServer ba_server = Main.get_control().get_ba_server();
        while (ba_it.hasNext())
        {
            ba_server.remove_child(ba_it.next());
        }

        if (getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = Main.get_control().get_imap_browser_server();
            ibs.remove_child(getMandant());
        }
        // REMOVE VAULT LIST FROM DISKARRAYS
        delete_vault_list();
    }

    private boolean user_is_member_of( Role role )
    {
        return false;
    }

    public ArrayList<String> get_mailaliases( String user, String pwd )
    {
        UserSSOEntry ussc = get_from_sso_cache(user, pwd);
        if (ussc != null)
        {
            return ussc.getMail_list();
        }
        return null;
    }

    /**
     * @return the rctx
     */
    public ReIndexContext getRctx()
    {
        return rctx;
    }

    /**
     * @param rctx the rctx to set
     */
    public void setRctx( ReIndexContext rctx )
    {
        this.rctx = rctx;
    }

    public IMAPServer get_imap_server()
    {
        return imap_browser;
    }

    public void reinit_importbuffer()
    {
        next_reinit_importbuffer = System.currentTimeMillis();
    }

    // CALLED REGULARLY FROM OUTSIDE
    void idle_call()
    {
        long now = System.currentTimeMillis();

        if (now > next_reinit_importbuffer)
        {
            next_reinit_importbuffer = System.currentTimeMillis() + 60 * 60 * 1000; // CLEAN IMPORTBUFFER EVERY 1h

            try
            {
                resolve_hold_buffer();

                // NOT THESE, THEY ARE HANDLAD AUTOMAGICALLY
                //resolve_clientimport_buffer();
                //resolve_mailimport_buffer();
            }
            catch (Exception e)
            {
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Error while cleaning importbuffers", e);
            }
        }
    }
    static int user_sso_id;
    final ArrayList<UserSSOEntry> user_sso_list = new ArrayList<UserSSOEntry>();

    void clear_sso_cache()
    {
        synchronized (user_sso_list)
        {
            user_sso_list.clear();
        }
    }
    public void remove_from_sso_cache( String user )
    {
        synchronized (user_sso_list)
        {
            // REMOVE
            for (int i = 0; i < user_sso_list.size(); i++)
            {
                UserSSOEntry entry = user_sso_list.get(i);
                if (entry.getUser().compareTo(user) == 0)
                {
                    user_sso_list.remove(i);
                    i--;
                }
            }
        }
    }

    public UserSSOEntry get_sso( int id )
    {
        synchronized (user_sso_list)
        {
            for (int i = 0; i < user_sso_list.size(); i++)
            {
                UserSSOEntry entry = user_sso_list.get(i);
                if (entry.getUser_sso_id() == id)
                {
                    return entry;
                }
            }
        }
        return null;
    }

    public int get_sso_id( String user, String pwd )
    {
        UserSSOEntry ssoc = get_from_sso_cache(user, pwd);
        if (ssoc == null)
        {
            return -1;
        }

        return ssoc.getUser_sso_id();
    }

    public UserSSOEntry get_from_sso_cache( String user, String pwd )
    {
        synchronized (user_sso_list)
        {
            for (int i = 0; i < user_sso_list.size(); i++)
            {
                UserSSOEntry entry = user_sso_list.get(i);
                if (entry.getUser().compareTo(user) == 0 && entry.getPwd().compareTo(pwd) == 0)
                {
                    return entry;
                }
            }
        }
        return null;
    }

    public boolean is_sso_valid( int sso_id )
    {
        synchronized (user_sso_list)
        {
            UserSSOEntry sso = get_sso(sso_id);
            if (sso == null)
            {
                return false;
            }
            sso.setLast_auth( System.currentTimeMillis() );
        }
        return true;
        /* NO AUTOMATIC LOGOUT, DOESNT WORK CLEAN ON CLIENT
            long now = System.currentTimeMillis();

            // TOO OLD
            if (now - sso.getLast_auth() < prefs.get_long_prop(MandantPreferences.SSO_TIMEOUT_S, MandantPreferences.DFTL_SSO_TIMEOUT_S) * 1000)
            {
                // REWIND CLOCK
                sso.setLast_auth( System.currentTimeMillis() );
                return true;
            }
            user_sso_list.remove(sso);
        }
        return false;*/
    }

    public int create_admin_sso_id( String name, String pwd )
    {
        synchronized (user_sso_list)
        {
            long now = System.currentTimeMillis();

            // NEUER CACHE ENTRY
            user_sso_id++;  // GENERATE UNIQOE SINGLE SIGN ON ID
            UserSSOEntry usc = new UserSSOEntry(name, pwd, null, null, now, now, user_sso_id, mandant.getId());

            // ALTE USERANGABEN RAUS
            remove_from_sso_cache(name);

            // NEUE DAZU
            user_sso_list.add(usc);

            // STORE ACT RESULTS
            usc.setLast_auth( now );
            usc.setMail_list( null );

            return user_sso_id;
        }
    }
    public boolean role_has_option( Role role, String opt_token)
    {
        Set<RoleOption> ros = role.getRoleOptions();
        for (Iterator<RoleOption> it = ros.iterator(); it.hasNext();)
        {
            RoleOption roleOption = it.next();
            if (roleOption.getToken().equals(opt_token))
                return true;
        }
        return false;

    }

    public boolean authenticate_user( String user, String pwd ) throws AuthException
    {
        boolean auth_ok = false;
        long now = System.currentTimeMillis();

        synchronized (user_sso_list)
        {
            UserSSOEntry usc = get_from_sso_cache(user, pwd);
            if (usc != null)
            {
                // USER STAYS VALID UNLIMITED (PLUGINS)
                if (!usc.is_admin())
                {
                    // REWIND CLOCK
                    usc.setLast_auth( System.currentTimeMillis() );
                    return true;
                }

                // ADMINUSER STILL VALID
                long diff_s = (now - usc.getLast_auth()) / 1000;
                if (diff_s < prefs.get_long_prop(MandantPreferences.SSO_TIMEOUT_S, MandantPreferences.DFTL_SSO_TIMEOUT_S))
                {
                    // REWIND CLOCK
                    usc.setLast_auth( System.currentTimeMillis() );
                    return true;
                }
            }
        }
        boolean role_found = false;
        boolean acct_connected = false;


        // PRUEFE FÜR ALLE ROLLEN DIESES MANDANTEN
        for (Iterator<Role> it = this.mandant.getRoles().iterator(); it.hasNext();)
        {
            Role role = it.next();
            AccountConnector acct = role.getAccountConnector();

            try
            {
                if ((Integer.parseInt(role.getFlags()) & CS_Constants.ROLE_DISABLED) == CS_Constants.ROLE_DISABLED)
                {
                    LogManager.msg_auth(LogManager.LVL_DEBUG, "Skipping disabled role " + role.getName());
                    continue;
                }
            }
            catch (NumberFormatException numberFormatException)
            {
            }
            if ((acct.getFlags() & CS_Constants.ACCT_DISABLED) == CS_Constants.ACCT_DISABLED)
            {
                    LogManager.msg_auth(LogManager.LVL_DEBUG,  "Skipping disabled realm for role " + role.getName());
                    continue;
            }


            GenericRealmAuth auth_realm = GenericRealmAuth.factory_create_realm(acct);


            if (!auth_realm.connect())
            {
                LogManager.msg_auth(LogManager.LVL_WARN, "Cannot connect to realm " + acct.getType() + ":" + acct.getIp() + ":" + acct.getPort());
                continue;

            }

            ArrayList<String> mail_list = null;


            try
            {
                mail_list = auth_realm.get_mailaliaslist_for_user(user);
            }
            catch (Exception namingException)
            {
                LogManager.msg_auth(LogManager.LVL_ERR, "Cannot retrieve mail list", namingException);
                auth_realm.disconnect();
                continue;
            }
            acct_connected = true;

            if (!GenericRealmAuth.user_is_member_of(role, user, mail_list))
            {
                auth_realm.disconnect();
                continue;
            }

            // OKAY USER IS REGISTERED
            role_found = true;

            // PRUEFE OB DER BENUTZER OK IST
            auth_ok = auth_realm.open_user_context(user, pwd);

            // SCHLIESSEN NICHT VERGESSEN
            auth_realm.close_user_context();
            auth_realm.disconnect();


            // WENN OK, DANN RAUS HIER, ABER SCHNELL!!!
            if (auth_ok)
            {
                synchronized (user_sso_list)
                {
                    // WE CAN SHARE AN SSO OVER MULTIPLE CONECTIONS -> IMAP-LIENTS UND USER OFTEN LOGIN DURING REGULAR WORK
                    UserSSOEntry usc = get_from_sso_cache(user, pwd);
                    if (usc != null)
                    {
                        // STORE ACT RESULTS
                        usc.setLast_auth(now);
                        if (mail_list != null && !mail_list.isEmpty())
                            usc.setMail_list(mail_list);
                    }
                    else
                    {
                        // NEUER CACHE ENTRY
                        user_sso_id++;  // GENERATE UNIQOE SINGLE SIGN ON ID
                        usc = new UserSSOEntry(user, pwd, role, acct, now, now, user_sso_id, mandant.getId());

                        
                        remove_from_sso_cache(user);

                        // NEUE DAZU
                        user_sso_list.add(usc);

                        // STORE ACT RESULTS
                        usc.setLast_auth(now);
                        usc.setMail_list(mail_list);
                    }
                }
                break;
            }
        }
        if (!acct_connected)
        {
            throw new AuthException(Main.Txt("No_Realm_could_be_connected"));
        }

        if (!role_found)
        {
            throw new AuthException(Main.Txt("No_Role_matches_this_user"));
        }

        // BENUTZER WAR OK / NICHT OK, BYE BYE
        return auth_ok;
    }

    void resolve_hold_buffer()
    {
        try
        {
            File hold_buffer_dir = getTempFileHandler().get_hold_mail_path();
            if (hold_buffer_dir.exists() && hold_buffer_dir.listFiles().length > 0)
            {
                LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Trying_to_resolve_hold_buffer"));

                File[] flist = hold_buffer_dir.listFiles();

                for (int i = 0; i < flist.length; i++)
                {
                    File file = flist[i];
                    String hold_uuid = file.getName();

                    int da_id = Main.get_control().get_da_id_from_hold_buffer_filename(hold_uuid);
                    long time = Main.get_control().get_date_from_hold_buffer_filename(hold_uuid);
                    DiskVault dv = get_vault_by_da_id(da_id);
                    if (dv == null)
                    {
                        LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, Main.Txt("Cannot_clean_up_hold_buffer,_missing_diskvault_ID") + " " + da_id);
                        continue;
                    }
                    boolean encoded = hold_uuid.endsWith(RFCGenericMail.get_suffix_for_encoded());

                    RFCFileMail mf = new RFCFileMail(file, new Date(time), encoded);
                    try
                    {
                        mf.read_attributes();
                    }
                    catch (IOException iOException)
                    {
                        LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Cannot_read_attributes" + " " + iOException.getMessage());
                    }

                    // HANDLE A NOT IN BG
                    // DO NOT CATCH EXCEPTIONS, THIS WILL ABORT THIS LOOP AND BE CAUGHT DOWN THERE
                    Main.get_control().add_rfc_file_mail(mf, getMandant(), dv.get_da(), false, true);
                }
                LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Finishing_resolving_hold_buffer"));
            }
        }
        catch (Exception e) // CATCH ANY ERROR HERE
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Error while cleaning up hold buffer", e);
            return;
        }
    }

    File[] get_mailimport_buffer_list()
    {
        File import_buffer_dir = getTempFileHandler().get_import_mail_path();
        if (import_buffer_dir.exists() && import_buffer_dir.listFiles().length > 0)
        {
            LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Trying_to_resolve_import_buffer"));

            File[] flist = import_buffer_dir.listFiles();
            return flist;
        }
        return null;
    }

    void resolve_mailimport_buffer( File[] flist )
    {
        if (flist == null || flist.length == 0)
        {
            return;
        }

        LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Trying_to_resolve_mailimport_buffer"));
        try
        {

            for (int i = 0; i < flist.length; i++)
            {
                File file = flist[i];
                String uuid = file.getName();

                int da_id = Main.get_control().get_da_id_from_import_filemail(uuid);
                boolean encoded = uuid.endsWith(RFCGenericMail.get_suffix_for_encoded());

                // SKIP INVALID ENTRIES
                if (da_id == -1)
                {
                    continue;
                }

                DiskVault dv = get_vault_by_da_id(da_id);
                if (dv == null)
                {
                    LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Cannot_clean_up_import_buffer,_missing_diskvault_ID" + " " + da_id);
                    continue;
                }
                RFCFileMail mf = new RFCFileMail(file, new Date(), encoded);
                try
                {
                    mf.read_attributes();
                }
                catch (IOException iOException)
                {
                    LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Cannot_read_attributes" + " " + iOException.getMessage());
                }
                // HANDLE ADD NOT IN BG
                // DO NOT CATCH EXCEPTIONS, THIS WILL ABORT THIS LOOP AND BE CAUGHT DOWN THERE
                Main.get_control().add_rfc_file_mail(mf, getMandant(), dv.get_da(), false, true);

            }
            LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Finishing_resolving_import_buffer"));

        }
        catch (Exception e) // CATCH ANY ERROR HERE
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,"Error while cleaning up import buffer", e);
            return;
        }
    }

    File[] get_clientimport_buffer_list()
    {
        File import_buffer_dir = getTempFileHandler().get_clientimport_path();
        if (import_buffer_dir.exists() && import_buffer_dir.listFiles().length > 0)
        {
            LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Trying_to_resolve_clientimport_buffer"));

            File[] flist = import_buffer_dir.listFiles();
            return flist;
        }
        return null;
    }

    void resolve_clientimport_buffer( File[] flist )
    {
        try
        {

            for (int i = 0; i < flist.length; i++)
            {
                File file = flist[i];
                String uuid = file.getName();

                int da_id = getTempFileHandler().get_da_id_from_import_file(uuid);


                // SKIP INVALID ENTRIES
                if (da_id == -1)
                {
                    continue;
                }

                DiskVault dv = get_vault_by_da_id(da_id);
                if (dv == null)
                {
                    LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,Main.Txt("Cannot_clean_up_clientimport_buffer,_missing_diskvault_ID") + " " + da_id);
                    continue;
                }
                if (!dv.has_sufficient_space())
                {
                    LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,Main.Txt("Cannot_clean_up_clientimport_buffer,_not_enough_space") + " " + dv.get_name());
                    continue;
                }

                // DO NOT CATCH EXCEPTIONS, THIS WILL ABORT THIS LOOP AND BE CAUGHT DOWN THERE
                Main.get_control().register_new_import(this, dv.get_da(), file.getAbsolutePath());

            }
            LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT,Main.Txt("Finishing_resolving_import_buffer"));

        }
        catch (Exception e) // CATCH ANY ERROR HERE
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,"Error while cleaning up import buffer", e);
            return;
        }
    }

    boolean low_tmp_space_left;
    boolean no_tmp_space_left;

    private void set_no_tmp_space_left( boolean b)
    {
        // DETECT STATUS CHANGE
        if (no_tmp_space_left != b)
        {
            no_tmp_space_left = b;
            
            if (no_tmp_space_left)
            {
                Notification.throw_notification(this.getMandant(), Notification.NF_FATAL_ERROR, 
                    Main.Txt("No_space_left_in_working_directory") + ": " + get_tmp_path().getAbsolutePath());
            }
            else
            {
                Notification.throw_notification(this.getMandant(), Notification.NF_INFORMATIVE, 
                    Main.Txt("Space_in_working_directory_is_sufficient_again") + ": " + get_tmp_path().getAbsolutePath());
            }
        }
    }
    private void set_low_tmp_space_left( boolean b, double free)
    {
        if (low_tmp_space_left != b)
        {
            low_tmp_space_left = b;

            if (low_tmp_space_left)
            {
                String space = new SizeStr( free ).toString() + "B";
                Notification.throw_notification(this.getMandant(), Notification.NF_WARNING,
                    Main.Txt("Low_space_left_in_working_directory") + ": " + get_tmp_path().getAbsolutePath() + ": < " + space );
            }
        }
    }
    
    void check_free_space()
    {

        File tmp_path = this.get_tmp_path();
        double free = tmp_path.getFreeSpace();
        double total = tmp_path.getTotalSpace();

        set_no_tmp_space_left( free <MIN_TMP_FREE_SPACE );
        set_low_tmp_space_left( free <LOW_TMP_FREE_SPACE, free );
       
    }

    public boolean no_tmp_space_left()
    {
        return no_tmp_space_left;
    }


    // IF FLAG IS NOT SET, WE WILL WAIT, USER HAS TO ACTIVELY SET THIS FLAG TO IGNORE MAILS IN THIS STATE
    public boolean wait_on_no_space()
    {
        return !test_flag( CS_Constants.MA_NOWAIT_ON_NO_SPACE );
    }

    public Backup get_backup_by_id( long id )
    {
        Iterator<Backup> it = getMandant().getBackups().iterator();

        while (it.hasNext())
        {
            Backup ba = it.next();
            if (ba.getId() == id)
            {
                return ba;
            }
        }
        return null;
    }

    public AccountConnector get_accountconnector( long ac_id )
    {
        for (Iterator<AccountConnector> it = mandant.getAccountConnectors().iterator(); it.hasNext();)
        {
            AccountConnector ac = it.next();
            if (ac.getId() == ac_id)
                return ac;
        }
        return null;
    }


    
    /*
     * So geht rollen 4 augen
     *
     * Erzeugen (Haken setzen)darf nur Admin plus DSB
     *
     *  Alle mitglieder der Rolle 4 augen dürfen das Kreuz wieder mit Admin oder DSB ebtfernen
     *
     * 4-Augen Prinzip muss in allen CLients sichtbar sein (Plugins und Client)
     *
     * Alle Änderungen bei Rolle dürfen nur Admin  + DSB durchführen            + OK
     * *
     * Mail von 4-Augen Mitgliedern darf nicht vom Admin gelesen wedren (auch Subject nicht) Ersatztext (z.B. not for your Eyes)
     *  Beim Öffnen, Verschicken und Exportieren geht Authehtifitierung 4-Augen auf -> DSB oder user selbst)
     *
     * */

    /* Auditor wie admin, nur lesen (auch logs, aber keine 4-Eyes Mails)*/

   
}
