/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.auth.GenericRealmAuth;
import dimm.home.auth.LDAPAuth;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import home.shared.hibernate.AccountConnector;

/**
 *
 * @author mw
 */
public class TestLogin extends AbstractCommand
{
    public TestLogin()
    {
        super("TestLogin");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String command = pt.GetString("CMD:");
        if (command.compareTo("test") == 0)
        {
            String admin_name = pt.GetString("NM:");
            String admin_pwd = pt.GetString("PW:");
            String ldap_host = pt.GetString("HO:");
            String type = pt.GetString("TY:");
            int ldap_port = (int)pt.GetLongValue("PO:");
            boolean ssl = pt.GetBoolean("SSL:");

            // CREATE NEW ACT WITH NO MANDANT; NO ROLES AND ID == -1)
            AccountConnector act = new AccountConnector(-1, null, type, ldap_host, ldap_port, admin_name, admin_pwd, ssl ? CS_Constants.ACCT_USE_SSL : 0, null);

            GenericRealmAuth auth_realm = GenericRealmAuth.factory_create_realm( act);

            // PRUEFE OB DER LOGIN OK IST
            boolean auth_ok = auth_realm.connect();

            // SCHLIESSEN NICHT VERGESSEN
            if (auth_ok)
                auth_realm.disconnect();

            if (!auth_ok)
            {
                answer = "1: " + auth_realm.get_error_txt();
            }
            else
            {                
                answer = "0: ok";
            }

            return true;
        }

        answer = "1: Unknown subcommand: " + data;
        return false;
    }
}