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

        long m_id = pt.GetLongValue("MA:");
        String name = pt.GetString("NM:");
        String pwd = pt.GetString("PW:");

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                
        try
        {
            boolean auth_ok = m_ctx.authenticate_user(name, pwd);
            if (auth_ok)
            {
                answer = "0: ok";
            }
            else
            {
                answer = "1: " + Main.Txt("Username_or_password_are_incorrect");
            }
        }
        catch (AuthException authException)
        {
            answer = "2: " + authException.getMessage();
        }


        return true;
    }        
}
