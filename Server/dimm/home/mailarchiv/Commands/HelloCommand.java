/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;

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

    @Override
    public boolean do_command(String data)
    {
        answer = "VER:" + Main.VERSION + " STATION:" + Main.get_long_prop(GeneralPreferences.STATION_ID, 0) + " NAME:" + Main.get_name();
        return true;
    }
    
}
