/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Examine extends ImapCmd
{
    public Examine()
    {
        super( "examine");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        return Select.select( is, sid, parameter );
    }

}
