/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.hibernate.HParseToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import dimm.home.mailarchiv.Main;
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

            WorkerParent wp = list.get(i);
            result.add( Main.get_control().get_ex_import_server().getName() );

            StringBuilder sb = new StringBuilder();
            sb.append("WPN:");
            sb.append( wp.getName() );
            sb.append( " ST:'" );
            sb.append( wp.getStatusTxt() );
            sb.append("' OK:");
            sb.append( (wp.isGoodState()?"1":"0") );
            sb.append( " " );
            if ( m_id > 0)
                sb.append( wp.get_task_status(m_id) );
            else
                sb.append( wp.get_task_status() );

            result.add( sb.toString() );
            result_list.add( new ArrayList<String>());
        }

        String cxml = HParseToken.BuildCompressedString(result_list);


        answer = "0: " + cxml;
                
        return true;
    }        
}
