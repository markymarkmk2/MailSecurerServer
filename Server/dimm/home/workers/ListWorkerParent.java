/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.workers;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.mailarchiv.WorkerParentChild;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public class ListWorkerParent extends WorkerParent
{
   // protected boolean m_Stop = false;

    final ArrayList<WorkerParentChild> child_list;
    SwingWorker idle_worker;

    public ListWorkerParent( String _name )
    {
        super(_name);
        child_list = new ArrayList<WorkerParentChild>();
    }


 
    public void add_child(WorkerParentChild child)
    {
        synchronized(child_list)
        {
            child_list.add( child );
        }
    }
    public void remove_child( Object db_object )
    {
        for (int i = 0; i < child_list.size(); i++)
        {
            WorkerParentChild child_entry = child_list.get(i);
            if (child_entry.get_db_object() == db_object)
            {
                child_entry.finish();
                child_list.remove(child_entry);
                break;
            }
        }
    }


    void do_idle()
    {
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);
            // CLEAN UP LIST OF FINISHED CONNECTIONS
            synchronized (child_list)
            {
                for (int i = 0; i < child_list.size(); i++)
                {
                    WorkerParentChild hf = child_list.get(i);
                    hf.idle_check();
                }
            }
        }
        for (int i = 0; i < child_list.size(); i++)
        {
            WorkerParentChild hf = child_list.get(i);
            hf.finish();
        }
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, getName() + " has " + child_list.size() + " task(s)");
        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }
       // m_Stop = false;
        synchronized (child_list)
        {
            for (int i = 0; i < child_list.size(); i++)
            {
                if (child_list.get(i).is_started())
                {
                    continue;
                }
                final WorkerParentChild child = child_list.get(i);
                SwingWorker worker = new SwingWorker()
                {

                    @Override
                    public Object construct()
                    {
                        child.run_loop();
                        return null;
                    }
                };
                worker.start();
            }
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

    @Override
    public String get_task_status()
    {
        StringBuffer stb = new StringBuffer();

        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < child_list.size(); i++)
        {
            WorkerParentChild ch  = child_list.get(i);

            stb.append(ch.get_task_status_txt() );
        }

        return stb.toString();
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        return true;
    }


}
