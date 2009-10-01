/*
 * LogicControl.java
 *
 * Created on 5. Oktober 2007, 18:42
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package dimm.home.mailarchiv;

import dimm.home.hibernate.HibernateUtil;
import dimm.home.importmail.DBXImporter;
import dimm.home.importmail.MBoxImporter;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.index.IndexManager;
import dimm.home.index.SearchCall;
import home.shared.mail.RFCFileMail;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.ImapFetcher;
import home.shared.hibernate.Mandant;
import home.shared.hibernate.Milter;
import home.shared.hibernate.Proxy;
import dimm.home.mailarchiv.Exceptions.VaultException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import dimm.home.mailarchiv.Commands.Ping;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.LicenseChecker;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import dimm.home.workers.HotfolderServer;
import dimm.home.workers.IMAPBrowserServer;
import dimm.home.workers.MBoxImportServer;
import dimm.home.workers.MailBoxFetcherServer;
import dimm.home.workers.MailProxyServer;
import dimm.home.workers.MilterServer;
import dimm.home.workers.SQLWorker;
import home.shared.CS_Constants;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.MessagingException;
import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.SessionFactory;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.SingleTableEntityPersister;

class MailBGEntry
{
    RFCFileMail mail;
    Mandant mandant;
    DiskArchive da;
    LogicControl control;
    boolean delete_afterwards;

    MailBGEntry( RFCFileMail mail, Mandant mandant, DiskArchive da, LogicControl control, boolean delete_afterwards )
    {
        this.mail = mail;
        this.mandant = mandant;
        this.da = da;
        this.control = control;
        this.delete_afterwards = delete_afterwards;
    }

    void do_archive()
    {
        try
        {
            control.add_mail_file(mail, mandant, da);
        }
        catch (ArchiveMsgException ex)
        {
            LogManager.err_log("Cannot archive mail", ex);
        }
        catch (VaultException ex)
        {
            if (mail != null)
            {
                try
                {
                    Main.get_control().move_to_quarantine(mail, mandant);
                }
                catch (IOException iOException)
                {
                    LogManager.err_log("Cannot move mail to quarantine", iOException);
                }
            }
        }
        catch (IndexException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            if (delete_afterwards)
            {
                mail.delete();
            }
        }
    }
}
/**
 *
 * @author Administrator
 */


public class LogicControl
{

    Communicator comm;
    MilterServer ms;
    MailProxyServer ps;
    HotfolderServer hf_server;
    MailBoxFetcherServer mb_fetcher_server;
    SQLWorker sql;
    IMAPBrowserServer ibs;

    ArrayList<WorkerParent> worker_list;
    ArrayList<MandantContext> mandanten_list;
    final ArrayList<MailBGEntry> mail_work_list;

    LicenseChecker lic_checker;
    
    MBoxImportServer mb_import_server;
    private boolean shutdown;



    void check_db_changes()
    {
        org.hibernate.classic.Session change_session = HibernateUtil.getSessionFactory().getCurrentSession();
        org.hibernate.Transaction tx = change_session.beginTransaction();

 /*       check_db_changes( change_session, "select max(id) from role_option", true,
                "create table role_option ( id INT NOT NULL GENERATED ALWAYS AS IDENTITY, ro_id int not null, token varchar(80) not null, flags int)",null );
*/

 /*       check_db_changes( change_session, "select token from role_option", false,
                "alter table role_option drop token",null );
        check_db_changes( change_session, "select token from role_option", true,
                "alter table role_option add token varchar(80)",null );
*/

        check_db_changes( change_session, "select max(imap_port) from mandant", true, "alter table mandant add imap_port int", "update mandant set imap_port=0" );
        check_db_changes( change_session, "select max(mid) from mail_header_variable", true, "alter table mail_header_variable add mid int", null );

//        check_db_changes( change_session, "select max(ac_id) from role", false, "alter table role drop ac_id", "delete from account_connector" );

//        check_db_changes( change_session, "select max(ac_id) from role", true, "alter table role add ac_id int", "delete from role" );

/*        check_db_changes( change_session, "select count(username) from account_connector", false,"alter table account_connector drop username", null );
        check_db_changes( change_session, "select count(pwd) from account_connector", false,"alter table account_connector drop pwd", null );

        check_db_changes( change_session, "select count(username) from account_connector", true,
                            "alter table account_connector add username varchar(80)",
                            "update account_connector set username =''");
        check_db_changes( change_session, "select count(pwd) from account_connector", true,
                            "alter table account_connector  add pwd varchar(80)",
                            "update account_connector set  pwd=''");
        check_db_changes( change_session, "select count(flags) from account_connector", true,
                            "alter table account_connector  add flags int",
                            "update account_connector set  flags=0");
*/
        tx.commit();
    }







