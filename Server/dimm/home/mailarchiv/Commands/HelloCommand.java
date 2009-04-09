/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Preferences;

/**
 *
 * @author Administrator
 */
public class HelloCommand extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public HelloCommand()
    {
        super("HELLO");
    }

    public boolean do_command(String data)
    {
        answer = "VER:" + Main.VERSION + " STATION:" + Main.get_long_prop(Preferences.STATION_ID, 0);         
        return true;
    }
    
}
