/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.util.ArrayList;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */


public class ListOptions extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public ListOptions()
    {
        super("LISTOPTIONS");
    }

    public boolean do_command(String data)
    {
        
        StringBuffer sb = new StringBuffer();
        
        
        ArrayList<String> list = Main.get_properties();
        
        for (int i = 0; i < list.size(); i++)
        {
            sb.append(list.get(i).toString() );
            sb.append("\n" );            
        }
        
        
        answer = sb.toString();
        
        
        return true;
    }
    
}