    /** Creates a new instance of LogicControl */
    public LogicControl()
    {
        lic_checker = new LicenseChecker( Main.APPNAME + ".license",  Main.license_interface, Main.create_licensefile );

        mandanten_list = new ArrayList<MandantContext>();
        worker_list = new ArrayList<WorkerParent>();

        mail_work_list = new ArrayList<MailBGEntry>();
        try
        {

            comm = new Communicator();
            worker_list.add(comm);



            ms = new MilterServer();
            worker_list.add(ms);

            ps = new MailProxyServer();
            worker_list.add(ps);

            hf_server = new HotfolderServer();
            worker_list.add(hf_server);

            mb_fetcher_server = new MailBoxFetcherServer();
            worker_list.add(mb_fetcher_server);

            mb_import_server = new MBoxImportServer();
            worker_list.add(mb_import_server);

            sql = new SQLWorker();
            worker_list.add(sql);

            ibs = new IMAPBrowserServer();
            worker_list.add(ibs);


        }
        catch (Exception ex)
        {
            LogManager.err_log_fatal("Constructor failed", ex);
        }
    }

    public MBoxImportServer get_mb_import_server()
    {
        return mb_import_server;
    }


    public void add_mandant( MandantPreferences prefs, Mandant m )
    {
        mandanten_list.add(new MandantContext(prefs, m));
    }

    public MandantContext get_m_context( Mandant m )
    {
        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext mandantContext = mandanten_list.get(i);
            if (mandantContext.getMandant() == m)
            {
                return mandantContext;
            }
        }
        return null;
    }

    public MandantContext get_mandant_by_id(long id)
    {
        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext mandantContext = mandanten_list.get(i);
            if (mandantContext.getMandant().getId() == id)
            {
                return mandantContext;
            }
        }
        return null;
    }

    public boolean is_licensed()
    {
        return lic_checker.is_licensed();
    }
    public boolean check_licensed()
    {
        return lic_checker.check_licensed();
    }

    public void add_mail_file( File mail, Mandant mandant, DiskArchive da, boolean background, boolean delete_afterwards ) throws ArchiveMsgException, VaultException, IndexException
    {
        // TODO: background
        if (mail == null)
        {
            throw new ArchiveMsgException(Main.Txt("Mail_input_file_is_null"));
        }
        if (!mail.exists())
        {
            throw new ArchiveMsgException(Main.Txt("Mail_input_file_is_missing"));
        }
        RFCFileMail mf = new RFCFileMail( mail, false );

        add_mail_file(mf, mandant, da, background, delete_afterwards);
    }

    void add_mail_file( RFCFileMail mf, Mandant mandant, DiskArchive da)  throws ArchiveMsgException, VaultException, IndexException
    {
        MandantContext context = get_m_context(mandant);
        if (context == null)
        {
            throw new ArchiveMsgException(Main.Txt("Invalid_context_for_mail"));
        }

        ArrayList<Vault> vault_list = context.getVaultArray();

        for (int i = 0; i < vault_list.size(); i++)
        {
            Vault vault = vault_list.get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault)vault;
                if (dv.get_da() == da)
                {
                    vault.archive_mail(mf, context, da);
                }
            }
            else
            {
                vault.archive_mail(mf, context, da);
            }
        }
    }
    public void add_mail_file( final RFCFileMail mf, final Mandant mandant, final DiskArchive da, boolean background, final boolean delete_afterwards ) throws ArchiveMsgException, VaultException, IndexException
    {
         LogManager.log(Level.SEVERE, "No parallel procress");
        background = false;

        if (background)
        {
            MailBGEntry mbge = new MailBGEntry(mf, mandant, da, this, delete_afterwards);
            synchronized( mail_work_list )
            {
                mail_work_list.add(mbge);
            }
        }
        else
        {
            add_mail_file(mf, mandant, da);

            if (delete_afterwards)
                mf.delete();
        }
    }
