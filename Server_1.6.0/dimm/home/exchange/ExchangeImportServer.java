/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package dimm.home.exchange;

import com.microsoft.schemas.exchange.services._2006.messages.ExchangeServicePortType;
import com.microsoft.schemas.exchange.services._2006.types.ArrayOfRealItemsType;
import com.microsoft.schemas.exchange.services._2006.types.BaseFolderIdType;
import com.microsoft.schemas.exchange.services._2006.types.BaseFolderType;
import com.microsoft.schemas.exchange.services._2006.types.ConnectingSIDType;
import com.microsoft.schemas.exchange.services._2006.types.DistinguishedFolderIdNameType;
import com.microsoft.schemas.exchange.services._2006.types.DistinguishedFolderIdType;
import com.microsoft.schemas.exchange.services._2006.types.ExchangeImpersonationType;
import com.microsoft.schemas.exchange.services._2006.types.ExchangeVersionType;
import com.microsoft.schemas.exchange.services._2006.types.FolderIdType;
import com.microsoft.schemas.exchange.services._2006.types.ItemIdType;
import com.microsoft.schemas.exchange.services._2006.types.ItemType;
import dimm.home.auth.GenericRealmAuth;
import dimm.home.importmail.ImapEnvelopeFetcher;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Exceptions.ImportException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.SQL.UserSSOEntry;
import home.shared.Utilities.SizeStr;
import home.shared.exchange.ExchangeAuthenticator;
import home.shared.exchange.dao.ItemTypeDAO;
import home.shared.exchange.util.ExchangeEnvironmentSettings;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCMimeMail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.mail.internet.MimeMessage;
import javax.swing.Timer;
import net.freeutils.tnef.mime.TNEFMime;
import org.apache.commons.codec.binary.Base64;

/*
 *
 * Das hier muss auf der MS Exchnage Console
 *
Get-ExchangeServer | where { $_.IsClientAccessServer -eq $TRUE} | ForEach-Object {Add-ADPermission -Identity $_.distinguishedname -User (Get-User -Identity Administrator | select-object).identity -extendedRight ms-Exch-EPI-Impersonation }

2K7
Get-ExchangeServer | Where {$_.ServerRole -match "ClientAccess"} | Add-ADPermission -User "Administrator" -ExtendedRights ms-Exch-EPI-Impersonation -InheritanceType None

and:

Get-MailboxDatabase | Add-ADPermission -User "Administrator" -ExtendedRights ms-Exch-EPI-May-Impersonate -InheritanceType All*
 *
 * 2K10
 * New-ManagementRoleAssignment -Name:impersonationAssignmentName -Role:ApplicationImpersonation -User:Administrator

 *
 *
 */


abstract class ExchangeImporterEntry
{
    UserSSOEntry sso;
    Mandant mandant;
    DiskArchive da;
    ExchangeVersionType ev;

    int total_msg;
    int act_msg;
    long size;
    float mb_per_s;
    private String status;
    private int err;

    // HELPERS FOR KEEPING ARGLIST SMALL
    ArrayList<String> act_user_mail;
    String act_user;

    public ExchangeImporterEntry( UserSSOEntry sso, Mandant mandant, DiskArchive da, ExchangeVersionType ev )
    {
        this.sso = sso;
        this.mandant = mandant;
        this.da = da;
        this.ev = ev;
        total_msg = 0;
        act_msg = 0;
        size = 0;
        mb_per_s = 0.0f;
        status = "";
        err = 0;
    }


    boolean is_in_rebuild()
    {
        Vault v = Main.get_control().get_m_context(mandant).get_vault_by_da_id(da.getId());
        return v.is_in_rebuild();
    }
    void set_status( int err, String string )
    {
        _set_status( string );
        this.err = err;
    }
    void set_status( String string )
    {
        _set_status( string );
        this.err = 0;
    }


    private void _set_status( String string )
    {
        synchronized(this)
        {
            if (status.compareTo(string) == 0)
                return;
            
            status = string;
        }
        if (status.length() > 0)
        {
            LogManager.msg_exchange( LogManager.LVL_DEBUG, status );
        }
    }
    String get_status(  )
    {
        synchronized(this)
        {
            return status;
        }
    }

    int get_err()
    {
        return err;
    }

}

class ExchangeImporterFolderEntry extends ExchangeImporterEntry
{

    ArrayList<BaseFolderIdType>folder_list;

    String user;
    String domain;
    String server;
    String pwd;

    ExchangeImporterFolderEntry( UserSSOEntry sso, Mandant m, DiskArchive da, ArrayList<BaseFolderIdType>folder_list,
            String user, String pwd, String domain, String server, ExchangeVersionType ev)
    {
        super( sso, m, da, ev );
        this.folder_list = folder_list;
        this.user = user;
        this.pwd = pwd;
        this.domain = domain;
        this.server = server;
    }

   

}

