/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import dimm.home.importmail.MBoxImporter;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
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

    MBoxImporter mbi;
    Mandant mandant;
    DiskArchive da;


    MBoxImporterEntry( Mandant m, DiskArchive da, MBoxImporter mbi )
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
    SwingWorker idle_worker;

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

   
    public void add_mbox_import(Mandant m, DiskArchive da, String path)
    {
        try
        {
            MBoxImporter mbi = new MBoxImporter(path);
            MBoxImporterEntry mbie = new MBoxImporterEntry(m, da, mbi);

            import_list.add(mbie);
        }
        catch (Exception exception)
        {
            this.setStatusTxt("Error opening MBox " + path);
            this.setGoodState(false);
        }
    }

    @Override
    public boolean start_run_loop()
    {
        idle_worker = new SwingWorker()
        {
            @Override
            public Object construct()
            {
                do_idle();

                return null;
            }
        };

        idle_worker.start();

       this.setStatusTxt("Running");
       this.setGoodState(true);
       return true;
    }

    void do_idle()
    {
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);

            // CLEAN UP LIST OF FINISHED CONNECTIONS
            synchronized(import_list)
            {
                if ( m_Stop && import_list.isEmpty())
                    break;

                if (import_list.isEmpty())
                    continue;

            }
          }
 
    }


    void run_import( MBoxImporterEntry mbie )
    {
        MBoxImporter mbi = mbie.mbi;


        try
        {
            if (!mbi.get_msg_file().exists())
            {
                mbie.status = "MBOX file " + mbi.get_msg_file().getAbsolutePath() + " does not exist";
                mbie.err = 1;
                return;
            }
            mbie.size = mbi.get_msg_file().length();
            mbie.total_msg = mbi.get_message_count();
        }
        catch (ExtractionException extractionException)
        {
            mbie.status = "Error while parsing mbox file " + mbi.get_msg_file().getAbsolutePath() + ": " + extractionException.getMessage();
            mbie.err = 2;
            return;
        }
        try
        {
            for (int i = 0; i < mbie.total_msg; i++)
            {
                mbie.act_msg = i;
                Message msg = mbi.get_message(i);

                Main.get_control().add_new_mail( msg.getDataHandler().getInputStream(), mbie.mandant, mbie.da, false);
            }
        }
        catch (Exception exception)
        {
            mbie.status = "Error while extracting mbox file " + mbi.get_msg_file().getAbsolutePath() + " at message " + mbie.act_msg + ": " + exception.getMessage();
            mbie.err = 3;
            return;
        }

    }
  

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    public String get_status_txt()
    {
        StringBuffer stb = new StringBuffer();

        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < import_list.size(); i++)
        {
            MBoxImporterEntry pe = import_list.get(i);
            
            stb.append("MBIO"); stb.append(i); stb.append(":"); stb.append(pe..getProtokoll());
            stb.append(" PXPR"); stb.append(i); stb.append(":"); stb.append(pe.getRemotePort() );
            stb.append(" PXPL"); stb.append(i); stb.append(":"); stb.append(pe.getLocalPort() );
            stb.append(" PXIN"); stb.append(i); stb.append(":"); stb.append(pe.getInstanceCnt()  );
            stb.append(" PXHO"); stb.append(i); stb.append(":'"); stb.append(pe.getRemoteServer() + "' " );*/
        }

        return stb.toString();
    }


}
