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
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    String result_text;
    boolean result_ok;
    int ma_id;

    UserTask( int ma_id, int level, String description, Object o, String method, ArrayList params )
    {
        this.ma_id = ma_id;
        this.level = level;
        this.description = description;
        this.obj = o;
        this.method = method;
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
    BackgroundWorker idle_worker;

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

   
    public void add_usertask(int ma_id, int level, String description, Object o, String method, ArrayList params)
    {
        synchronized( task_list )
        {
            task_list.add( new UserTask( ma_id, level, description, o, method, params ) );
        }
    }

    @Override
    public boolean start_run_loop()
    {
        if (!is_started)
        {
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
        }

       this.setStatusTxt("Running");
       this.setGoodState(true);
       return true;
    }

    void work_jobs()
    {
        while (true)
        {
            UserTask userTask = null;
            synchronized (task_list)
            {
                if (task_list.size() > 0)
                {
                    userTask = task_list.remove(0);
                }
            }

            if (userTask == null)
            {
                break;
            }

            // NOT LOCKED, OTHERS CAN ADD ENTRIES TO LIST
            run_usertask(userTask);
        }
    }

    void do_idle()
    {
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);

            work_jobs();
        }
        finished = true;
    }

  

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }


    private void run_usertask( UserTask userTask )
    {
        Method[] m_list = userTask.obj.getClass().getDeclaredMethods();
        for (int i = 0; i < m_list.length; i++)
        {
            Method method = m_list[i];
            if (method.getName().compareTo(userTask.method) == 0)
            {
                boolean params_fit = true;
                Class[] types = method.getParameterTypes();


                // TYPE OF FIRST ARG IS USERTASK OBJECT
                for (int j = 1; j < types.length; j++)
                {
                    Class class1 = types[j];
                    if (class1.getName().compareTo(userTask.params.get(j).getClass().getName()) != 0)
                    {
                        params_fit = false;
                        break;
                    }
                }
                if (!params_fit)
                    continue;

                call_method( userTask, method );

            }
        }
    }

    private void call_method( UserTask userTask, Method method )
    {
        int arg_len = userTask.params.size();

        try
        {
            switch (arg_len)
            {
                case 0:  method.invoke(userTask.obj);  break;
                case 1:  method.invoke(userTask.obj, userTask.params.get(0));  break;
                case 2:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0));  break;
                case 3:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0), userTask.params.get(0));  break;
                case 4:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0));  break;
                case 5:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0));  break;
                case 6:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0));  break;
                case 7:  method.invoke(userTask.obj, userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0), userTask.params.get(0));  break;
                default: userTask.result_text = "Too many params in usertask"; userTask.result_ok = false;
            }
        }
        catch (IllegalAccessException illegalAccessException)
        {
        }
        catch (IllegalArgumentException illegalArgumentException)
        {
        }
        catch (InvocationTargetException invocationTargetException)
        {
        }
   }
    @Override
    public String get_task_status()
    {
        return  get_task_status( 0 );
    }

    @Override
    public String get_task_status( int ma_id )
    {
        StringBuilder stb = new StringBuilder();

        synchronized( task_list )
        {
            for (int i = 0; i < task_list.size(); i++)
            {
                UserTask userTask = task_list.get(i);
                if (ma_id > 0 && ma_id >= userTask.ma_id)
                    continue;

                stb.append("UTD:");
                stb.append( userTask.description );
                stb.append("\n");
            }
        }

        return stb.toString();

    }


}
