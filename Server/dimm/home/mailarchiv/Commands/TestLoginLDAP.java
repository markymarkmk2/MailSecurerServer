/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.auth.AD.LDAPAuth;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author mw
 */
public class TestLoginLDAP extends AbstractCommand
{
    public TestLoginLDAP()
    {
        super("TestLoginLDAP");
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
            int ldap_port = (int)pt.GetLongValue("PO:");
            boolean ssl = pt.GetBoolean("SSL:");

            LDAPAuth la = new LDAPAuth(admin_name, admin_pwd, ldap_host, ldap_port, ssl);

            boolean ret = la.connect();
            if (!ret)
            {
                answer = "1: " + la.get_error_txt();
            }
            else
            {
                la.disconnect();
                answer = "0: ok";
            }

            return true;
        }

        answer = "1: Unknown subcommand: " + data;
        return false;
    }
}