/*
    public void add_new_mail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
        if (background)
        {
            try
            {
                FileInputStream rfc_is;
                rfc_is = new FileInputStream(rfc_dump);
                File mail_file = create_dupl_temp_file(mandant, rfc_is);

                add_mail_file(mail_file, mandant, da, true);
            }
            catch (FileNotFoundException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
                throw new ArchiveMsgException(Main.Txt("Mail_file_disappeared") + ": " + ex.getMessage());
            }
        }
        else
        {
            add_mail_file(rfc_dump, mandant, da, false);
        }

   
    }
 * */
/*
    public void add_new_mail_stream( InputStream rfc_is, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
            File mail_file = create_dupl_temp_file(mandant, rfc_is);
            add_mail_file(mail_file, mandant, da, background);
    }
 * */
/*
    public void add_new_outmail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
        add_new_mail(rfc_dump, mandant, da, background);
    }

    public void add_new_inmail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
        add_new_mail(rfc_dump, mandant, da, background);
    }
    */
/*
    public void add_new_outmail( MimeMessage msg, Mandant mandant, DiskArchive diskArchive, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
        try
        {
            MandantContext m_ctx = get_m_context(mandant);
            FileOutputStream fos = new FileOutputStream( m_ctx.getTempFileHandler().create_new_import_file(null))
            msg.writeTo(null);
            add_new_mail_stream( is, mandant, diskArchive, background);
            is.close();
        }
        catch (IOException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Message Inputstream exception: " + ex.getMessage());
        }
        catch (MessagingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Messaging exception: " + ex.getMessage());
        }
    }
*/
    /*
    public void add_new_outmail( RFCMailStream mail, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException, IndexException
    {
        try
        {
            InputStream is = mail.getInputStream();
            add_new_mail_stream(is, mandant, da, background);
            is.close();
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
*/
    public DiskSpaceHandler get_mail_dsh( Mandant mandant, String mail_uuid ) throws ArchiveMsgException, VaultException
    {
        long ds_id = DiskSpaceHandler.get_ds_id_from_uuid(mail_uuid);
        long da_id = DiskSpaceHandler.get_da_id_from_uuid(mail_uuid);
        long time = DiskSpaceHandler.get_time_from_uuid(mail_uuid);


        MandantContext context = get_m_context(mandant);

        DiskSpaceHandler found_ds = null;
        ArrayList<Vault> vault_list = context.getVaultArray();

        for (int i = 0; i < vault_list.size(); i++)
        {
            Vault vault = vault_list.get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault) vault;
                DiskArchive da = dv.get_da();
                if (da.getId() != da_id)
                    continue;

                found_ds = dv.get_dsh(ds_id);
                break;
            }
        }
        return found_ds;
    }


    public File get_mail_file( Mandant mandant, String mail_uuid ) throws ArchiveMsgException, VaultException
    {
        long time = DiskSpaceHandler.get_time_from_uuid(mail_uuid);

        DiskSpaceHandler found_ds = get_mail_dsh( mandant, mail_uuid );

        if (found_ds == null)
            throw new VaultException( "Cannot find ds for get mail file " + mail_uuid );

        RFCFileMail msg = new RFCFileMail( null, new Date(time), false);
        File mail_file = msg.create_unique_mailfile( found_ds.getDs().getPath() );

        return mail_file;
    }


    public void delete_mail_file( Mandant mandant, String mail_uuid ) throws ArchiveMsgException, IOException, VaultException
    {
        File mail_file = get_mail_file( mandant, mail_uuid );

        if (mail_file.exists())
            mail_file.delete();
    }


    public File create_temp_file( Mandant mandant ) throws ArchiveMsgException
    {
        File tmp_file = get_m_context(mandant).getTempFileHandler().create_temp_file(/*SUBDIR*/"", "dump", "tmp");

        return tmp_file;
    }
    public RFCFileMail dump_msg_to_temp_file( Mandant mandant, Message msg, String subdir, String prefix, String suffix, boolean encoded ) throws ArchiveMsgException
    {
        OutputStream bos = null;
        RFCFileMail fm = null;

        File tmp_file = get_m_context(mandant).getTempFileHandler().create_temp_file(subdir, prefix, suffix);


        try
        {
            fm = new RFCFileMail(tmp_file, encoded );
            bos = fm.open_outputstream();
            msg.writeTo(bos);
        }
        catch (MessagingException ex)
        {
            LogManager.log(Level.SEVERE, "Cannot extract message file", ex);
            throw new ArchiveMsgException("Cannot extract message file: " + ex.getMessage());
        }
        catch (IOException ex)
        {
            LogManager.log(Level.SEVERE, "Cannot create duplicate temp file", ex);
            throw new ArchiveMsgException("Cannot create duplicate temp file: " + ex.getMessage());
        }
        finally
        {
            try
            {
                bos.close();
            }
            catch (IOException ex)
            {
            }
        }
        return fm;
    }

    public RFCFileMail dump_msg_stream_to_temp_file( Mandant mandant, InputStream is, String subdir, String prefix, String suffix, boolean encoded ) throws ArchiveMsgException
    {
        RFCFileMail fm = null;
        OutputStream bos = null;
        byte[] buff = new byte[CS_Constants.STREAM_BUFFER_LEN];

        File tmp_file = get_m_context(mandant).getTempFileHandler().create_temp_file(subdir, prefix, suffix);
        

        try
        {
            BufferedInputStream bis = new BufferedInputStream(is);
            fm = new RFCFileMail(tmp_file, encoded );
            bos = fm.open_outputstream();

            while (true)
            {
                int rlen = bis.read(buff);
                if (rlen == -1)
                {
                    break;
                }

                bos.write(buff, 0, rlen);                
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Cannot create duplicate temp file: " + ex.getMessage());
        }
        finally
        {
            try
            {
                bos.close();
            }
            catch (IOException ex)
            {
            }
        }
        return fm;
    }

    public RFCFileMail create_filemail_from_msg( Mandant mandant, Message msg, String subdir, String prefix, String suffix ) throws ArchiveMsgException
    {
        RFCFileMail f = dump_msg_to_temp_file(mandant, msg, subdir, prefix, suffix, RFCFileMail.dflt_encoded );

        return f;
    }

    public RFCFileMail create_filemail_from_msg_stream( Mandant mandant, InputStream is, String subdir, String prefix, String suffix ) throws ArchiveMsgException
    {
        RFCFileMail f = dump_msg_stream_to_temp_file(mandant, is, subdir, prefix, suffix, RFCFileMail.dflt_encoded );

        return f;
    }

    public RFCFileMail create_import_filemail_from_eml_stream( Mandant mandant, InputStream is, String prefix) throws ArchiveMsgException
    {
        return create_filemail_from_msg_stream(mandant, is, "import", prefix, "eml");
    }
    public RFCFileMail create_import_filemail_from_eml( Mandant mandant, Message msg, String prefix) throws ArchiveMsgException
    {
        return create_filemail_from_msg(mandant, msg, "import", prefix, "eml");
    }



    void initialize_mandant( MandantContext ctx )
    {
        // ATTACH COMM
        TCPCallConnect tcp_conn = new TCPCallConnect(ctx);
        worker_list.add(tcp_conn);
        ctx.set_tcp_conn( tcp_conn );

        // ATTACH INDEXMANAGER
        IndexManager idx_util = new IndexManager(ctx, /*MailHeadervariable*/null, /*index_attachments*/ true);
        ctx.set_index_manager( idx_util );

        idx_util.initialize();
        worker_list.add(idx_util);


        // BUILD VAULT LIST FROM DISKARRAYS
        ctx.build_vault_list();


        Set<Milter> milters = ctx.getMandant().getMilters();
        Iterator<Milter> milter_it = milters.iterator();

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

        Set<Proxy> proxies = ctx.getMandant().getProxies();
        Iterator<Proxy> proxy_it = proxies.iterator();

        while (proxy_it.hasNext())
        {
             ps.add_proxy(proxy_it.next());
        }

        Set<Hotfolder> hfs = ctx.getMandant().getHotfolders();
        Iterator<Hotfolder> hf_it = hfs.iterator();

        while (hf_it.hasNext())
        {
             hf_server.add_hfolder(hf_it.next());
        }

        Set<ImapFetcher> ifs = ctx.getMandant().getImapFetchers();
        Iterator<ImapFetcher> if_it = ifs.iterator();

        while (if_it.hasNext())
        {
             mb_fetcher_server.add_fetcher(if_it.next());
        }

        if ( ctx.getMandant().getImap_port() > 0)
        {
            try
            {
                ibs.add_browser(ctx, null, ctx.getMandant().getImap_port());
            }
            catch (IOException ex)
            {
                LogManager.err_log_fatal(Main.Txt("Cannot_start_IMAP_server_for") + " " + ctx.getMandant().getName(), ex);
            }
        }

    }

    void initialize()
    {
       // READ PARAM DB
        read_param_db();

        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext ctx = mandanten_list.get(i);

            initialize_mandant( ctx );
        }



        // WAIT UNTIL WE REACH INET BEFORE CONTINUING
        if (comm != null)
        {
            comm.setStatusTxt(Main.Txt("Checking_internet"));
            for (int i = 0; i < 3; i++)
            {
                if (do_server_ping())
                {
                    break;
                }
                sleep(500);
            }

            if (!do_server_ping())
            {
                comm.setStatusTxt(Main.Txt("Internet_not_reachable"));
                comm.setGoodState(false);
                LogManager.err_log_fatal(Main.Txt("Cannot_connect_internet_at_startup"));

            }
            else
            {
                if (!comm.isGoodState())
                {
                    comm.setStatusTxt(Main.Txt("Internet_reachable"));
                    comm.setGoodState(true);
                }
            }
        }

        lic_checker.check_licensed();

        for (int i = 0; i < worker_list.size(); i++)
        {
            try
            {
                boolean ok = worker_list.get(i).initialize();
                if (!ok)
                {
                    LogManager.err_log_fatal("Initialize of " + worker_list.get(i).getName() + " failed");
                }
            }
            catch (Exception ex)
            {
                // SHOULD NEVER BE RECHED
                LogManager.err_log_fatal("Initialize of " + worker_list.get(i).getName() + " failed : " + ex.getMessage());
            }
        }
     }

    public static void sleep( int ms )
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }

    boolean set_system_time()
    {
        try
        {
            // USE  --directisa WG: SHUTTLE, SHOULD NOT BOTHER OTHER BOX
            String[] ntp_server_list =
            {
                "ptbtime2.ptb.de"
            };

            for (int idx = 0; idx < ntp_server_list.length; idx++)
            {
                String rdate_cmd = Main.prefs.get_prop(GeneralPreferences.RDATE_COMMAND, "ntpdate " + ntp_server_list[idx] + " && hwclock --directisa -w");
                String[] cmd =
                {
                    rdate_cmd
                };
                CmdExecutor exec = new CmdExecutor(cmd);

                if (exec.exec() != 0)
                {
                    String err_txt = "";
                    if (exec.get_out_text() != null && exec.get_out_text().length() > 0)
                    {
                        err_txt += exec.get_out_text();
                    }
                    if (exec.get_err_text() != null && exec.get_err_text().length() > 0)
                    {
                        err_txt += exec.get_err_text();
                    }

                    LogManager.err_log_warn(Main.Txt("System_time_cannot_be_retrieved") + ": " + err_txt);

                    sleep(1000);
                }
                else
                {
                    LogManager.debug_msg(1, Main.Txt("Systemtime_was_synchronized"));
                    return true;
                }
            }
        }
        catch (Exception exc)
        {
        }
        return false;
    }

    boolean do_server_ping()
    {
        boolean ok = false;
        try
        {
            Ping ping = new Ping();

            ping.do_command("");

            if (ping.get_answer().compareTo("INET_OK") == 0)
            {
                ok = true;
            }
        }
        catch (Exception exc)
        {
        }

        if (comm != null)
        {

            if (!ok)
            {
                comm.setStatusTxt(Main.Txt("Internet_not_reachable"));
            }
            comm.setGoodState(ok);
        }

        return ok;
    }

    void work_mail_bg_jobs()
    {
        while (true)
        {
            MailBGEntry mbge = null;
            synchronized (mail_work_list)
            {
                if (mail_work_list.size() > 0)
                {
                    mbge = mail_work_list.remove(0);
                }
            }

            if (mbge == null)
            {
                break;
            }

            // NOT LOCKED, OTHERS CAN ADD ENTRIES TO LIST
            mbge.do_archive();
        }
    }


    // MAIN WORK LOOP
    void run()
    {
        long last_date_set = 0;
        long last_ping = 0;
        boolean last_start_written = false;
        long started = System.currentTimeMillis();

        final int MIN_VALID_RUN_TIME = 10000;  // AFTER THIS TIME WE WRITE OUR VALID TIMESTAMP

        try
        {


            // SET SYSTEM TIME PRIOR STARTING PLAYLISTS
            set_system_time();
            last_date_set = System.currentTimeMillis();


            for (int i = 0; i < worker_list.size(); i++)
            {
                if (!worker_list.get(i).start_run_loop())
                {
                    LogManager.err_log_fatal(Main.Txt("Cannot_start_runloop_for_Worker") + " " + worker_list.get(i).getName());
                }
            }


            while (!shutdown)
            {
                sleep(1000);

                long now = System.currentTimeMillis();

                // ALLE 24h UHRZEIT SETZEN
                if ((now - last_date_set) > 24 * 60 * 60 * 1000)
                {
                    set_system_time();
                    last_date_set = now;
                }



                // ALLE 10 SEKUNDEN INET PINGENSETZEN
                if ((now - last_ping) > 10 * 1000)
                {
                    do_server_ping();
                    last_ping = now;
                }

                if (!last_start_written)
                {
                    if ((now - started) > MIN_VALID_RUN_TIME)
                    {
                        try
                        {
                            File f = new File(Main.STARTED_OK);

                            FileWriter fw = new FileWriter(f);
                            fw.write("OK");
                            fw.close();
                        }
                        catch (Exception exc)
                        {
                        }
                    }
                }

                // DO BACKGROUND ARCHIVE JOBS
                work_mail_bg_jobs();
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }


        LogManager.info_msg("Closing down " + Main.APPNAME);
        //System.exit(0);
    }

    public void set_shutdown( boolean b )
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            worker_list.get(i).setShutdown(b);
        }
        shutdown = b;
    }
