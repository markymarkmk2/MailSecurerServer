/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import home.shared.hibernate.Hotfolder;
import dimm.home.importmail.HotFolderImport;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.util.ArrayList;
import javax.swing.Timer;




/**
 *
 * @author Administrator
 */
public class HotfolderServer extends WorkerParent
{
    
    
    public static final String NAME = "HotfolderServer";

    
    Timer timer;
    final ArrayList<HotFolderImport> hfolder_list;
    SwingWorker idle_worker;

    boolean m_Stop = false;
    
    
    /** Creates a new instance of StatusDisplay */
    public HotfolderServer()
    {        
        super(NAME);
        hfolder_list = new ArrayList<HotFolderImport>();
    }
    
    @Override
    public boolean initialize()
    {
        return true;
    }

    public void set_hfolder_list(Hotfolder[] hf_array) throws Exception
    {
        synchronized(hfolder_list)
        {
            hfolder_list.clear();
            for (int i = 0; i < hf_array.length; i++)
            {
                hfolder_list.add( new HotFolderImport( hf_array[i] ) );
            }
        }
    }
    public void add_hfolder(Hotfolder hf)
    {
        synchronized(hfolder_list)
        {
            hfolder_list.add( new HotFolderImport( hf ) );
        }
    }

    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting " + hfolder_list.size() + " hotfolder tasks" );

        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }

        m_Stop = false;

        for (int i = 0; i < hfolder_list.size(); i++)
        {
            final HotFolderImport hf = hfolder_list.get(i);

            SwingWorker worker = new SwingWorker()
            {
                @Override
                public Object construct()
                {

                    hf.run_loop();

                    return null;
                }
            };

            worker.start();
        }

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
            synchronized(hfolder_list)
            {
                if ( m_Stop && hfolder_list.isEmpty())
                    break;


                for (int i = 0; i < hfolder_list.size(); i++)
                {
                    HotFolderImport hf = hfolder_list.get(i);
                    hf.idle_check();
                }
                if (this.isGoodState())
                {
                     this.setStatusTxt("");
                }
            }
        }
        for (int i = 0; i < hfolder_list.size(); i++)
        {
            HotFolderImport hf = hfolder_list.get(i);
            hf.finish();
        }
    }



    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    @Override
    public String get_task_status()
    {
        StringBuffer stb = new StringBuffer();

        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < hfolder_list.size(); i++)
        {
            HotFolderImport hf = hfolder_list.get(i);

            stb.append("HFST"); stb.append(i); stb.append(":"); stb.append(hf.get_status_txt() );
        }

        return stb.toString();
    }
}
