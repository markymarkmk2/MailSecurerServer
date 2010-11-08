/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package dimm.home.workers;

import dimm.home.importmail.MultipleMailImporter;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.vault.Vault;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import home.shared.mail.RFCFileMail;
import java.io.IOException;
import java.util.ArrayList;
import javax.mail.Message;
import javax.swing.Timer;


class MBoxImporterEntry
{

    int total_msg;
    int act_msg;
    long size;
    float mb_per_s;
    String status;
    int err;
    MultipleMailImporter mbi;
    Mandant mandant;
    DiskArchive da;

    MBoxImporterEntry( Mandant m, DiskArchive da, MultipleMailImporter mbi )
    {
        this.mbi = mbi;
        this.da = da;
        mandant = m;
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
}
/**
 *
 * @author Administrator
 */
public class MBoxImportServer extends WorkerParent
{

    public static final String NAME = "MBoxImportServer";
    Timer timer;
    final ArrayList<MBoxImporterEntry> import_list;
    BackgroundWorker idle_worker;
    boolean m_Stop = false;

    /** Creates a new instance of StatusDisplay */
    public MBoxImportServer()
    {
        super(NAME);
        import_list = new ArrayList<MBoxImporterEntry>();
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    public void add_mbox_import( MandantContext m_ctx, DiskArchive da, MultipleMailImporter mbi )
    {
        MBoxImporterEntry mbie = new MBoxImporterEntry(m_ctx.getMandant(), da, mbi);
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
            MBoxImporterEntry mbie = null;

            // GET FIFO ENTRY
            synchronized (import_list)
            {
                if (m_Stop && import_list.isEmpty())
                {
                    break;
                }

                if (!import_list.isEmpty())
                {
                    mbie = import_list.get(0);
                    
                    // ARE WE BUSY
                    if (mbie.is_in_rebuild())
                        break;

                    import_list.remove(mbie);

                }
            }
            // LIST EMPTY ?
            if (mbie == null)
                break;

            setStatusTxt(Main.Txt("Importing_mailbox"));
            run_import(mbie);
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

    void run_import( MBoxImporterEntry mbie )
    {
        MultipleMailImporter mbi = mbie.mbi;

        try
        {
            if (!mbi.get_msg_file().exists())
            {
                mbie.status = "MBOX file " + mbi.get_msg_file().getAbsolutePath() + " does not exist";
                mbie.err = 1;
                return;
            }

            mbi.open();

            mbie.size = mbi.get_msg_file().length();
            mbie.total_msg = mbi.get_message_count();
        }
        catch (Exception extractionException)
        {
            try
            {
                mbi.close();
            }
            catch (IOException iOException)
            {
            }

            mbie.status = "Error while parsing mbox file " + mbi.get_msg_file().getAbsolutePath() + ": " + extractionException.getMessage();
            mbie.err = 2;

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, mbie.status, extractionException);
            return;
        }
        try
        {
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Starting_import") + " N=" + mbie.total_msg + " (" + Long.toString(mbie.size/(1000*1000)) + "MB)" );
            long start_t = System.currentTimeMillis();
            int i = 0;
            for (i = 0; i < mbie.total_msg; i++)
            {
                mbie.act_msg = i;
                Message msg = mbi.get_message(i);

                
                RFCFileMail mail = Main.get_control().create_import_filemail_from_eml(mbie.mandant, msg, "mbximp", mbie.da);
                
                Main.get_control().add_rfc_file_mail(mail, mbie.mandant, mbie.da, /*bg*/ true, /*del_after*/ true);

                if (isShutdown())
                    break;
            }
            long end_t = System.currentTimeMillis();
            int speed = 0;
            if (end_t > start_t)
            {
                speed = (int)((1000*i) / (end_t - start_t));
            }
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_IMPORT, Main.Txt("Messages_imported") + ": " + i + " (" + speed + "/s)" );
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            mbie.status = "Error while extracting mbox file " + mbi.get_msg_file().getAbsolutePath() + " at message " + mbie.act_msg + ": " + exception.getMessage();
            mbie.err = 3;

            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_IMPORT, mbie.status, exception);
            return;
        }
        finally
        {
            try
            {
                mbi.close();
                mbi.delete();
            }
            catch (IOException iOException)
            {
            }
        }
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        return true;
    }

    @Override
    public String get_task_status()
    {
        return get_task_status(0);
    }
   
    @Override
    public String get_task_status( int ma_id )
    {
        StringBuilder stb = new StringBuilder();


        synchronized (import_list)
        {
            for (int i = 0; i < import_list.size(); i++)
            {
                MBoxImporterEntry mbie = import_list.get(i);
                if (ma_id > 0 && mbie.mandant.getId() != ma_id)
                    continue;

                stb.append("MBISI");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.size);
                stb.append(" MBIST");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.status);
                stb.append(" MBIAM");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.act_msg);
                stb.append(" MBITM");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.total_msg);
                stb.append("\n");
            }
        }
        return stb.toString();
    }
}
