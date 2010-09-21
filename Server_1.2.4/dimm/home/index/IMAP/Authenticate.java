/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.SQL.UserSSOEntry;
import java.io.IOException;
import org.apache.commons.codec.binary.Base64;
/**
 *
 * @author mw
 */
public class Authenticate extends ImapCmd
{
    public Authenticate()
    {
        super( "authenticate");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        return login( is, sid, parameter );
    }

    private int login( ImapsInstance is, String sid, String par )
    {

        is.write("+");
        String login_param;
        try
        {
            login_param = is.read();
        }
        catch (IOException iOException)
        {
            is.response(sid, false, "LOGIN failed");
            return 1;
        }

        String name = new String(Base64.decodeBase64(login_param.getBytes()));
        String auth[] = name.split("\0");
        if (auth != null && auth.length >= 3)
        {
            String user = auth[1];
            String pwd = auth[2];

            try
            {
                if (is.get_konto() != null)
                {
                    if (is.get_konto().user.compareTo(user) == 0)
                    {
                        is.response(sid, true, "User " + is.m_ctx.getMandant().getName() + " logged in");
                        return 0;
                    }
                }

                if (is.m_ctx.authenticate_user(user, pwd))
                {
                    //Alles Ok
                    UserSSOEntry sso_entry = is.m_ctx.get_from_sso_cache(user, pwd);
                    is.set_konto(  new MailKonto(is, user, pwd, is.m_ctx, is.m_ctx.get_mailaliases(user, pwd), sso_entry) );
                    is.response(sid, true, "User " + is.m_ctx.getMandant().getName() + " logged in");
                    return 0;
                }
            }
            catch (AuthException authException)
            {
                LogManager.msg_imaps(LogManager.LVL_ERR,"IMAP Login failed", authException);
            }
        }

        is.response(sid, false, "LOGIN failed");
        return 1;
    }
}
