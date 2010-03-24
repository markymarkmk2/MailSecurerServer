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
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class ReadLog extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public ReadLog()
    {
        super("READLOG");
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        
        ParseToken pt = new ParseToken(opt);
        
        String log = pt.GetString("LOG:");
        long lines = pt.GetLong("LINES:");
        
        if (log == null || lines == 0)
        {
            answer = MISS_ARGS;
            return false;
        }
        
        StringBuffer sb = new StringBuffer();
        
        boolean ok = Main.read_log( log, lines, sb );
        
        if (ok)
            answer = sb.toString();
        else
            answer = WRONG_ARGS;
        
        
        return ok;
    }
    
}
