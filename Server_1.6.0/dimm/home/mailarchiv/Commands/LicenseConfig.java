/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.hibernate.HParseToken;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LicenseChecker;
import home.shared.Utilities.ParseToken;
import home.shared.license.HWIDLicenseTicket;
import home.shared.license.LicenseTicket;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */
public class LicenseConfig extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public LicenseConfig()
    {
        super("LicenseConfig");
    }

    @Override
    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        
        ParseToken pt = new ParseToken(opt);
        
        String command = pt.GetString("CMD:");
        if (command == null)
        {
            answer = MISS_ARGS;
            return false;
        }        

        // CHECK A SPECIFIC LICENSE
        if ( command.compareTo("CHECK") == 0)
        {
            String product = pt.GetString("PRD:");
            LicenseChecker lc = Main.get_control().get_license_checker();
            boolean licensed = lc.is_licensed(product);
            int max_users = lc.get_max_units(product);
            int used_users = lc.get_used_units(product);
            answer = "0: LS:" + (licensed?"1":"0") + " MU:" + max_users + " UU:" + used_users;
            return true;
        }

        // GET ALL LICENSES
        if ( command.compareTo("GET") == 0)
        {
            ArrayList list = Main.get_control().get_license_checker().get_ticket_list();
            String ticket_str = HParseToken.BuildCompressedObjectString(list);

            answer = "0: TK:\"" + ticket_str + "\"";
            return true;
        }
        // GET ALL LICENSES
        if ( command.compareTo("DEL") == 0)
        {
            String product = pt.GetString("PRD:");
            Main.get_control().get_license_checker().delete_license( product );
            Main.get_control().get_license_checker().read_licenses();
            answer = "0: ";
            return true;
        }
        // GET HWID
        if ( command.compareTo("HWID") == 0)
        {
            try
            {
                String hwid = HWIDLicenseTicket.generate_hwid();
                
                if (HWIDLicenseTicket.is_virtual_license())
                {
                    hwid = HWIDLicenseTicket.read_virtual_license();
                }
                answer = "0: HWID:\"" + hwid + "\"";
                return true;
            }
            catch (IOException iOException)
            {
                answer = "1: " + iOException.getLocalizedMessage();
                return true;
            }
        }

        // SET A SPECIFIC LICENSE
        if ( command.compareTo("SET") == 0)
        {
            String product = pt.GetString("PRD:");
            Object o = pt.GetCompressedObject( "TK:" );
            if (o instanceof LicenseTicket)
            {
                // WRITE NEW LIC
                Main.get_control().get_license_checker().write_ticket((LicenseTicket)o);
                // REREAD ALL LICS
                Main.get_control().get_license_checker().read_licenses();

                // AND CHECK IF VALID
                boolean licensed = Main.get_control().get_license_checker().is_licensed(product);
                answer = "0: LS:" + (licensed?"1":"0");
                return true;
            }
        }        

        if (answer.length() == 0)
            answer = WRONG_ARGS;
        
        return false;
    }

    
}
