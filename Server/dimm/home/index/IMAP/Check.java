/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Check extends ImapCmd
{
     public Check()
    {
        super( "check");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        is.response(sid, true, "CHECK completed");
        return 0;
    }
    
}
