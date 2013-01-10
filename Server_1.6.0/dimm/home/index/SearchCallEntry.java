/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import home.shared.SQL.UserSSOEntry;

/**
 *
 * @author mw
 */
public class SearchCallEntry
{

    public static final long MAX_AGE_MS = 86400*1000l;
    private SearchCall call;
    private int id;
    private UserSSOEntry ssoc;
    private long created;

    public SearchCallEntry( UserSSOEntry ssoc, SearchCall call, int id )
    {
        this.call = call;
        this.id = id;
        this.ssoc = ssoc;
        created = System.currentTimeMillis();
    }
    int getId()
    {
        return id;
    }

    String get_SceId()
    {
        return "sc" + id;
    }

    public UserSSOEntry getSsoc()
    {
        return ssoc;
    }
    public SearchCall getCall()
    {
        return call;
    }
    public boolean isExpired()
    {
        long age = System.currentTimeMillis() - created;
        return  (age > MAX_AGE_MS);
    }
    
}