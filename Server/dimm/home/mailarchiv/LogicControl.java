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
import dimm.home.index.IndexManager;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.ImapFetcher;
import home.shared.hibernate.Mandant;
import home.shared.hibernate.Milter;
import home.shared.hibernate.Proxy;
import dimm.home.mail.RFCFileMail;
import dimm.home.mail.RFCMailStream;
import dimm.home.mailarchiv.Exceptions.VaultException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
import dimm.home.workers.MailBoxImportServer;
import dimm.home.workers.MailProxyServer;
import dimm.home.workers.MilterServer;
import dimm.home.workers.SQLWorker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 *
 * @author Administrator
 */
public class LogicControl
{

    Communicator comm;
    StatusDisplay sd;
    MilterServer ms;
    MailProxyServer ps;
    HotfolderServer hf_server;
    MailBoxImportServer mb;
    SQLWorker sql;

    ArrayList<WorkerParent> worker_list;
    ArrayList<MandantContext> mandanten_list;

    LicenseChecker lic_checker;
    IndexManager idx_util;

    /** Creates a new instance of LogicControl */
    public LogicControl()
    {
        lic_checker = new LicenseChecker( Main.license_interface, Main.create_licensefile );

        mandanten_list = new ArrayList<MandantContext>();
        worker_list = new ArrayList<WorkerParent>();
        try
        {

            comm = new Communicator();
            worker_list.add(comm);

            sd = new StatusDisplay();
            worker_list.add(sd);

            ms = new MilterServer();
            worker_list.add(ms);

            ps = new MailProxyServer();
            worker_list.add(ps);

            hf_server = new HotfolderServer();
            worker_list.add(hf_server);

            mb = new MailBoxImportServer();
            worker_list.add(mb);

            sql = new SQLWorker();
            worker_list.add(sql);
            

            idx_util = new IndexManager(null);

        }
        catch (Exception ex)
        {
            LogManager.err_log_fatal("Constructor failed", ex);
        }
    }
    public IndexManager get_index_manager()
    {
        return idx_util;
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

    public void add_mail_file( File mail, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
    {
        // TODO: background
        if (mail == null)
        {
            throw new ArchiveMsgException("Mail input file is null");
        }
        RFCFileMail mf = new RFCFileMail( mail );

        MandantContext context = get_m_context(mandant);
        if (context == null)
        {
            throw new ArchiveMsgException("Invalid context for mail");
        }

        ArrayList<Vault> vault_list = context.getVaultArray();

        for (int i = 0; i < vault_list.size(); i++)
        {
            Vault vault = vault_list.get(i);
            vault.archive_mail(mf, context, da);
        }
    }

    public void add_new_mail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
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
                Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
                throw new ArchiveMsgException("Mail file disappeared: " + ex.getMessage());
            }
        }
        else
        {
            add_mail_file(rfc_dump, mandant, da, false);
        }

