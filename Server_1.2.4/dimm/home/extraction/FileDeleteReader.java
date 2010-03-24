/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.extraction;

import dimm.home.mailarchiv.TempFileHandler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author mw
 */
public class FileDeleteReader extends FileReader
{
    File del_file ;
    TempFileHandler tfh;
    public FileDeleteReader( TempFileHandler tfh, File f ) throws FileNotFoundException
    {
        super(f);
        del_file = f;
        this.tfh = tfh;
    }

    @Override
    public void close() throws IOException
    {
        super.close();
        if (tfh != null)
        {
            tfh.delete( del_file );
        }
        else
        {
            if (del_file.exists())
                del_file.delete();
        }
    }


}