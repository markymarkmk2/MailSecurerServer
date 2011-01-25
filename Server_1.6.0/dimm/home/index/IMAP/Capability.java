/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Capability extends ImapCmd
{
    public Capability()
    {
        super( "capability");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        return capability( is, sid, parameter );
    }

    private int capability( ImapsInstance is, String sid, String par )
    {
        is.response("CAPABILITY IMAP4 IDLE LOGIN AUTH=PLAIN");
//        response("CAPABILITY IMAP4 IDLE LOGIN");
        is.response(sid, true, "CAPABILITY completed");
        return 0;
    }
}
