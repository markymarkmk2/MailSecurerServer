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
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.license.LicenseTicket;
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
            answer = "0: TK:" + ticket_str ;
            return true;
        }

        // SET A SPECIFIC LICENSE
        if ( command.compareTo("SET") == 0)
        {
            String product = pt.GetString("PRD:");
            String ticket_str = pt.GetString( "TK:" );
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
