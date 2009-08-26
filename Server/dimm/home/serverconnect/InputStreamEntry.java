/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import java.io.File;
import java.io.InputStream;

/**
 *
 * @author mw
 */
public class InputStreamEntry
{

    public InputStream is;
    public int id;
    public File file;

    public InputStreamEntry( InputStream _is, File _file, int _id )
    {
        is = _is;
        file = _file;
        id = _id;
    }
}