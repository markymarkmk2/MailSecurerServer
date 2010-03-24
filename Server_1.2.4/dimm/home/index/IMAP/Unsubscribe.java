/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Unsubscribe extends ImapCmd
{
    public Unsubscribe()
    {
        super( "unsubscribe");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        is.response(sid, true, "UNSUBSCRIBE completed");
        return 1;
    }
}
