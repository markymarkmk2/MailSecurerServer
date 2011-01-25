/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Utilities.CmdExecutor;

/**
 *
 * @author Administrator
 */
public class Reboot extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public Reboot()
    {
        super("REBOOT");
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String cmd[] = {"shutdown", "-r", "now" };
        
        CmdExecutor exe = new CmdExecutor( cmd );
        exe.start();
        
        return true;
    }
    
}
