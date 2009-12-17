/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.auth.GenericRealmAuth;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
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
            int m_id = (int)pt.GetLongValue("MA:");
            String admin_name = pt.GetString("NM:");
            String admin_pwd = pt.GetString("PW:");
            String auth_host = pt.GetString("HO:");
            String search_base = pt.GetString("SB:");
            String type = pt.GetString("TY:");
            int auth_port = (int)pt.GetLongValue("PO:");
            int acct_flags = (int)pt.GetLongValue("FL:");

            MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);

            // CREATE NEW ACT WITH NO MANDANT; NO ROLES AND ID == -1)
            AccountConnector act = new AccountConnector(-1, m_ctx.getMandant(), type, auth_host, auth_port, admin_name, admin_pwd, search_base, acct_flags, null);

            GenericRealmAuth auth_realm = GenericRealmAuth.factory_create_realm( act);

            // PRUEFE OB DER LOGIN OK IST
            boolean auth_ok = auth_realm.connect();

            // ON NON-LDAP CONNECTIONS WE HAVE TO CHECK USERNAME DIRECTLY IF USER WAS GIVEN TOO
            if (auth_ok && type.compareTo("ldap") != 0 && admin_name.length() > 0)
            {
                if (auth_realm.open_user_context(admin_name, admin_pwd))
                {
                    auth_realm.close_user_context();
                }
                else
                {
                    // SCHLIESSEN NICHT VERGESSEN
                    auth_realm.disconnect();
                    auth_ok = false;
                }
            }

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