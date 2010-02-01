/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import dimm.home.mailarchiv.UserSSOEntry;

/**
 *
 * @author mw
 */
public class SearchCallEntry
{

    SearchCall call;
    int id;
    UserSSOEntry ssoc;

    public SearchCallEntry( UserSSOEntry ssoc, SearchCall call, int id )
    {
        this.call = call;
        this.id = id;
        this.ssoc = ssoc;
    }

    String get_id()
    {
        return "sc" + id;
    }

    public UserSSOEntry getSsoc()
    {
        return ssoc;
    }
    
}