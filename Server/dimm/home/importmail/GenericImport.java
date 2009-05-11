/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public abstract class GenericImport extends WorkerParent
{
    boolean m_Stop = false;
    SwingWorker idle_worker;
    final ArrayList<ProxyConnection> connection_list;


    GenericImport(String name)
    {
        super(name);

        m_Stop = false;
        connection_list = new ArrayList<ProxyConnection>();
    }

    public abstract void run_loop();

    @Override
    public boolean start_run_loop()
    {
        LogManager.debug_msg(1, "Starting import task" + getName() );

        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }

        m_Stop = false;

        SwingWorker worker = new SwingWorker()
        {
            @Override
            public Object construct()
            {
                run_loop();
                return null;
            }
        };

        worker.start();

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
            synchronized(connection_list)
            {
                if ( m_Stop && connection_list.isEmpty())
                    break;


                for (int i = 0; i < connection_list.size(); i++)
                {
                    ProxyConnection m = connection_list.get(i);
                    if (!m.is_connected())
                    {
                        connection_list.remove(m);
                        i--;
                    }
                }
                if (this.isGoodState())
                {
                    if (connection_list.size() > 0)
                        this.setStatusTxt("Connected to " + connection_list.size() + " client(s)" );
                    else
                        this.setStatusTxt("");
                }
            }
        }
    }

    @Override
    public boolean initialize()
    {
        return true;
    }
    
    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }



}
