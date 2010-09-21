/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Close extends ImapCmd
{
    public Close()
    {
        super( "close");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        is.response(sid, true, "CLOSE completed");
        return 0;
    }
    
}