class ExchangeImporterUserEntry extends ExchangeImporterEntry
{
    ArrayList<DistinguishedFolderIdNameType>folder_list;
    ArrayList<String>user_list;
    boolean user_folders;
    long ac_id;
    String domain;

    ExchangeImporterUserEntry( UserSSOEntry sso, Mandant m, DiskArchive da, long ac_id, String domain, ArrayList<DistinguishedFolderIdNameType> folder_list,
            ArrayList<String> user_list, boolean user_folders, ExchangeVersionType ev )
    {
        super( sso, m, da, ev );
        this.folder_list = folder_list;
        this.user_list = user_list;
        this.user_folders = user_folders;
        this.ac_id = ac_id;
        this.domain = domain;
    }

}

class ExchangeMailEntry
{
    ItemIdType id;
    int size;
    String subject;

    public ExchangeMailEntry( ItemIdType id, int size, String s )
    {
        this.id = id;
        this.size = size;
        this.subject = s;
    }

}
/**
 *
 * @author Administrator
 */
public class ExchangeImportServer extends WorkerParent
{

    public static final String NAME = "ExchangeImportServer";


    Timer timer;
    final ArrayList<ExchangeImporterEntry> import_list;
    BackgroundWorker idle_worker;
    boolean m_Stop = false;

    // AFTER 20M OR 500 MAILS A NEW IMPORT-CALL TO EXCHANGE IS CREATED
    private long max_chunk_size = 20*1024*1024;
    private int max_mailcount_threshold = 500;

    ExchangeImporterEntry active_entry;
    private ExchangeImporterEntry last_error_entry;
    final String entry_lock = "";

