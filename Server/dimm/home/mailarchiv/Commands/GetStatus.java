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
import java.util.Date;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.WorkerParent;
import java.io.File;

/**
 *
 * @author Administrator
 */


public class GetStatus extends AbstractCommand
{
    SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");    

    
    /** Creates a new instance of HelloCommand */
    public GetStatus()
    {
        super("GETSTATUS");
        
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

    public boolean do_command(String data)
    {                
        StringBuffer sb = new StringBuffer();        
        ArrayList<WorkerParent> list = Main.get_control().get_worker_list();
        
        File rest = new File(".");
        long free_bytes = rest.getFreeSpace();
        long free_mem = Runtime.getRuntime().totalMemory();   
        String fsp = mk_dim_str( free_bytes );
        String fmy = mk_dim_str( free_mem );
        
        // 1. LINE GENERAL INF= AND WORKERS
        sb.append( "TIM:'" + sdf.format( new Date() ) + "' FSP:'" + fsp + "' FMY:'" + fmy + "'\n");

        String proxy_status = Main.get_control().get_proxy_server().get_proxy_status_txt();
        sb.append( proxy_status + "\n");
                        
                        
        for (int i = 0; i < list.size(); i++)
        {
            WorkerParent wp = list.get(i);            
            sb.append("WPN:" + wp.getName() + " ST:'" + wp.getStatusTxt() + "' OK:" + (wp.isGoodState()?"1":"0") + "\n" );
        }
        
        answer = sb.toString();
                
        return true;
    }        
}
