/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxy.Commands;

import java.awt.Toolkit;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import dimm.home.mailproxy.Main;
import dimm.home.mailproxy.Utilities.ParseToken;
import dimm.home.mailproxy.WorkerParent;

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

    public boolean do_command(String data)
    {                
        StringBuffer sb = new StringBuffer();        
        ArrayList<WorkerParent> list = Main.get_control().get_worker_list();
        
        // 1. LINE GENERAL INF= AND WORKERS
        sb.append( "TIM:'" + sdf.format( new Date() ) + "' " );
                        
        for (int i = 0; i < list.size(); i++)
        {
            WorkerParent wp = list.get(i);            
            sb.append("WPN:" + wp.getName() + " ST:'" + wp.getStatusTxt() + "' OK:" + (wp.isGoodState()?"1":"0") + "\n" );
        }
        
        long mem = Runtime.getRuntime().totalMemory();
        
                        
        answer = sb.toString();
                
        return true;
    }    
}
