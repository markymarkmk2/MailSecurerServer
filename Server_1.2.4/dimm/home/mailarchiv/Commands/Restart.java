/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.io.File;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class Restart extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public Restart()
    {
        super("RESTART");
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        ParseToken pt = new ParseToken(opt);
        
       
        boolean last_version= pt.GetBoolean("LV:");
        String cmd[];
        if (last_version)
        {        
            cmd = new String[]{"cp", "-p", Main.PROGNAME_LASTVALID,  Main.PROGNAME };
        }
        else
        {
            File started_ok = new File(Main.STARTED_OK);
            if (started_ok.exists())
                started_ok.delete();

            cmd = new String[]{"cp", "-p", Main.UPDATE_PATH + Main.PROGNAME,  Main.PROGNAME };                        
        }
            

        CmdExecutor exe = new CmdExecutor( cmd );
        if (exe.exec() == 0)

        {
            // RESTART IS DONE IN SHELL-SCRIPT-LOOP
            System.exit( 0 );
        }
                
        
        return true;
    }
    
}
