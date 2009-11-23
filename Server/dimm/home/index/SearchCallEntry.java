/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

/**
 *
 * @author mw
 */
public class SearchCallEntry
{

    SearchCall call;
    int id;

    public SearchCallEntry( SearchCall call, int id )
    {
        this.call = call;
        this.id = id;
    }

    String get_id()
    {
        return "sc" + id;
    }
}