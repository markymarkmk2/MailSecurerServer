/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.util.StringTokenizer;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class GetSetOption extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public GetSetOption()
    {
        super("GETSETOPTION");
    }

    public boolean do_command(String data)
    {
        boolean is_setter = false;
        String opt = get_opts( data );
        answer = "";
        
        ParseToken pt = new ParseToken(opt);
        
        String command = pt.GetString("CMD:");
        String name = pt.GetString("NAME:");
        String mode = pt.GetString("MODE:");
        String val = pt.GetString("VAL:");
        
        if (command == null || name == null)
        {
            answer = MISS_ARGS;
            return false;
        }
        
        if (command.indexOf("SET") >= 0)
            is_setter = true;
        
        if (is_setter && val == null)
        {
            answer = MISS_ARGS;
            return false;
        }
        
         
        boolean ok = true;
        
        try
        {
            if (handle_option( command, name, val ))
            {
                if (is_setter)
                {
                    if (Main.write_prefs())
                    {
                        Main.debug_msg( 1, "Parameter <" + name + "> was set to <" + val + ">");
                        ok = true;
                    }
                }
                else
                    ok = true;
            }                    
        }
        catch (Exception exc)
        {
            Main.err_log("Setting parameter <" + data + "> failed: " + exc.getMessage() );
            return false;
        }
        
        return ok;
    }

    
    boolean handle_option( String command, String name, String new_val)
    {
        answer = "";
        
        if ( command.compareTo("GET") == 0)
        {
            String val = Main.get_prop( name );
            answer = val;
            return true;
        }
        else if ( command.compareTo("GETLONG") == 0)
        {
            long val = Main.get_long_prop( name );
            answer = Long.toString(val);
            return true;
        }
        if ( command.compareTo("SET") == 0)
        {
            String val = new_val;
            if (val == null)
                return false;
                        
            Main.set_prop( name, val );            
            return true;
          
        }        
        if ( command.compareTo("SETLONG") == 0)
        {
            String val = new_val;
            try
            {
                long v = Long.parseLong( val );
                Main.set_long_prop( name, v );            
                return true;
            }
            catch (Exception exc) {}
        }        
        
        return false;
    }
   
}
