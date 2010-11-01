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
import com.microsoft.schemas.exchange.services._2006.types.ExchangeVersionType;
import com.microsoft.schemas.exchange.services._2006.types.ItemIdType;
import com.microsoft.schemas.exchange.services._2006.types.ItemType;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.exchange.ExchangeAuthenticator;
import home.shared.exchange.dao.ItemTypeDAO;
import home.shared.exchange.util.ExchangeEnvironmentSettings;
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
import javax.swing.Timer;
import org.apache.commons.codec.binary.Base64;

abstract class ExchangeImporterEntry
{
    Mandant mandant;
    DiskArchive da;

    int total_msg;
    int act_msg;
    long size;
    float mb_per_s;
    String status;
    int err;

    public ExchangeImporterEntry( Mandant mandant, DiskArchive da )
    {
        this.mandant = mandant;
        this.da = da;
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


    void set_status( String string )
    {
        synchronized(this)
        {
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

}

class ExchangeImporterFolderEntry extends ExchangeImporterEntry
{

    ArrayList<BaseFolderIdType>folder_list;

    String user;
    String domain;
    String server;
    String pwd;

    ExchangeImporterFolderEntry( Mandant m, DiskArchive da, ArrayList<BaseFolderIdType>folder_list,
            String user, String pwd, String domain, String server)
    {
        super( m, da );
        this.folder_list = folder_list;
        this.user = user;
        this.pwd = pwd;
        this.domain = domain;
        this.server = server;
    }

   

}

class ExchangeImporterUserEntry extends ExchangeImporterEntry
{
    ArrayList<BaseFolderIdType>folder_list;
    ArrayList<String>user_list;
    boolean user_folders;
    long ac_id;

    ExchangeImporterUserEntry( Mandant m, DiskArchive da, long ac_id, ArrayList<BaseFolderIdType> folder_list, ArrayList<String> user_list, boolean user_folders )
    {
        super( m, da );
        this.folder_list = folder_list;
        this.user_list = user_list;
        this.user_folders = user_folders;
        this.ac_id = ac_id;
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

    /** Creates a new instance of StatusDisplay */
    public ExchangeImportServer()
    {
        super(NAME);
        import_list = new ArrayList<ExchangeImporterEntry>();
        max_chunk_size = Main.get_prefs().get_long_prop(GeneralPreferences.EXCHANGE_IMPORT_MAX_CHUNK_SIZE);
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    public void add_exchange_import( MandantContext m_ctx, DiskArchive da, ArrayList<BaseFolderIdType>folder_list,
             String user, String pwd, String domain, String server)
    {
        ExchangeImporterFolderEntry mbie = new ExchangeImporterFolderEntry(m_ctx.getMandant(), da, folder_list,
                 user, pwd, domain, server );
        synchronized (import_list)
        {
            import_list.add(mbie);
        }
    }
    private void add_exchange_import( MandantContext m_ctx, DiskArchive da, long ac_id, ArrayList<BaseFolderIdType> folder_list, ArrayList<String> user_list, boolean user_folders )
    {
        ExchangeImporterUserEntry mbie = new ExchangeImporterUserEntry(m_ctx.getMandant(), da, ac_id, folder_list, user_list, user_folders );
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
            run_import(exie);
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


    public static void register_import( MandantContext m_ctx, DiskArchive da, ArrayList<BaseFolderIdType> folder_list,
            String user, String pwd, String domain, String server )
    {
        Main.get_control().get_ex_import_server().add_exchange_import(m_ctx, da, folder_list, user, pwd, domain, server );
    }
    public static void register_import( MandantContext m_ctx, DiskArchive da, long ac_id, ArrayList<BaseFolderIdType> folder_list, ArrayList<String> user_list, boolean user_folders )
    {
        Main.get_control().get_ex_import_server().add_exchange_import(m_ctx, da, ac_id, folder_list, user_list, user_folders );
    }

    void run_import( ExchangeImporterEntry exie )
    {
        if (exie instanceof ExchangeImporterFolderEntry)
        {
            run_folder_import( (ExchangeImporterFolderEntry) exie);
        }
        if (exie instanceof ExchangeImporterUserEntry)
        {
            run_user_import( (ExchangeImporterUserEntry) exie);
        }
    }
    void run_user_import( ExchangeImporterUserEntry exie )
    {
    }

    void run_folder_import( ExchangeImporterFolderEntry exie )
    {
        ArrayList<BaseFolderIdType> folder_list = exie.folder_list;
        ExchangeAuthenticator.reduce_ssl_security();
        ItemTypeDAO itemTypeDAO = null;
        ExchangeServicePortType port = null;
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(exie.mandant.getId());

        List<ExchangeMailEntry> full_mail_list = new ArrayList<ExchangeMailEntry>();
        long total_size = 0;

        try
        {

            port = ExchangeAuthenticator.open_exchange_port( exie.user, exie.pwd, exie.domain, exie.server );
            ExchangeEnvironmentSettings settings = new ExchangeEnvironmentSettings( ExchangeEnvironmentSettings.get_cultures()[0], ExchangeVersionType.EXCHANGE_2007_SP_1 );

            itemTypeDAO = new ItemTypeDAO(settings);


            // CREATE A LIST OF MAILS OVER ALL SELECTED FOLDERS
            for (int i = 0; i < folder_list.size(); i++)
            {
                BaseFolderIdType baseFolderIdType = folder_list.get(i);

                List<ItemType> mails = itemTypeDAO.getFolderItems( port, baseFolderIdType );

                for (Iterator<ItemType> it1 = mails.iterator(); it1.hasNext();)
                {
                    ItemType mail = it1.next();
                    Integer size = mail.getSize();
                    String s = mail.getSubject();
                    System.out.println("Mail: " + s  + " Size: " + size);
                    total_size += size.intValue();

                    List<ItemIdType> mail_list = new ArrayList<ItemIdType>();
                    mail_list.add(mail.getItemId());

                    ExchangeMailEntry entry = new ExchangeMailEntry( mail.getItemId(), size.intValue() );
                    full_mail_list.add( entry );
                }
            }

                    //ArrayOfRealItemsType rfc_mails = itemTypeDAO.getItem(port, mail_list);
                    //System.out.println(rfc_mails.getItemOrMessageOrCalendarItem().size());

            exie.size = total_size;
            exie.total_msg = full_mail_list.size();

            DiskVault dv = m_ctx.get_vault_by_da_id(exie.da.getId());

            // CHECK FOR SPACE
            if (!dv.has_sufficient_space(total_size))
            {
                throw new VaultException( Main.Txt("Cannot_import_mail,_not_enough_space") + ": " + dv.get_name() );
            }
        }
        catch (Exception extractionException)
        {
            exie.status = "Error while importing exchange data: " + extractionException.getMessage();
            exie.err = 2;

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.status, extractionException);
            return;
        }
        try
        {
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Starting_import") + " N=" + exie.total_msg + " (" + Long.toString(exie.size/(1000*1000)) + "MB)" );
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
            exie.status = "Error while importing exchange message: " + exception.getMessage();
            exie.err = 3;

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, exie.status, exception);
            return;
        }
        finally
        {

        }
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        return true;
    }



    void handle_import_chunks( ExchangeImporterFolderEntry exie, ExchangeServicePortType port, ItemTypeDAO itemTypeDAO, List<ExchangeMailEntry> full_mail_list, int n ) throws ArchiveMsgException, IOException, VaultException, IndexException
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

   
    @Override
    public String get_task_status()
    {
        StringBuilder stb = new StringBuilder();


        synchronized (import_list)
        {
            for (int i = 0; i < import_list.size(); i++)
            {
                ExchangeImporterEntry mbie = import_list.get(i);

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
        }
        return stb.toString();
    }

}