    /*        FileInputStream rfc_is;
    try
    {
    rfc_is = new FileInputStream(rfc_dump);
    }
    catch (FileNotFoundException ex)
    {
    Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
    throw new ArchiveMsgException("Mail file disappeared: " + ex.getMessage());
    }
    add_new_mail( rfc_is, mandant, da, background );
     * */
    }

    public void add_new_mail( InputStream rfc_is, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
    {
        if (true/*background*/)
        {
            File mail_file = create_dupl_temp_file(mandant, rfc_is);
            add_mail_file(mail_file, mandant, da, background);
        }
    /*      else
    {
    add_mail_file( rfc_is, mandant, da, background );
    }*/
    }

    public void add_new_outmail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
    {
        add_new_mail(rfc_dump, mandant, da, background);
    }

    public void add_new_inmail( File rfc_dump, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
    {
        add_new_mail(rfc_dump, mandant, da, background);
    }

    public void add_new_outmail( Message msg, Mandant mandant, DiskArchive diskArchive, boolean background ) throws ArchiveMsgException, VaultException
    {
        try
        {
            add_new_mail(msg.getInputStream(), mandant, diskArchive, background);
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Message Inputstream exception: " + ex.getMessage());
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Messaging exception: " + ex.getMessage());
        }
    }

    public void add_new_outmail( RFCMailStream mail, Mandant mandant, DiskArchive da, boolean background ) throws ArchiveMsgException, VaultException
    {
        add_new_mail(mail.getInputStream(), mandant, da, background);
    }

    public DiskSpaceHandler get_mail_dsh( Mandant mandant, String mail_uuid ) throws ArchiveMsgException
    {
        long ds_id = DiskSpaceHandler.get_ds_id_from_uuid(mail_uuid);
        long da_id = DiskSpaceHandler.get_da_id_from_uuid(mail_uuid);
        long time = DiskSpaceHandler.get_time_from_uuid(mail_uuid);


        MandantContext context = get_m_context(mandant);
        if (context == null)
        {
            throw new ArchiveMsgException("Invalid context for mail");
        }

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

        RFCFileMail msg = new RFCFileMail( null, new Date(time));
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
        File tmp_file = null;
        File directory = null;
        try
        {
            String tmp_dir = Main.get_prop(MandantPreferences.TEMPFILEDIR);
            if (tmp_dir != null && tmp_dir.length() > 0)
            {
                directory = new File(tmp_dir);
                if (!directory.exists())
                {
                    directory.mkdirs();
                }
            }
        }
        catch (Exception e)
        {
            directory = null;
        }
        try
        {
            tmp_file = File.createTempFile("mlt" + mandant.getId(), ".tmp", directory);
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (tmp_file == null)
        {
            try
            {
                tmp_file = File.createTempFile("mlt" + mandant.getId(), ".tmp", null);
            }
            catch (IOException ex)
            {
                Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
                throw new ArchiveMsgException("Cannot create temp file: " + ex.getMessage());
            }
        }

        // GET RID OF FILE ON EXIT OF JVM
        tmp_file.deleteOnExit();

        return tmp_file;
    }

    public File create_dupl_temp_file( Mandant mandant, InputStream is ) throws ArchiveMsgException
    {
        BufferedOutputStream bos = null;
        byte[] buff = new byte[8192];

        File tmp_file = create_temp_file(mandant);

        try
        {

            BufferedInputStream bis = new BufferedInputStream(is);
            bos = new BufferedOutputStream(new FileOutputStream(tmp_file));

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
        return tmp_file;
    }


    void initialize()
    {
       // READ PARAM DB
        read_param_db();

        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext ctx = mandanten_list.get(i);
            Set<Milter> milters = ctx.getMandant().getMilters();

            Iterator<Milter> it = milters.iterator();

            while (it.hasNext())
            {
                try
                {
                    ms.add_milter(it.next());
                }
                catch (IOException ex)
                {
                    ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                    ms.setGoodState(false);
                    LogManager.err_log_fatal(ms.getStatusTxt(), ex);
                }
                catch (ClassNotFoundException ex)
                {
                    ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                    ms.setGoodState(false);
                    LogManager.err_log_fatal(ms.getStatusTxt(), ex);
                }
                catch (InstantiationException ex)
                {
                    ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                    ms.setGoodState(false);
                    LogManager.err_log_fatal(ms.getStatusTxt(), ex);
                }
                catch (IllegalAccessException ex)
                {
                    ms.setStatusTxt("Cannot create milter: " + ex.getMessage());
                    ms.setGoodState(false);
                    LogManager.err_log_fatal(ms.getStatusTxt(), ex);
                }
            }
        }
        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext ctx = mandanten_list.get(i);
            Set<Proxy> proxies = ctx.getMandant().getProxies();

            Iterator<Proxy> it = proxies.iterator();

            while (it.hasNext())
            {
                 ps.add_proxy(it.next());
            }
        }
        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext ctx = mandanten_list.get(i);
            Set<Hotfolder> proxies = ctx.getMandant().getHotfolders();

            Iterator<Hotfolder> it = proxies.iterator();

            while (it.hasNext())
            {
                 hf_server.add_hfolder(it.next());
            }
        }
        for (int i = 0; i < mandanten_list.size(); i++)
        {
            MandantContext ctx = mandanten_list.get(i);
            Set<ImapFetcher> proxies = ctx.getMandant().getImapFetchers();

            Iterator<ImapFetcher> it = proxies.iterator();

            while (it.hasNext())
            {
                 mb.add_fetcher(it.next());
            }
        }



        // WAIT UNTIL WE REACH INET BEFORE CONTINUING
        if (comm != null)
        {
            comm.setStatusTxt("Checking internet...");
            for (int i = 0; i < 5; i++)
            {
                if (do_server_ping())
                {
                    break;
                }
                sleep(1000);
            }

            if (!do_server_ping())
            {
                comm.setStatusTxt("Internet not reachable");
                comm.setGoodState(false);
                LogManager.err_log_fatal("Cannot connect internet at startup");

            }
            else
            {

                if (!comm.isGoodState())
                {
                    comm.setStatusTxt("Internet reachable");
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

    void set_system_time()
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
                String rdate_cmd = Main.get_prop(GeneralPreferences.RDATE_COMMAND, "ntpdate " + ntp_server_list[idx] + " && hwclock --directisa -w");
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

                    LogManager.err_log_warn("System time cannot be retrieved: " + err_txt);

                    sleep(1000);
                }
                else
                {
                    LogManager.debug_msg(1, "Systemtime was synchronized");
                    break;
                }
            }
        }
        catch (Exception exc)
        {
        }
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
        if (sd != null)
        {
            this.sd.set_router_ok(ok);
        }

        if (comm != null)
        {

            if (!ok)
            {
                comm.setStatusTxt("Internet not reachable");
            }
            comm.setGoodState(ok);
        }

        return ok;
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
                    LogManager.err_log_fatal("Cannot start runloop for Worker " + worker_list.get(i).getName());
                }
            }


            while (true)
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
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }


        LogManager.info_msg("Closing down " + Main.APPNAME);
        System.exit(0);
    }

    public void set_shutdown( boolean b )
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            worker_list.get(i).setShutdown(b);
        }
    }

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
        }

        return now;
    }

    public StatusDisplay get_status_display()
    {
        return sd;
    }
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
        MandantPreferences prefs = new MandantPreferences(prefs_path);
        return prefs;
    }

    org.hibernate.classic.Session param_session;
    private void read_param_db()
    {
        try
        {
            param_session = HibernateUtil.getSessionFactory().getCurrentSession();
            org.hibernate.Transaction tx = param_session.beginTransaction();
            org.hibernate.Query q = param_session.createQuery("from Mandant");
            
            List l = q.list();

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
                        
                    }
                    catch (Exception ex )
                    {
                        LogManager.err_log_fatal("Cannot read preferences for Mandant " +  m.getName(), ex);
                    }
                }
            }
            tx = param_session.beginTransaction();
            q = param_session.createQuery("from Hotfolder");
            l = q.list();
            tx.commit();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
