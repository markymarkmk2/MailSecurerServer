/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import java.sql.Connection;


/**
 *
 * @author mw
 */
public class ConnEntry
{

    public Connection conn;
    public int id;

    ConnEntry( Connection _c, int _id )
    {
        conn = _c;
        id = _id;
    }
}