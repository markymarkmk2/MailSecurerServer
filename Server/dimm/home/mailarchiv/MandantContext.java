/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.auth.GenericRealmAuth;
import dimm.home.importmail.HotFolderImport;
import dimm.home.importmail.MailBoxFetcher;
import dimm.home.importmail.MilterImporter;
import dimm.home.importmail.ProxyEntry;
import dimm.home.index.IMAP.IMAPBrowser;
import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.index.IndexManager;
import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import dimm.home.workers.HotfolderServer;
import dimm.home.workers.IMAPBrowserServer;
import dimm.home.workers.MailBoxFetcherServer;
import dimm.home.workers.MailProxyServer;
import dimm.home.workers.MilterServer;
import home.shared.CS_Constants;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.ImapFetcher;
import home.shared.hibernate.Milter;
import home.shared.hibernate.Proxy;
import home.shared.hibernate.Role;
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
    
    
    ArrayList<WorkerParent> worker_list;

    public MandantContext(  MandantPreferences _prefs, Mandant _m )
    {
        prefs = _prefs;
        mandant = _m;
        vaultArray = new ArrayList<Vault>();
        tempFileHandler = new TempFileHandler( this );
        worker_list = new ArrayList<WorkerParent>();
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
        Iterator <DiskArchive> it = getMandant().getDiskArchives().iterator();

        while( it.hasNext() )
        {
            DiskArchive da =  it.next();
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

    public void start_run_loop()
    {


        for (int i = 0; i < worker_list.size(); i++)
        {
            if (!worker_list.get(i).start_run_loop())
            {
                LogManager.err_log_fatal(Main.Txt("Cannot_start_runloop_for_Worker") + " " + worker_list.get(i).getName());
            }
        }
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
        IndexManager idx_util = new IndexManager(this, /*MailHeadervariable*/null, /*index_attachments*/ true);
        set_index_manager( idx_util );

        idx_util.initialize();
        worker_list.add(idx_util);


        // BUILD VAULT LIST FROM DISKARRAYS
        build_vault_list();

        // BUILD CACHE SEARCHERLIST
        idx_util.create_hash_searcher_list();


        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = control.get_milter_server();
        while (milter_it.hasNext())
        {
            try
            {
                ms.add_child( new MilterImporter(milter_it.next()));
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

        MailProxyServer ps = control.get_mail_proxy_server();
        while (proxy_it.hasNext())
        {
             ps.add_child( new ProxyEntry( proxy_it.next() ));
        }

        Set<Hotfolder> hfs = getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        HotfolderServer hf_server = control.get_hf_server();
        while (hf_it.hasNext())
        {
             hf_server.add_child( new HotFolderImport( hf_it.next() ));
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = control.get_mb_fetcher_server();
        while (if_it.hasNext())
        {
            ImapFetcher imf = if_it.next();
            int fl = Integer.parseInt(imf.getFlags());
            if ((fl & CS_Constants.IMF_DISABLED) == CS_Constants.IMF_DISABLED)
                continue;

            MailBoxFetcher child = MailBoxFetcher.mailbox_fetcher_factory( imf );
             mb_fetcher_server.add_child(  child );
        }

        if ( getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = control.get_imap_browser_server();
            try
            {
                LogManager.info_msg("Starting IMAP-Server for " + getMandant().getName() + " on " + getMandant().getImap_host() + ":" + getMandant().getImap_port() );

                ibs.add_child( new IMAPBrowser(this, getMandant().getImap_host(), getMandant().getImap_port()) );
            }
            catch (IOException ex)
            {
                LogManager.err_log_fatal(Main.Txt("Cannot_start_IMAP_server_for") + " " + getMandant().getName(), ex);
            }
        }

    }



    void teardown_mandant( )
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            WorkerParent wp = worker_list.get(i);
            if (!(wp instanceof TCPCallConnect))
            {
                wp.setShutdown(true);
            }
        }

        // DO NOT REMOVE COMM; WE ARE IN COMM RIGHT NOW
      /*  tcp_conn.setShutdown(true);
        worker_list.remove( tcp_conn );*/

        // REMOVE INDEXMANAGER
        index_manager.setShutdown(true);
        worker_list.remove(index_manager);


        if (worker_list.size() > 0)
        {
            LogManager.err_log_fatal(Main.Txt("Workerlist is not empty") + " " + getMandant().getName());
            worker_list.clear();
        }


        Set<Milter> milters = getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

        MilterServer ms = Main.get_control().get_milter_server();
        while (milter_it.hasNext())
        {
             ms.remove_child(milter_it.next());
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
             hf_server.remove_child( hf_it.next());
        }

        Set<ImapFetcher> ifs = getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        MailBoxFetcherServer mb_fetcher_server = Main.get_control().get_mb_fetcher_server();
        while (if_it.hasNext())
        {
              mb_fetcher_server.remove_child(if_it.next());
        }

        if ( getMandant().getImap_port() > 0)
        {
            IMAPBrowserServer ibs = Main.get_control().get_imap_browser_server();
            ibs.remove_child( getMandant() );
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
        UserSSOcache ussc = get_from_sso_cache( user,  pwd );
        if (ussc != null)
        {
            return ussc.mail_list;
        }
        return null;
    }

    class UserSSOcache
    {
        String user;
        String pwd;
        Role role;
        AccountConnector acct;
        long checked;
        long last_auth;
        ArrayList<String> mail_list;


        public UserSSOcache( String user, String pwd, Role role, AccountConnector acct, long checked, long last_auth )
        {
            this.user = user;
            this.pwd = pwd;
            this.role = role;
            this.acct = acct;
            this.checked = checked;
            this.last_auth = last_auth;
        }


    }

    ArrayList< UserSSOcache> user_sso_list = new ArrayList<UserSSOcache>();
    void remove_from_sso_cache( String user )
    {
        // REMOVE
        for (int i = 0; i < user_sso_list.size(); i++)
        {
            UserSSOcache entry = user_sso_list.get(i);
            if (entry.user.compareTo(user) == 0)
            {
                user_sso_list.remove(i);
                i--;
            }
        }
    }
    UserSSOcache get_from_sso_cache( String user, String pwd )
    {
        for (int i = 0; i < user_sso_list.size(); i++)
        {
            UserSSOcache entry = user_sso_list.get(i);
            if (entry.user.compareTo(user) == 0 && entry.pwd.compareTo(pwd) == 0 )
            {
                return entry;
            }
        }
        return null;
    }

    public boolean authenticate_user( String user, String pwd ) throws AuthException
    {
        boolean auth_ok = false;
        long now = System.currentTimeMillis();

        UserSSOcache usc = get_from_sso_cache( user, pwd);
        if (usc != null)
        {
            // USER STILL VALID
            if (now - usc.last_auth < prefs.get_long_prop( MandantPreferences.SSO_TIMEOUT_S, MandantPreferences.DFTL_SSO_TIMEOUT_S ))
            {
                // REWIND CLOCK
                usc.last_auth = System.currentTimeMillis();
                return true;
            }
            remove_from_sso_cache( user );
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
                    LogManager.debug_msg(4, "Skipping disabled role " + role.getName());
                    continue;
                }
            }
            catch (NumberFormatException numberFormatException)
            {
            }

            
            GenericRealmAuth auth_realm = GenericRealmAuth.factory_create_realm( acct );


            if (!auth_realm.connect())
            {
                LogManager.err_log("Cannot connect to realm " + acct.getType() + ":" +  acct.getIp() + ":" + acct.getPort());
                continue;

            }

            ArrayList<String> mail_list = null;
            try
            {
                mail_list = auth_realm.get_mailaliaslist_for_user(user);
            }
            catch (Exception namingException)
            {
                LogManager.err_log("Cannot retrieve mail list", namingException);
                auth_realm.disconnect();
                continue;
            }
            acct_connected = true;

            if (!auth_realm.user_is_member_of( role, user, mail_list ))
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
                // NEUER CACHE ENTRY 
                usc = new UserSSOcache(user, pwd, role, acct, now, now);

                // ALTE USERANGABEN RAUS
                remove_from_sso_cache( user );

                // NEUE DAZU
                user_sso_list.add(usc);

                // STORE ACT RESULTS
                usc.last_auth = now;
                usc.mail_list = mail_list;

                break;
            }
        }
        if (!acct_connected)
            throw new AuthException(Main.Txt("No_Realm_could_be_connected"));
        
        if (!role_found)
            throw new AuthException(Main.Txt("No_Role_matches_this_user"));


        // BENUTZER WAR OK / NICHT OK, BYE BYE
        return auth_ok;
    }




}
