/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mail;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author mw
 */
public class RFCFileMail
{
    File msg;
    Date date;


    static SimpleDateFormat sdf = new SimpleDateFormat("/yyyy/MM/dd/HHmmss.SSS");


    public RFCFileMail( File _msg )
    {
        msg = _msg;
        date = new Date();
    }

    public File getFile()
    {
        return msg;
    }

    synchronized public File create_unique_mailfile( String parent_path)
    {
        File trg_file = null;

        do
        {
            trg_file = new File( parent_path + sdf.format(date) );

            if (!trg_file.exists())
            {
                break;
            }
            try
            {
                Thread.sleep(5);
            }
            catch (InterruptedException ex)
            {}

        } while (true);

        return trg_file;
    }

}
