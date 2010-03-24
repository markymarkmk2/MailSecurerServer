/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import java.sql.ResultSet;


/**
 *
 * @author mw
 */
public class ResultEntry
{
    public ResultSet rs;
    public int id;
    public ConnEntry ce;

    public ResultEntry( ConnEntry _ce, ResultSet _c, int _id )
    {
        rs = _c;
        id = _id;
        ce = _ce;
    }
}
