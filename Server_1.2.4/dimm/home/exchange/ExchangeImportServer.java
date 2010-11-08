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
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.ImportException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.SQL.UserSSOEntry;
import home.shared.exchange.ExchangeAuthenticator;
import home.shared.exchange.dao.ItemTypeDAO;
import home.shared.exchange.util.ExchangeEnvironmentSettings;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCMimeMail;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.mail.MessagingException;
import javax.naming.NamingException;
import javax.swing.Timer;
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

    public ExchangeMailEntry( ItemIdType id, int size )
    {
        this.id = id;
        this.size = size;
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
        max_chunk_size = Main.get_prefs().get_long_prop(GeneralPreferences.EXCHANGE_IMPORT_MAX_CHUNK_SIZE);
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

        this.setStatusTxt("Running");
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
            
            setStatusTxt("");
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
                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Work_jobs got exception", e);
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

        // USERLIST CONTAINS LIST OF USERPRINCIPALNAMES
        if (user_list == null || user_list.isEmpty())
        {
            try
            {
                GenericRealmAuth realm = GenericRealmAuth.factory_create_realm(ac);
                realm.connect();
                user_list = realm.list_users_for_group("");
                realm.disconnect();
            }
            catch (NamingException ex)
            {
                exie.set_status( 1, "Error while retrieving user list:" );

                LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), ex);
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

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), e);
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
                    run_user_import( exie, port, itemTypeDAO );
                }
                catch (Exception e)
                {
                    LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), e);
                }
            }
        }
        catch (Exception e)
        {
            exie.set_status( 2, "Error while opening reteiving exchange mail data:");

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), e);
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

    private void run_user_import( ExchangeImporterUserEntry exie, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO ) throws IOException, ImportException
    {
        ArrayList<BaseFolderIdType> folder_list = new ArrayList<BaseFolderIdType>();

        List<BaseFolderType> all_folder_list = new ArrayList<BaseFolderType>();
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());

        exie.set_status( Txt("Fetching_folder_struct_for_user") + " " + itemTypeDAO.getImpersonation().getConnectingSID().getPrincipalName() + "...");

        all_folder_list = itemTypeDAO.GetFolders(port);

        // IF USER HAS SELECTED FOLDERS, WE HAVE TO MERGE SUBFOLDERS INT HIS LIST
        if (exie.folder_list != null && !exie.folder_list.isEmpty())
        {


            ArrayList<BaseFolderIdType> included_folder_ids = create_folder_id_list( exie.folder_list );

            ArrayList<BaseFolderIdType> merged_folder_list = new ArrayList<BaseFolderIdType>();

            exie.set_status( Txt("Retreiving_folders"));

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
                }
            }
        }

        exie.set_status( Txt("Folders_to_fetch:") + " " + folder_list.size());


        // SO WEVE GOT ALL FOLDERS TO IMPORT NOW
        run_folder_import(m_ctx, exie, folder_list, port, itemTypeDAO);

    }
    String Txt( String kex )
    {
        return Main.Txt(kex);
    }

    void run_folder_import( ExchangeImporterFolderEntry exie )
    {
        ItemTypeDAO itemTypeDAO = null;
        ExchangeServicePortType port = null;
        
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());
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
            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), extractionException);
            return;
        }

        try
        {
            run_folder_import(m_ctx, exie, folder_list, port, itemTypeDAO);
        }
        catch (Exception extractionException)
        {
            exie.set_status( 2, "Error while importing exchange data:");

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.get_status(), extractionException);
            return;
        }

    }


    void run_folder_import( MandantContext m_ctx, ExchangeImporterEntry exie, ArrayList<BaseFolderIdType> folder_list, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO) throws ImportException
    {
        long total_size = 0;
        int cnt = 0;
        List<ExchangeMailEntry> full_mail_list = new ArrayList<ExchangeMailEntry>();

        try
        {
            // CREATE A LIST OF MAILS OVER ALL SELECTED FOLDERS
            for (int i = 0; i < folder_list.size(); i++)
            {
                BaseFolderIdType baseFolderIdType = folder_list.get(i);

                exie.set_status( Txt("Reading_mail_data") + "_<" + baseFolderIdType.toString() + "> ...");

                List<ItemType> mails = itemTypeDAO.getFolderItems( port, baseFolderIdType );

                for (Iterator<ItemType> it1 = mails.iterator(); it1.hasNext();)
                {
                    ItemType mail = it1.next();
                    Integer size = mail.getSize();
                    String s = mail.getSubject();
                    //System.out.println("Mail: " + s  + " Size: " + size);

                    total_size += size.intValue();
                    cnt++;

                    exie.size = total_size;
                    exie.total_msg = cnt;

                    List<ItemIdType> mail_list = new ArrayList<ItemIdType>();
                    mail_list.add(mail.getItemId());

                    ExchangeMailEntry entry = new ExchangeMailEntry( mail.getItemId(), size.intValue() );
                    full_mail_list.add( entry );
                }
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
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Starting_import") + " " + user + " N=" + exie.total_msg + " (" + Long.toString(exie.size/(1000*1000)) + "MB)" );
            long start_t = System.currentTimeMillis();

            while (full_mail_list.size() > 0)
            {
                long sum = 0;
                int i;
                for (i = 0; i < full_mail_list.size(); i++)
                {

                    ExchangeMailEntry me = full_mail_list.get(i);
                    sum += me.size;

                    if (i > 0 && (sum > max_chunk_size || i > max_mailcount_threshold))
                    {
                        break;
                    }
                }

                handle_import_chunks( exie, port, itemTypeDAO, full_mail_list, i );

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
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Messages_imported") + ": " + imported_msgs + " (" + speed + "/s)" );
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



    void handle_import_chunks( ExchangeImporterEntry exie, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO, List<ExchangeMailEntry> full_mail_list, int n ) throws ArchiveMsgException, IOException, VaultException, IndexException
    {

        // CREATE LIST FOR EXCHANGE IMPORT
        List<ItemIdType> mail_list = new ArrayList<ItemIdType>();

        for (int i = 0; i < n; i++)
        {
            mail_list.add(full_mail_list.get(i).id);
        }


        exie.set_status(  Main.Txt("Fetching_mails_from_Exchangeserver") + "(" + n + "/" + full_mail_list.size() + ")");


        // FETCH MESSAGES FROM SERVER
        ArrayOfRealItemsType rfc_mails = itemTypeDAO.getItem(port, mail_list);

        
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

                // CREATE FILE
                RFCFileMail mail = Main.get_control().create_import_filemail_from_eml(exie.mandant, mime_mail.getMsg(), "exch", exie.da);

                // ADD TO IMPORT QUEUE
                Main.get_control().add_rfc_file_mail(mail, exie.mandant, exie.da, /*bg*/ true, /*del_after*/ true);
            }
            catch (MessagingException messagingException)
            {
                LogManager.msg_exchange( LogManager.LVL_ERR, "Cannot import exchange mail data for Mail:" + msg_type.getSubject() );
            }
            bis.close();


            // REMOVE FROM GLOBAL LIST
            full_mail_list.remove(0);

            if (isShutdown())
                break;

        }        
    }

    void append_result( StringBuilder stb, ExchangeImporterEntry mbie, int i )
    {
        stb.append("EXIMA");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.mandant.getId());
        stb.append("EXISI");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.size);
        stb.append(" EXIST");
        stb.append(i);
        stb.append(":");
        stb.append(mbie.get_status());
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
                append_result( stb, active_entry, i );
                append_result( stb, last_error_entry, i );
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
                if (active_entry.mandant.getId() == ma_id)
                    append_result( stb, active_entry, i );
                if (last_error_entry.mandant.getId() == ma_id)
                append_result( stb, last_error_entry, i );
            }
        }
        return stb.toString();
    }
}
