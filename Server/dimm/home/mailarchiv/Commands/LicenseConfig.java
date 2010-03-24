/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.HWIDLicenseTicketHelper;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.Utilities.ZipUtilities;
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
            boolean licensed = Main.get_control().get_license_checker().is_licensed(product);
            answer = "0: LS:" + (licensed?"1":"0");
            return true;
        }

        // GET ALL LICENSES
        if ( command.compareTo("GET") == 0)
        {
            ArrayList list = Main.get_control().get_license_checker().get_ticket_list();
            XStream xs = new XStream();
            String ticket_str = xs.toXML(list);
            ticket_str = ZipUtilities.compress(ticket_str);

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
                String hwid = HWIDLicenseTicketHelper.generate_hwid();
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
            String ticket_str = pt.GetString( "TK:" );
            ticket_str = ZipUtilities.uncompress(ticket_str);

            XStream xs = new XStream();
            Object o = xs.fromXML( ticket_str);
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
