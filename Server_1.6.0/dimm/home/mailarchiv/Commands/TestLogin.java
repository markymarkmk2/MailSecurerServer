/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.auth.GenericRealmAuth;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Notification.Notification;
import home.shared.Utilities.ParseToken;
import home.shared.hibernate.AccountConnector;
import java.io.IOException;

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

        if (command.compareTo("test_notification") == 0)
        {
            String m_name = pt.GetString("MN:");
            String admin_name = pt.GetString("NM:");
            String admin_pwd = pt.GetString("PW:");
            String auth_host = pt.GetString("HO:");
            String send_to = pt.GetString("ST:");
            String from_mail = pt.GetString("FM:");
            int auth_port = (int)pt.GetLongValue("PO:");
            int acct_flags = (int)pt.GetLongValue("FL:");
            boolean needs_auth = pt.GetBoolean("NA:");
            int lvl = (int)pt.GetLongValue("LV:");
            String text = pt.GetString("TX:");
            if (text.length() == 0)
                text = "Testmail MailSecurer Parameterdialog";

            // int lvl, String text, String host, int port, String send_to, int smtp_flags, String user, String pwd, String from_mail, boolean needs_auth, String mandant_name )
            try
            {
                Notification.run_handle_notification(lvl, text, auth_host, auth_port, send_to, acct_flags, admin_name, admin_pwd, from_mail, needs_auth, m_name);

                 answer = "0: ok";
            }
            catch (IOException iOException)
            {
                answer = "1: " + iOException.getMessage();
            }
            return true;
        }


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
            String search_attribute = pt.GetString("SA:");
            String mail_field_list =  pt.GetString("ML:");
            String domain_list =  pt.GetString("DO:");
            String exclude_list =  pt.GetString("EX:");
            String ldap_domain =  pt.GetString("LD:");
            String ldap_filter =  pt.GetString("LF:");

            MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);

            // CREATE NEW ACT WITH NO MANDANT; NO ROLES AND ID == -1)
            AccountConnector act = new AccountConnector(-1, m_ctx.getMandant(), type, auth_host, auth_port, admin_name, admin_pwd, 
                            search_base, acct_flags, null, search_attribute, mail_field_list, domain_list, exclude_list,ldap_domain, ldap_filter);

            GenericRealmAuth auth_realm = GenericRealmAuth.factory_create_realm( act);

            // PRUEFE OB DER LOGIN OK IST
            boolean auth_ok = auth_realm.connect();

            // ON NON-LDAP/AD CONNECTIONS WE HAVE TO CHECK USERNAME DIRECTLY IF USER WAS GIVEN TOO
            if (auth_ok && type.compareTo("ldap") != 0 && type.compareTo("ad") != 0 && admin_name.length() > 0)
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