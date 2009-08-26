/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import java.sql.Statement;


/**
 *
 * @author mw
 */
public class StatementEntry
{
    public Statement sta;
    public int id;
    public ConnEntry ce;

    StatementEntry( ConnEntry _ce, Statement _c, int _id )
    {
        sta = _c;
        id = _id;
        ce = _ce;
    }
}
