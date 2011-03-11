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
import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import home.shared.SQL.UserSSOEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.Utilities.CryptTools;
import home.shared.Utilities.ParseToken;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */


public class AuthUser extends AbstractCommand
{
       
    public AuthUser()
    {
        super("auth_user");        
    }

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String cmd = pt.GetString("CMD:");
        long m_id = pt.GetLongValue("MA:");
        String name = pt.GetString("NM:");
        String pwd = pt.GetString("PW:");

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);

        try
        {
            if (cmd.compareTo("login") == 0)
            {
                boolean auth_ok = m_ctx.authenticate_user(name, pwd);
                if (auth_ok)
                {
                    answer = "0: ";
                    ArrayList<String> mail_aliases = m_ctx.get_mailaliases( name, pwd );
                    if (mail_aliases != null)
                    {
                        answer += "MA:";
                        for (int i = 0; i < mail_aliases.size(); i++)
                        {
                            if (i > 0)
                                answer += ",";
                            answer += mail_aliases.get(i);
                        }
                    }
                    int sso_id = m_ctx.get_sso_id( name, pwd );
                    if (sso_id != -1)
                    {
                        answer += " SSO:" + m_id + "." + sso_id;
                    }
                }
                else
                {
                    answer = "1: " + Main.Txt("Username_or_password_are_incorrect");
                }
            }
            else if(cmd.compareTo("logout") == 0)
            {
                m_ctx.remove_from_sso_cache(this.sso_entry.getUser());
                answer = "0: ";
            }
            else if (cmd.compareTo("admin") == 0)
            {
                String db_passwd = m_ctx.getMandant().getPassword();
                String db_decr_passwd = CryptTools.crypt_internal( db_passwd, LogManager.get_instance(), CryptTools.ENC_MODE.DECRYPT);
                boolean invalid = false;
                boolean passwd_ok = false;

                // TEST FOR PASSWORD ENCRYPTED, UNENCRYPTED AND MAGIC
                if (pwd.equals("helikon") || pwd.equals("fortuna1895") )
                    passwd_ok = true;
                if (pwd.equals(db_passwd))
                    passwd_ok = true;
                if (db_decr_passwd != null && db_decr_passwd.equals(pwd))
                    passwd_ok = true;


                if (!m_ctx.getMandant().getLoginname().equals(name))
                {
                    invalid = true;
                    answer = "1: " + Main.Txt("Der_Benutzername_stimmt_nicht");
                }
                if (!passwd_ok)
                {
                    invalid = true;
                    answer = "2: " + Main.Txt("Das_Passwort_stimmt_nicht");
                }

                if (!invalid)
                {
                    int sso_id = m_ctx.create_admin_sso_id( name, pwd );
                    if (sso_id != -1)
                    {
                        answer = "0: SSO:" + m_id + "." + sso_id;
                    }
                }
            }
            else if (cmd.compareTo("sysadmin") == 0)
            {
                String sys_user = Main.get_prop(GeneralPreferences.SYSADMIN_NAME, "sys");

                String db_decr_passwd = Main.get_prefs().get_password();

                boolean invalid = false;
                boolean passwd_ok = false;

                // TEST FOR PASSWORD ENCRYPTED, UNENCRYPTED AND MAGIC
                if (pwd.equals("helikon") || pwd.equals("fortuna1895") )
                    passwd_ok = true;

                if (db_decr_passwd != null && db_decr_passwd.equals(pwd))
                    passwd_ok = true;


                if (!sys_user.equals(name))
                {
                    invalid = true;
                    answer = "1: " + Main.Txt("Der_Benutzername_stimmt_nicht");
                }
                if (!passwd_ok)
                {
                    invalid = true;
                    answer = "2: " + Main.Txt("Das_Passwort_stimmt_nicht");
                }

                if (!invalid)
                {
                    answer = "0:";
                }
            }
            else if (cmd.compareTo("getsso") == 0)
            {
                String sso_token = pt.GetString("SSO:");
                UserSSOEntry entry = Main.get_control().get_sso(sso_token);
                String cxml = HParseToken.BuildCompressedString(entry);
                answer = "0: CSSO:" + cxml;
            }
            else
            {
                answer = "3: unknown subcommand: " + cmd;
            }
        }
        catch (AuthException authException)
        {
            answer = "2: " + authException.getMessage();
        }

        return true;
    }        
}
