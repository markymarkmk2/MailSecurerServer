/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParent;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */


public class GetWorkerStatus extends AbstractCommand
{
    SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");    

    
    /** Creates a new instance of HelloCommand */
    public GetWorkerStatus()
    {
        super("GETWORKERSTATUS");
        
    }
    
    String mk_dim_str( long val )
    {
        String[] dim = {"", " kB", " MB", " GB", " TB" };  
        int nachkomma = 0;
        for (int i = 0; i < dim.length; i++)
        {            
            if (val < 1200)
            {
                if (nachkomma != 0)
                    return val + "." + nachkomma + dim[i];
                else
                    return val + dim[i];
            }
            nachkomma = (int)(val % 100);
            val /= 1024;
        }
        
        return val + " PB";
    }

    @Override
    public boolean do_command(String data)
    {                
        ArrayList<WorkerParent> list = Main.get_control().get_worker_list();
        
        String opt = get_opts(data);

        ParseToken pt = new ParseToken(opt);

        int m_id = (int) pt.GetLongValue("MA:");
        
        

        ArrayList<ArrayList<String>> result_list = new ArrayList<ArrayList<String>>();


        for (int i = 0; i < list.size(); i++)
        {

            ArrayList<String> result = new ArrayList<String>();

            try
            {
                WorkerParent wp = list.get(i);
                result.add( wp.getName() );
                result.add( wp.isGoodState()?"1":"0" );
                result.add( wp.getStatusTxt() );

                if ( m_id > 0)
                    result.add(  wp.get_task_status(m_id) );
                else
                    result.add(  wp.get_task_status() );

                result_list.add( result );
            }
            catch( Exception exc )
            {
                LogManager.msg_cmd(LogManager.LVL_WARN, "Error reading status", exc);
            }
        }

        String cxml = ParseToken.BuildCompressedObjectString(result_list);


        answer = "0: " + cxml;
                
        return true;
    }        
}
