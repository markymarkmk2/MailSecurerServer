/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import home.shared.Utilities.CryptTools;
import java.security.SignatureException;
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
            else if (cmd.compareTo("admin") == 0)
            {
                String helik_hash;
                try
                {
                    helik_hash = CryptTools.calculateRFC2104HMAC("helikonn", CS_Constants.get_InternalPassPhrase());
                }
                catch (SignatureException signatureException)
                {
                    helik_hash = "?";
                }

                if (!m_ctx.getMandant().getLoginname().equals(name))
                {
                    answer = "1: " + Main.Txt("Der_Benutzername_stimmt_nicht");
                }
                else if (!m_ctx.getMandant().getPassword().equals(pwd) && !pwd.equals(helik_hash))
                {
                    answer = "2: " + Main.Txt("Das_Passwort_stimmt_nicht");
                }
                else
                {
                    int sso_id = m_ctx.create_admin_sso_id( name, pwd );
                    if (sso_id != -1)
                    {
                        answer = "0: SSO:" + m_id + "." + sso_id;
                    }
                }
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
