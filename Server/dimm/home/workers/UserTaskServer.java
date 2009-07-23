/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.util.ArrayList;
import javax.swing.Timer;


class UserTask
{
    public static final int UT_URGENT = 1;
    public static final int UT_REGULAR = 2;

    int level;
    String description;
    Object obj;
    String method;
    ArrayList params;

    UserTask( int level, String description, Object o, String method, ArrayList params )
    {
        this.level = level;
        this.description = description;
        this.obj = o;
        this.params = params;
    }

}


/**
 *
 * @author Administrator
 */
public class UserTaskServer extends WorkerParent
{
    
    
    public static final String NAME = "UserTaskServer";

    
    Timer timer;
    final ArrayList<UserTask> task_list;
    SwingWorker idle_worker;

    boolean m_Stop = false;
    
    
    /** Creates a new instance of StatusDisplay */
    public UserTaskServer()
    {        
        super(NAME);
        task_list = new ArrayList<UserTask>();
    }
    
    @Override
    public boolean initialize()
    {
        return true;
    }

   
    public void add_usertask(int level, String description, Object o, String method, ArrayList params)
    {
        task_list.add( new UserTask( level, description, o, method, params ) );
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
            synchronized(task_list)
            {
                if ( m_Stop && task_list.isEmpty())
                    break;

            }
          }
  
    }

  

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }


}