    /** Creates a new instance of StatusDisplay */
    public ExchangeImportServer()
    {
        super(NAME);
        import_list = new ArrayList<ExchangeImporterEntry>();
        max_chunk_size = Main.get_prefs().get_long_prop(GeneralPreferences.EXCHANGE_IMPORT_MAX_CHUNK_SIZE, max_chunk_size);
        max_mailcount_threshold = (int)Main.get_prefs().get_long_prop(GeneralPreferences.EXCHANGE_IMPORT_MAX_MAILCOUNT_TRESHOLD, max_mailcount_threshold);
        active_entry = null;
        last_error_entry = null;
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    public void add_exchange_import( UserSSOEntry sso, MandantContext m_ctx, DiskArchive da, ArrayList<BaseFolderIdType>folder_list,
             String user, String pwd, String domain, String server, ExchangeVersionType ev)
    {
        ExchangeImporterFolderEntry mbie = new ExchangeImporterFolderEntry(sso, m_ctx.getMandant(), da, folder_list,
                 user, pwd, domain, server, ev );
        synchronized (import_list)
        {
            import_list.add(mbie);
        }
    }
    private void add_exchange_import( UserSSOEntry sso, MandantContext m_ctx, DiskArchive da, long ac_id, String domain, ArrayList<DistinguishedFolderIdNameType> folder_list,
            ArrayList<String> user_list, boolean user_folders, ExchangeVersionType ev )
    {
        ExchangeImporterUserEntry mbie = new ExchangeImporterUserEntry(sso, m_ctx.getMandant(), da, ac_id, domain, folder_list, user_list, user_folders, ev );
        synchronized (import_list)
        {
            import_list.add(mbie);
        }
    }

    @Override
    public boolean start_run_loop()
    {
        if (is_started)
            return true;

        idle_worker = new BackgroundWorker(NAME)
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

        this.setStatusTxt(ST_IDLE);
        this.setGoodState(true);
        return true;
    }

    void work_jobs()
    {
        while (true)
        {
            ExchangeImporterEntry exie = null;

            // GET FIFO ENTRY
            synchronized (import_list)
            {
                if (m_Stop && import_list.isEmpty())
                {
                    break;
                }

                if (!import_list.isEmpty())
                {
                    exie = import_list.get(0);
                    
                    // ARE WE BUSY
                    if (exie.is_in_rebuild())
                        break;

                    import_list.remove(exie);

                }
            }
            // LIST EMPTY ?
            if (exie == null)
                break;

            setStatusTxt(Main.Txt("Importing_mailbox"));
            synchronized( entry_lock )
            {
                active_entry = exie;
            }
            
            run_import(exie);

            synchronized (entry_lock)
            {
                if (exie.get_err() != 0)
                {
                    last_error_entry = active_entry;
                }
                active_entry = null;
            }
            
            setStatusTxt(ST_IDLE);
        }
    }

    void do_idle()
    {
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);

            try
            {
                work_jobs();
            }
            catch (Exception e)
            {
                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, "Work_jobs got exception", e);
            }
         }
        finished = true;
    }


    public static void register_import( UserSSOEntry sso, MandantContext m_ctx, DiskArchive da, ArrayList<BaseFolderIdType> folder_list,
            String user, String pwd, String domain, String server, ExchangeVersionType ev )
    {
        Main.get_control().get_ex_import_server().add_exchange_import(sso, m_ctx, da, folder_list, user, pwd, domain, server, ev );
    }
    public static void register_import( UserSSOEntry sso, MandantContext m_ctx, DiskArchive da, long ac_id, String domain,
            ArrayList<DistinguishedFolderIdNameType> folder_list, ArrayList<String> user_list, boolean user_folders, ExchangeVersionType ev )
    {
        Main.get_control().get_ex_import_server().add_exchange_import(sso, m_ctx, da, ac_id, domain, folder_list, user_list, user_folders, ev );
    }

    void run_import( ExchangeImporterEntry exie )
    {
        if (exie instanceof ExchangeImporterFolderEntry)
        {
            run_folder_import( (ExchangeImporterFolderEntry) exie);
        }
        if (exie instanceof ExchangeImporterUserEntry)
        {
            run_users_import( (ExchangeImporterUserEntry) exie);
        }
    }
    void run_users_import( ExchangeImporterUserEntry exie )
    {
        ItemTypeDAO itemTypeDAO = null;
        ExchangeServicePortType port = null;

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());

        ExchangeAuthenticator.reduce_ssl_security();

        ArrayList<DistinguishedFolderIdNameType> folder_list = exie.folder_list;
        ArrayList<String> user_list = exie.user_list;

        AccountConnector ac = m_ctx.get_accountconnector( exie.ac_id );

        ArrayList<ArrayList<String>> users_mail_list = new ArrayList<ArrayList<String>>();


        // USERLIST CONTAINS LIST OF USERPRINCIPALNAMES
        if (user_list == null || user_list.isEmpty())
        {
            try
            {
                GenericRealmAuth realm = GenericRealmAuth.factory_create_realm(ac);
                realm.connect();
                user_list = realm.list_users_for_group("");

                // NOW ADD THE EMAIL-LIST FOR EACH USER TO EMAIL-LIST-LIST
                for (int i = 0; i < user_list.size(); i++)
                {
                    String user = user_list.get(i);
                    ArrayList<String> _list = new ArrayList<String>();
                    _list.add(user);

                    ArrayList<String> user_mail = realm.list_mailaliases_for_userlist( _list );
                    users_mail_list.add(user_mail);
                }

                realm.disconnect();
            }
            catch (Exception ex)
            {
                exie.set_status( 1, "Error while retrieving user list:" );

                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), ex);
                return;
            }
        }


        ExchangeEnvironmentSettings settings = null;
        try
        {
            port = ExchangeAuthenticator.open_exchange_port(ac.getUsername(), ac.getPwd(), exie.domain, ac.getIp());
            settings = new ExchangeEnvironmentSettings(ExchangeEnvironmentSettings.get_cultures()[0], exie.ev);
        }
        catch (Exception e)
        {
            exie.set_status( 2, "Error while opening exchange connection:" );

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), e);
            return;
        }
        itemTypeDAO = new ItemTypeDAO(settings);

        try
        {
            for (int i = 0; i < user_list.size(); i++)
            {
                String user = user_list.get(i);

                // SET IMPERSONATION
                ExchangeImpersonationType impersonation = new ExchangeImpersonationType();
                ConnectingSIDType cid = new ConnectingSIDType();
                cid.setPrincipalName(user);
                impersonation.setConnectingSID(cid);

                itemTypeDAO.setImpersonation(impersonation);

                try
                {
                    // SET WORKING VARS
                    exie.act_user_mail = users_mail_list.get(i);
                    exie.act_user = user;

                    run_user_import( exie, port, itemTypeDAO );
                }
                catch (Exception e)
                {
                    LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), e);
                }
            }
        }
        catch (Exception e)
        {
            exie.set_status( 2, "Error while opening reteiving exchange mail data:");

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), e);
            return;
        }

    }

    private void add_to_merged_list( ExchangeImporterUserEntry exie, List<BaseFolderType> all_folder_list, List<BaseFolderIdType> merged_folder_list, FolderIdType folder )
    {
        for (int i = 0; i < all_folder_list.size(); i++)
        {
            BaseFolderType bf = all_folder_list.get(i);
            if (bf.getParentFolderId().getId().equals(folder.getId()) )
            {
                exie.set_status( Txt("Adding_subfolder") + " " + bf.getDisplayName());
                
                // ADD THE FOUND SUBENTRY
                if (!merged_folder_list.contains(bf.getFolderId()))
                    merged_folder_list.add(bf.getFolderId());

                // AND ADD THE SUB-SUB-ENTRIES
                add_to_merged_list( exie, all_folder_list, merged_folder_list, bf.getFolderId() );
            }
        }
    }

    private ArrayList<BaseFolderIdType> create_exclude_folder_id_list()
    {

        DistinguishedFolderIdNameType[] excluded_ids =
        {
            DistinguishedFolderIdNameType.JUNKEMAIL,
            DistinguishedFolderIdNameType.OUTBOX,
            DistinguishedFolderIdNameType.DELETEDITEMS,
            DistinguishedFolderIdNameType.SEARCHFOLDERS,
            DistinguishedFolderIdNameType.DRAFTS

        };
        return create_folder_id_list( excluded_ids );
    }

    private ArrayList<BaseFolderIdType> create_folder_id_list(DistinguishedFolderIdNameType[] id_name_list)
    {
        ArrayList<BaseFolderIdType> ret = new ArrayList<BaseFolderIdType>();

        for (int i = 0; i < id_name_list.length; i++)
        {
            DistinguishedFolderIdNameType distinguishedFolderIdNameType = id_name_list[i];

            DistinguishedFolderIdType folderIdType = new DistinguishedFolderIdType();
            folderIdType.setId(distinguishedFolderIdNameType);

            ret.add( folderIdType );
        }

        return ret;
    }
    private ArrayList<BaseFolderIdType> create_folder_id_list(ArrayList<DistinguishedFolderIdNameType> id_name_list)
    {
        ArrayList<BaseFolderIdType> ret = new ArrayList<BaseFolderIdType>();

        for (int i = 0; i < id_name_list.size(); i++)
        {
            DistinguishedFolderIdNameType distinguishedFolderIdNameType = id_name_list.get(i);

            DistinguishedFolderIdType folderIdType = new DistinguishedFolderIdType();
            folderIdType.setId(distinguishedFolderIdNameType);

            ret.add( folderIdType );
        }

        return ret;
    }

    private void get_subfolders( ExchangeServicePortType port, ItemTypeDAO itemTypeDAO, BaseFolderType baseFolderType, List<BaseFolderType> subfolders_folder_list )
    {
        if (baseFolderType.getChildFolderCount() > 0)
        {
            List<BaseFolderType> folders = null;
            try
            {
                folders = itemTypeDAO.GetFoldersbyParent(port, baseFolderType.getFolderId());
            }
            catch (IOException iOException)
            {
                iOException.printStackTrace(System.err);
                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, Main.Txt("Abrufen_der_Unterordner_schlug_fehl:") + " " + baseFolderType.getDisplayName(), iOException);
                return;
            }

            for (Iterator<BaseFolderType> it = folders.iterator(); it.hasNext();)
            {
                BaseFolderType sub_folder = it.next();
                subfolders_folder_list.add(sub_folder);

                get_subfolders( port, itemTypeDAO, sub_folder, subfolders_folder_list );
            }
        }
    }

    private List<BaseFolderType>  get_user_folder_list(List<BaseFolderType> all_folder_list, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO) throws IOException
    {

         ArrayList<BaseFolderType>user_folders = new ArrayList<BaseFolderType>();


         FolderIdType root_folder_id = null;

        //THESE ARE THE SETTINGS FROM CLIENT
        ArrayList<DistinguishedFolderIdNameType>folders = new ArrayList<DistinguishedFolderIdNameType>();
        folders.add(DistinguishedFolderIdNameType.INBOX );
        folders.add(DistinguishedFolderIdNameType.SENTITEMS );
        folders.add(DistinguishedFolderIdNameType.DRAFTS );
        folders.add(DistinguishedFolderIdNameType.OUTBOX );
        folders.add(DistinguishedFolderIdNameType.VOICEMAIL );
        folders.add(DistinguishedFolderIdNameType.DELETEDITEMS );
        folders.add(DistinguishedFolderIdNameType.PUBLICFOLDERSROOT );

        ArrayList<BaseFolderIdType> included_folder_ids = create_folder_id_list( folders );

        List<BaseFolderType> included_folders = itemTypeDAO.GetFoldersbyId( port, included_folder_ids );

        if ( included_folders.size() > 0)
        {
            root_folder_id = included_folders.get(0).getParentFolderId();
        }



        for (int i = 0; i < all_folder_list.size(); i++)
        {
            BaseFolderType bf = all_folder_list.get(i);

            if (bf.getFolderId() == null)
                    continue;
            
            // SKIP NON-ROOT FOLDERS
            if (!bf.getParentFolderId().getId().equals( root_folder_id.getId()))
                continue;

            // SKIP KNOWN FOLDERS
            boolean skip = false;
            for (int j = 0; j < included_folders.size(); j++)
            {
                BaseFolderType ibf = included_folders.get(j);
                if (ibf.getFolderId() == null)
                {
                    skip = true;
                    break;
                }

                if (ibf.getFolderId().getId().equals(bf.getFolderId().getId()))
                {
                    skip = true;
                    break;
                }
            }

            // THIS MUST BE A USER-ROOT-FOLDER!!
            if (!skip)
                user_folders.add(bf);

        }
        

        return user_folders;
    }


    private void run_user_import(  ExchangeImporterUserEntry exie, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO ) throws IOException, ImportException
    {
        ArrayList<BaseFolderIdType> folder_list = new ArrayList<BaseFolderIdType>();

        List<BaseFolderType> all_folder_list = new ArrayList<BaseFolderType>();

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());

        exie.set_status( Txt("Fetching_folder_struct_for_user") + " " + itemTypeDAO.getImpersonation().getConnectingSID().getPrincipalName() + "...");

        // GET ALL FOLDERS ON ROOT LEVEL
        all_folder_list = itemTypeDAO.GetFolders(port);

        List<BaseFolderType> subfolders_folder_list = new ArrayList<BaseFolderType>();


        // GET ALL SUBFOLDERS FOR TESE ROOTFOLDERSD RECURSIVELY
        for (int i = 0; i < all_folder_list.size(); i++)
        {
            BaseFolderType baseFolderType = all_folder_list.get(i);

            get_subfolders( port, itemTypeDAO, baseFolderType, subfolders_folder_list );
        }
        all_folder_list.addAll(subfolders_folder_list);
        


        // IF USER HAS SELECTED FOLDERS, WE HAVE TO MERGE SUBFOLDERS INT HIS LIST
        if ((exie.folder_list != null && !exie.folder_list.isEmpty()) || exie.user_folders)
        {

            ArrayList<BaseFolderIdType> merged_folder_list = new ArrayList<BaseFolderIdType>();

            exie.set_status( Txt("Retrieving_folders"));

            if (exie.folder_list != null && !exie.folder_list.isEmpty())
            {
                ArrayList<BaseFolderIdType> included_folder_ids = create_folder_id_list( exie.folder_list );
                List<BaseFolderType> included_folders = itemTypeDAO.GetFoldersbyId( port, included_folder_ids );

                for (int i = 0; i < included_folders.size(); i++)
                {
                    BaseFolderType folder = included_folders.get(i);

                    // DOES NOT EXIST ON SERVER?
                    if (folder.getFolderId() == null)
                        continue;

                    exie.set_status( Txt("Analyzing_folder") + " " + folder.getDisplayName());

                    // ADD THIS
                    merged_folder_list.add(folder.getFolderId());

                    // AND ALL SUBFOLDERS
                    add_to_merged_list( exie, all_folder_list, merged_folder_list, folder.getFolderId() );
                }
            }


            if (exie.user_folders)
            {
                try
                {
                    List<BaseFolderType> user_folder_list = get_user_folder_list(all_folder_list, port, itemTypeDAO);
                    for (int i = 0; i < user_folder_list.size(); i++)
                    {
                        BaseFolderType bf = user_folder_list.get(i);
                        merged_folder_list.add(bf.getFolderId());

                        // AND ALL SUBFOLDERS
                        add_to_merged_list( exie, all_folder_list, merged_folder_list, bf.getFolderId() );

                    }
                }
                catch (Exception e)
                {
                    exie.set_status( 2, "Error while reading user folders:");
                    e.printStackTrace();
                    LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), e);
                }
            }

            folder_list = merged_folder_list;
        }
        // ELSE WE TAKE ALL FOLDERS AND EXCLUDE TRASH JUNK OUTBOX
        else
        {
            // BUILD IDTYPE LIST
            ArrayList<BaseFolderIdType> exclude_folder_ids = create_exclude_folder_id_list();

            // GET FOLDERS
            List<BaseFolderType> excluded_folders = itemTypeDAO.GetFoldersbyId( port, exclude_folder_ids );

            folder_list = new ArrayList<BaseFolderIdType>();
            //ArrayList<BaseFolderIdType> merged_folder_list = new ArrayList<BaseFolderIdType>();

            for (int i = 0; i < all_folder_list.size(); i++)
            {
                BaseFolderType baseFolderType = all_folder_list.get(i);
                
                boolean found_excl_match = false;
                for (int j = 0; j < excluded_folders.size(); j++)
                {
                    BaseFolderType ex_folder = excluded_folders.get(j);
                    if (ex_folder.getFolderId().getId().equals( baseFolderType.getFolderId().getId() ))
                    {
                        found_excl_match = true;
                        break;
                    }
                }
                
                // OVERRIDE MISSING TRASH ENTRY
                if (baseFolderType.getDisplayName() != null && baseFolderType.getDisplayName().equals("Trash"))
                    found_excl_match = true;

                if (!found_excl_match)
                {
                    folder_list.add(baseFolderType.getFolderId());

                    /*merged_folder_list.add(baseFolderType.getFolderId());
                    // AND ALL SUBFOLDERS
                    add_to_merged_list( exie, all_folder_list, merged_folder_list, baseFolderType.getFolderId() );*/
                }
            }

           //folder_list = merged_folder_list;
        }

        exie.set_status( Txt("Folders_to_fetch:") + " " + folder_list.size());

        if (folder_list.size() > 0)
        {
            // SO WEVE GOT ALL FOLDERS TO IMPORT NOW
            run_folder_import(m_ctx, exie, folder_list, port, itemTypeDAO);
        }

    }
    String Txt( String kex )
    {
        return Main.Txt(kex);
    }

    ArrayList<String> get_mail_list( MandantContext m_ctx, ExchangeImporterFolderEntry exie )
    {
        ArrayList<String> user_mail_list = new ArrayList<String>();

        UserSSOEntry ssoc = m_ctx.get_from_sso_cache( exie.user, exie.pwd );
        if (ssoc == null)
        {
            try
            {
                if (!m_ctx.authenticate_user(exie.user, exie.pwd))
                {
                    LogManager.msg( LogManager.LVL_WARN, LogManager.TYP_EXCHANGE, "Importing unknown user: " + exie.user);
                }
            }
            catch (AuthException authException)
            {
                LogManager.msg( LogManager.LVL_WARN, LogManager.TYP_EXCHANGE, "Importing invalid user: " + exie.user);
            }
        }
        ssoc = m_ctx.get_from_sso_cache(exie.user, exie.pwd);


        // USERLIST CONTAINS LIST OF USERPRINCIPALNAMES
        if (ssoc != null)
        {

            try
            {
                GenericRealmAuth realm = GenericRealmAuth.factory_create_realm(ssoc.getAcct());
                realm.connect();
                
                ArrayList<String> _list = new ArrayList<String>();
                _list.add(exie.user);

                user_mail_list = realm.list_mailaliases_for_userlist( _list );

                realm.disconnect();
            }
            catch (Exception ex)
            {
                exie.set_status( 1, "Error while retrieving user list:" );

                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), ex);
                
            }
        }
        return user_mail_list;
    }

    void run_folder_import( ExchangeImporterFolderEntry exie )
    {
        ItemTypeDAO itemTypeDAO = null;
        ExchangeServicePortType port = null;
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());

        // FETCH EMAILS FOR THIS USER
        ArrayList<String> user_mail_list = get_mail_list( m_ctx, exie );
       
        ExchangeAuthenticator.reduce_ssl_security();
        ArrayList<BaseFolderIdType> folder_list = exie.folder_list;

        exie.set_status( Txt("Connecting_exchange_server..."));
        try
        {

            port = ExchangeAuthenticator.open_exchange_port( exie.user, exie.pwd, exie.domain, exie.server );
            ExchangeEnvironmentSettings settings = new ExchangeEnvironmentSettings( ExchangeEnvironmentSettings.get_cultures()[0], exie.ev );

            itemTypeDAO = new ItemTypeDAO(settings);
        }
        catch (Exception extractionException)
        {
            exie.set_status( 2, "Error while connecting exchange server:");
            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), extractionException);
            return;
        }

        try
        {
            run_folder_import(m_ctx, exie, folder_list, port, itemTypeDAO);
        }
        catch (Exception extractionException)
        {
            exie.set_status( 2, "Error while importing exchange data:");

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_EXCHANGE, exie.get_status(), extractionException);
            return;
        }

    }


    void run_folder_import( MandantContext m_ctx, ExchangeImporterEntry exie, ArrayList<BaseFolderIdType> folder_list, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO) throws ImportException
    {
        long total_size = 0;
        int cnt = 0;
        List<ExchangeMailEntry> full_mail_list = new ArrayList<ExchangeMailEntry>();

        List<BaseFolderType> nice_folder_list = null;

        try
        {
            nice_folder_list = itemTypeDAO.GetFoldersbyId( port, folder_list );

            // CREATE A LIST OF MAILS OVER ALL SELECTED FOLDERS
            for (int i = 0; i < folder_list.size(); i++)
            {
                BaseFolderIdType baseFolderIdType = folder_list.get(i);

                String folder_name =  baseFolderIdType.toString();
                if (nice_folder_list != null && nice_folder_list.size() == folder_list.size())
                   folder_name = nice_folder_list.get(i).getDisplayName();


                exie.set_status( Txt("Reading_mail_data_for_folder") + " <" + folder_name + "> ...");

                List<ItemType> mails = null;
                try
                {
                    mails = itemTypeDAO.getFolderItems(port, baseFolderIdType);
                }
                catch (Exception e)
                {
                    exie.set_status( Txt("Fetching_folder_items_failed") + " <" + folder_name + "> " + e.getMessage());
                    continue;
                }

                int local_cnt = 0;
                long local_size = 0;

                for (Iterator<ItemType> it1 = mails.iterator(); it1.hasNext();)
                {
                    ItemType mail = it1.next();
                    Integer size = mail.getSize();
                    String subject = mail.getSubject();
                    //System.out.println("Mail: " + s  + " Size: " + size);

                    total_size += size.intValue();
                    local_size += size.intValue();
                    cnt++;
                    local_cnt++;

                    exie.size = total_size;
                    exie.total_msg = cnt;

                    ExchangeMailEntry entry = new ExchangeMailEntry( mail.getItemId(), size.intValue(), subject );
                    full_mail_list.add( entry );
                }
                exie.set_status( "<" + folder_name + ">: N=" + local_cnt + " (" + SizeStr.format(local_size) + ")");
            }

            // CHEKC FOR SPACE
            DiskVault dv = m_ctx.get_vault_by_da_id(exie.da.getId());

            // CHECK FOR SPACE
            if (!dv.has_sufficient_space(total_size))
            {
                throw new VaultException( Main.Txt("Cannot_import_mail,_not_enough_space") + ": " + dv.get_name() );
            }
        }
        catch (Exception extractionException)
        {            
            LogManager.printStackTrace(extractionException);
            exie.set_status( 3, "Error while importing exchange data: " + extractionException.getMessage());            
            throw new ImportException( exie.get_status() );
        }
        try
        {
            String user = "";
            if (itemTypeDAO.getImpersonation() != null)
            {
                user = itemTypeDAO.getImpersonation().getConnectingSID().getPrincipalName();
            }
            LogManager.msg_exchange( LogManager.LVL_INFO, Main.Txt("Starting_import") + " " + user + " N=" + exie.total_msg + " (" +  SizeStr.format(exie.size) + ")" );
            long start_t = System.currentTimeMillis();

            
            while (full_mail_list.size() > 0)
            {
                long sum = 0;

                List<ItemIdType> mail_list = new ArrayList<ItemIdType>();
                List<ExchangeMailEntry> exch_mail_list = new ArrayList<ExchangeMailEntry>();

                while( full_mail_list.size() > 0)
                {

                    ExchangeMailEntry me = full_mail_list.get(0);
                    sum += me.size;

                    if (mail_list.size() > 0 && (sum > max_chunk_size || mail_list.size() > max_mailcount_threshold))
                    {
                        break;
                    }

                    // CREATE LIST FOR EXCHANGE IMPORT
                    full_mail_list.remove(me);
                    mail_list.add(me.id);
                    exch_mail_list.add(me);
                }

                exie.set_status( Main.Txt("Fetching_mails_from_Exchangeserver") + "(" + mail_list.size() + "/" + full_mail_list.size() + "/" + exie.total_msg + ")");

                if (!handle_import_chunks( exie, port, itemTypeDAO, mail_list ) && mail_list.size() > 1)
                {
                    LogManager.msg_exchange(LogManager.LVL_ERR, Main.Txt("Fetching_mails_failed,_trying_again"));
                    for (int i = 0; i < mail_list.size(); i++)
                    {
                         ItemIdType itemIdType = mail_list.get(i);
                         List<ItemIdType> single_mail_list = new ArrayList<ItemIdType>();
                         single_mail_list.add(itemIdType);

                         if (handle_import_chunks( exie, port, itemTypeDAO, single_mail_list ))
                         {
                            LogManager.msg_exchange(LogManager.LVL_ERR, Main.Txt("Fetching_mail_failed") + ": " + exch_mail_list.get(i).subject);
                         }
                    }
                }

                if (isShutdown())
                    break;
            }
            
            int imported_msgs = exie.total_msg - full_mail_list.size();

            long end_t = System.currentTimeMillis();
            int speed = 0;
            if (end_t > start_t)
            {
                speed = (int)((1000*imported_msgs) / (end_t - start_t));
            }
            LogManager.msg_exchange( LogManager.LVL_INFO, Main.Txt("Messages_imported") + ": " + imported_msgs + " (" + speed + "/s)" );
        }
        catch (Exception exception)
        {
            LogManager.printStackTrace(exception);
            exie.set_status( 4, "Error while importing exchange message: " + exception.getMessage());
            throw new ImportException( exie.get_status() );
        }
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        return true;
    }


    boolean handle_import_chunks( ExchangeImporterEntry exie, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO, List<ItemIdType> mail_list ) throws ArchiveMsgException, IOException, VaultException, IndexException
    {
 
        // FETCH MESSAGES FROM SERVER
        ArrayOfRealItemsType rfc_mails = null;
        try
        {
            rfc_mails = itemTypeDAO.getItem(port, mail_list);
        }
        catch (Exception e)
        {
            exie.set_status( 4,  Main.Txt("Error_while_fetching_mail_items_from_server") + " " + e.getMessage() );
            LogManager.msg_exchange( LogManager.LVL_ERR, exie.get_status() );
            return false;
        }
        
        exie.set_status(  Main.Txt("Importing_mails_into_archive"));

        for (int j = 0; j < rfc_mails.getItemOrMessageOrCalendarItem().size(); j++)
        {
            ItemType msg_type = rfc_mails.getItemOrMessageOrCalendarItem().get(j);

            String mime_txt = msg_type.getMimeContent().getValue();

            // DECODE TO 8-BIT MIMETEXT
            byte[] data = Base64.decodeBase64(mime_txt.getBytes());
            
            ByteArrayInputStream bis = new ByteArrayInputStream(data);

            RFCMimeMail mime_mail = new RFCMimeMail();
            
            try
            {
                // PARSE MAIL
                mime_mail.parse(bis);
                MimeMessage m = mime_mail.getMsg();

                try
                {
                    if (ImapEnvelopeFetcher.contains_tnef( m))
                    {
                        m = TNEFMime.convert(mime_mail.getSession(), m, /*embed*/ true);
                    }
                }
                catch (Exception iOException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_EXCHANGE, "Cannot decode TNEF content, using Mime", iOException);
                }

                // CREATE FILE
                RFCFileMail mail = Main.get_control().create_import_filemail_from_eml(exie.mandant, m, "exch", exie.da);

                // HANDLE ENVELOPE
                if (exie.act_user_mail.size() > 0)
                {
                    check_for_bcc_attribute( exie, mime_mail, mail );
                }

                // ADD TO IMPORT QUEUE
                Main.get_control().add_rfc_file_mail(mail, exie.mandant, exie.da, /*bg*/ true, /*del_after*/ true);
            }
            catch (Exception messagingException)
            {
                LogManager.printStackTrace(messagingException);
                exie.set_status( 4,  Main.Txt("Cannot_import_exchange_mail_data_for_Mail:") + " " +  msg_type.getSubject() + ": " + messagingException.getMessage() );
                LogManager.msg_exchange(  LogManager.LVL_ERR, exie.get_status() );
            }
            bis.close();



            if (isShutdown())
                break;

        }
        return true;
    }

    void append_result( StringBuilder stb, ExchangeImporterEntry mbie, int i )
    {
        stb.append("EXIMA");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.mandant.getId());
        stb.append(" EXISI");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.size);
        stb.append(" EXIST");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.get_status());
        stb.append(" EXISP");
        stb.append(i);
        stb.append(":");
        stb.append(String.format("%.1f", mbie.mb_per_s) );
        stb.append(" EXIAM");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.act_msg);
        stb.append(" EXITM");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.total_msg);
        stb.append("\n");

    }


   
    @Override
    public String get_task_status()
    {
        StringBuilder stb = new StringBuilder();


        synchronized (import_list)
        {
            int i = 0;
            for (i = 0; i < import_list.size(); i++)
            {
                ExchangeImporterEntry mbie = import_list.get(i);

                append_result( stb, mbie, i );

            }
            synchronized(entry_lock)
            {
                if (active_entry!= null)
                    append_result( stb, active_entry, i++ );
                if (last_error_entry != null)
                    append_result( stb, last_error_entry, i++ );
            }
        }
        return stb.toString();
    }

    @Override
    public String get_task_status( int ma_id )
    {
        StringBuilder stb = new StringBuilder();

        synchronized (import_list)
        {
            int i = 0;
            for (i = 0; i < import_list.size(); i++)
            {
                ExchangeImporterEntry mbie = import_list.get(i);
                if (mbie.mandant.getId() != ma_id)
                    continue;

                append_result( stb, mbie, i );

            }
            synchronized(entry_lock)
            {
                 if (active_entry!= null && active_entry.mandant.getId() == ma_id)
                    append_result( stb, active_entry, i++ );
                if (last_error_entry != null && last_error_entry.mandant.getId() == ma_id)
                    append_result( stb, last_error_entry, i++ );
            }
        }
        return stb.toString();
    }

    private void check_for_bcc_attribute( ExchangeImporterEntry exie, RFCMimeMail msg, RFCFileMail mail  )
    {
        ArrayList<String> user_mails = exie.act_user_mail;
        boolean found_mail = false;

        for (int i = 0; i < user_mails.size(); i++)
        {
            String string = user_mails.get(i);
            if (msg.contains_email( string ))
            {
                found_mail = true;
                break;
            }
        }
        if (!found_mail)
        {
            // ADD FIRST MAIL FROM USER AS ENVELOPE ATTRIBUTE
            LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_EXCHANGE, "Adding Envelope address " + user_mails.get(0) + " to mail");
            mail.add_attribute(RFCGenericMail.MATTR_LUCENE, CS_Constants.FLD_BCC, user_mails.get(0));
       }

        throw new UnsupportedOperationException("Not yet implemented");
    }
}
