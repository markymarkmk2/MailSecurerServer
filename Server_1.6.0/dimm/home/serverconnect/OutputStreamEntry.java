/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import java.io.File;
import java.io.OutputStream;

/**
 *
 * @author mw
 */
public class OutputStreamEntry
{

    public OutputStream os;
    public int id;
    public File file;

    public OutputStreamEntry( OutputStream _os, File _file, int _id )
    {
        os = _os;
        file = _file;
        id = _id;
    }
    
}