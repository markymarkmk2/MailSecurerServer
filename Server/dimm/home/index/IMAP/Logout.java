/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Logout extends ImapCmd
{
    public Logout()
    {
        super( "logout");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return logout( is, sid, parameter );
    }

    private int logout( MWImapServer is, String sid, String par )
    {
        if (is.m_ctx != null)
        {
            //Alles Ok
            is.response(sid, true, "User " + is.m_ctx.getMandant().getName() + " logged out");
            is.m_ctx = null;
            return 0;
        }
        is.response(sid, false, "LOGIN failed");
        return 1;
    }
}