/*
    static public Date get_actual_rel_date()
    {
        Date now = new Date(System.currentTimeMillis());

        SimpleDateFormat full_date_sdf = new SimpleDateFormat("HH:mm:ss");
        String sd = full_date_sdf.format(now);
        try
        {
            now = full_date_sdf.parse(sd);
        }
        catch (Exception ex)
        {
            System.out.println("Cannot retreive get_actual_rel_date " + ex.getMessage());
            return null;
        }

        return now;
    }*/

    public SQLWorker get_sql_worker()
    {
        return sql;
    }

    WorkerParent get_worker( String name )
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            if (worker_list.get(i).getName().compareTo(name) == 0)
            {
                return worker_list.get(i);
            }
        }
        return null;
    }

    public ArrayList<WorkerParent> get_worker_list()
    {
        return worker_list;
    }

    public boolean check_requirements( StringBuffer sb )
    {
        boolean ok = true;
        sb.append("LogicControl check :\n");


        for (int i = 0; i < worker_list.size(); i++)
        {
            sb.append(worker_list.get(i).getName() + " check: ");
            if (!worker_list.get(i).check_requirements(sb))
            {
                ok = false;
            }
            sb.append("\n");
        }

        return ok;
    }

    private MandantPreferences read_mandant_prefs( Mandant m )
    {
        String prefs_path = Main.PREFS_PATH + m.getId() + "/";
        File d = new File( prefs_path );
        if (!d.exists())
            d.mkdirs();
        
        MandantPreferences prefs = new MandantPreferences(prefs_path);
        return prefs;
    }

    org.hibernate.classic.Session param_session;
    org.hibernate.Query read_param_db_qry;

    boolean check_db_changes(org.hibernate.classic.Session change_session, String check_qry, boolean on_fail, String alter_cmd, String fill_cmd)
    {

        boolean failed = false;

        try
        {
            SQLQuery sql_res = change_session.createSQLQuery(check_qry);
            List l = sql_res.list();
            if (l.size() != 1)
                throw new Exception( "Missing field" );
        }
        catch (Exception hibernateException)
        {
            failed = true;
        }

        if ((failed && on_fail) || (!failed && !on_fail))
        {
            LogManager.info_msg("Performing database update: " + alter_cmd);
            try
            {
                SQLQuery sql_res = change_session.createSQLQuery(alter_cmd);
                int ret = sql_res.executeUpdate();
            }
            catch (Exception hibernateException1)
            {
                LogManager.err_log_fatal("Cannot change table struct " +  alter_cmd, hibernateException1);
                return false;
            }
            if (fill_cmd != null)
            {
                try
                {
                    SQLQuery sql_res = change_session.createSQLQuery(fill_cmd);
                    int ret = sql_res.executeUpdate();
                }
                catch (HibernateException hibernateException)
                {
                    LogManager.err_log_fatal("Cannot fill changed table struct " +  fill_cmd, hibernateException);
                    return false;
                }
            }
        }
        
        return true;
    }

    private void read_param_db()
    {
        try
        {
            check_db_changes();
        }

        
        catch (Exception ex)
        {
            LogManager.err_log_fatal("Error while checking database struct:", ex);
        }

        try
        {
            param_session = HibernateUtil.getSessionFactory().getCurrentSession();
            org.hibernate.Transaction tx = param_session.beginTransaction();
            read_param_db_qry = param_session.createQuery("from Mandant");

            List l = read_param_db_qry.list();

            

            if (!l.isEmpty() && l.get(0) instanceof Mandant)
            {
                for (int i = 0; i < l.size(); i++)
                {
                    Mandant m = (Mandant)l.get(i);
                    try
                    {
                        Set<Hotfolder> hfs = (Set<Hotfolder>)m.getHotfolders();
                        Iterator<Hotfolder> hfi = hfs.iterator();

                        while (hfi.hasNext())
                        {
                            Hotfolder hf = hfi.next();
                            String p = hf.getPath();
                        }

//                        param_session = HibernateUtil.getSessionFactory().getCurrentSession();
//                        m.setName( m.getName() + "s");
//                        GenericDAO.save(param_session, m);

                        MandantPreferences prefs = read_mandant_prefs( m );
                        add_mandant(prefs, m);

                        // PREFS NEEDS CONTEXT FOR ENCRYPTION
                        prefs.setContext( get_m_context(m));
                        prefs.read_props();
                        
                    }
                    catch (Exception ex )
                    {
                        ex.printStackTrace();
                        LogManager.err_log_fatal("Cannot read preferences for Mandant " +  m.getName(), ex);
                    }
                }
            }
            /*
            tx = param_session.beginTransaction();
            q = param_session.createQuery("from Hotfolder");
            l = q.list();
            tx.commit();
             * */
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public Communicator get_communicator()
    {
        return comm;
    }

    public void register_new_import( MandantContext m_ctx, DiskArchive da, String path ) throws ArchiveMsgException
    {
        int itype = CS_Constants.get_itype_from_name(path);
        switch (itype)
        {
            case CS_Constants.ITYPE_TBIRD: 
            {
                try
                {
                    MBoxImporter mbi = new MBoxImporter(path);
                    get_mb_import_server().add_mbox_import(m_ctx, da, mbi);
                }
                catch (Exception exception)
                {
                    get_mb_import_server().setStatusTxt(Main.Txt("Error_opening_TBird_mbox") + " " + path + ": " + exception.getMessage());
                    get_mb_import_server().setGoodState(false);
                }
                
                break;
            }
            case CS_Constants.ITYPE_OLEXP: 
            {
                try
                {
                    DBXImporter mbi = new DBXImporter(path);
                    get_mb_import_server().add_mbox_import(m_ctx, da, mbi);
                }
                catch (Exception exception)
                {
                    get_mb_import_server().setStatusTxt(Main.Txt("Error_opening_Olexp_mbox") + " " + path + ": " + exception.getMessage());
                    get_mb_import_server().setGoodState(false);
                }
                
                break;
            }
            case CS_Constants.ITYPE_EML:
            {
                try
                {
                    add_mail_file(new File(path), m_ctx.getMandant(), da, true, true);
                }
                catch (ArchiveMsgException ex)
                {
                    LogManager.log(Level.SEVERE, null, ex);
                    try
                    {
                        move_mail_to_quarantine(m_ctx, path);
                    }
                    catch (IOException ex1)
                    {
                        Main.err_log_fatal(Main.Txt("Cannot_store_mail_file_to_quarantine"));
                        LogManager.log(Level.SEVERE, null, ex1);
                    }
                }
                catch (VaultException ex)
                {
                    LogManager.log(Level.SEVERE, null, ex);
                    try
                    {
                        move_mail_to_hold_buffer(m_ctx, path);
                    }
                    catch (IOException ex1)
                    {
                        Main.err_log_fatal(Main.Txt("Cannot_store_mail_file_to_holdbuffer"));
                        LogManager.log(Level.SEVERE, null, ex1);
                    }
                }
                catch (IndexException ex)
                {
                    LogManager.log(Level.SEVERE, Main.Txt("Index_generation_failed"), ex);
                }
            }
            default:
            {
                Main.err_log_fatal(Main.Txt("Invalid_mailbox_type") + ": " + path);
                throw new ArchiveMsgException(Main.Txt("Invalid_mailbox_type"));
            }

        }
    }

   /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        DiskArchive da = null;
        Main m = new Main(args);
        LogicControl instance = new LogicControl();
        instance.initialize();

        MandantContext m_ctx = instance.get_mandant_by_id(1);
        da = m_ctx.get_da_by_id(1);
        String path = "z:\\Mailtest\\test2.eml";
        try
        {
            instance.register_new_import(m_ctx, da, path);
        }
        catch (ArchiveMsgException archiveMsgException)
        {
            System.out.println("Failed:" + archiveMsgException.getMessage());
        }
    }
    
    public String get_suffix( String p )
    {
        int idx = p.lastIndexOf('.');
        if (idx >= 0 && idx < p.length())
            return p.substring(idx+1);

        return p;
    }
    
    private synchronized void move_mail_to_dir( String path, File dir ) throws IOException
    {
        File file = new File(path);
        File new_file = new File( dir, file.getName() );
        while (new_file.exists())
        {
            String tmp_name = file.getName();
            int idx = tmp_name.lastIndexOf('.');
            if (idx > 0)
            {
                tmp_name = tmp_name.substring(0, idx) + "_" + System.currentTimeMillis() + file.getName().substring(idx);
            }
            new_file = new File( dir, tmp_name );
        }
        file.renameTo(new_file);
    }

    private void move_mail_to_quarantine( MandantContext m_ctx, String path ) throws IOException
    {
        move_mail_to_dir( path, m_ctx.getTempFileHandler().get_quarantine_mail_path());
    }

    public void move_mail_to_hold_buffer( MandantContext m_ctx, String path ) throws IOException
    {
        move_mail_to_dir( path, m_ctx.getTempFileHandler().get_hold_mail_path());
    }
    public void move_mail_to_index_buffer( MandantContext m_ctx, String path ) throws IOException
    {
        move_mail_to_dir( path, m_ctx.getTempFileHandler().get_index_buffer_mail_path());
    }

    public void move_to_quarantine( RFCFileMail mail, Mandant mandant ) throws IOException
    {
        MandantContext m_ctx = get_mandant_by_id( mandant.getId());

        move_mail_to_quarantine( m_ctx, mail.getFile().getAbsolutePath() );

    }
}
