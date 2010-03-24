/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Subscribe extends ImapCmd
{
    public Subscribe()
    {
        super( "subscribe");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        is.response(sid, true, "SUBSCRIBE completed");
        return 1;
    }

}
