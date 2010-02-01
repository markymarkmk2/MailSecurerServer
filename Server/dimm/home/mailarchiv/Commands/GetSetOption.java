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
import java.util.StringTokenizer;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.mailarchiv.Utilities.Preferences;

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

    @Override
    public boolean do_command(String data)
    {
        boolean is_setter = false;
        String opt = get_opts( data );
        answer = "";
        
        ParseToken pt = new ParseToken(opt);
        
        String command = pt.GetString("CMD:");
        String name = pt.GetString("NAME:");
        long ma_id = pt.GetLongValue("MA:");
        String val = pt.GetString("VAL:");

        if (getSsoEntry() != null && !getSsoEntry().is_admin())
        {
            answer = "8: only admin allowed";
            return true;
        }

        Preferences prefs = Main.get_prefs();
        if (ma_id > 0)
        {
            if (Main.get_control().get_mandant_by_id(ma_id) == null)
            {
                answer = "9: invalid mandant";
                return true;
            }
            prefs = Main.get_control().get_mandant_by_id(ma_id).getPrefs();
        }


      
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
            if (handle_option( prefs, command, name, val ))
            {
                if (is_setter)
                {
                    Main.debug_msg( 1, "Parameter <" + name + "> was set to <" + val + ">");
                }
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

    
    boolean handle_option( Preferences prefs, String command, String name, String new_val)
    {
        answer = "";
        
        if ( command.compareTo("GET") == 0)
        {
            String val = prefs.get_prop( name );
            answer = val;
            return true;
        }
        else if ( command.compareTo("GETLONG") == 0)
        {
            long val = prefs.get_long_prop( name );
            answer = Long.toString(val);
            return true;
        }
        else if ( command.compareTo("SET") == 0)
        {
            String val = new_val;
            if (val == null)
                return false;
                        
            prefs.set_prop( name, val );
            prefs.store_props();
            return true;
          
        }        
        else if ( command.compareTo("SETLONG") == 0)
        {
            String val = new_val;
            try
            {
                long v = Long.parseLong( val );
                prefs.set_prop( name, val );
                prefs.store_props();
                return true;
            }
            catch (Exception exc) 
            {
            }
        }
        else if ( command.compareTo("GETPWD") == 0)
        {
            if (prefs instanceof MandantPreferences)
            {
                MandantPreferences mprefs = (MandantPreferences)prefs;
                if (mprefs.hasNoPwd())
                {
                    answer = "1: no_pwd";
                    return true;
                }
                answer = "0: PWD:" + mprefs.get_password();
            }
            else
            {
                answer = "9: invalid prefs";
                return true;
            }
         }
        else if ( command.compareTo("SETPWD") == 0)
        {
            if (prefs instanceof MandantPreferences)
            {
                MandantPreferences mprefs = (MandantPreferences)prefs;
                if (mprefs.hasNoPwd())
                {
                    mprefs.set_password(new_val);
                    mprefs.store_props();
                    answer = "0:";
                    return true;
                }
                else
                {
                    answer = "1: " + Main.Txt("Password_was_already_set");
                    return true;
                }
            }
            else
            {
                answer = "9: invalid prefs";
                return true;
            }
         }
        else if ( command.compareTo("GETADMIN") == 0)
        {

            answer = "0: NA:" + Main.get_prop(GeneralPreferences.SYSADMIN_NAME, "sys") + " PWD:" + Main.get_prefs().get_password();
            return true;
         }
        else if ( command.compareTo("SETADMIN") == 0)
        {
            Main.set_prop(GeneralPreferences.SYSADMIN_NAME, name);
            Main.get_prefs().set_password(new_val);
            Main.get_prefs().store_props();
            answer = "0: ";
            return true;

         }
        
        return false;
    }
   
}
