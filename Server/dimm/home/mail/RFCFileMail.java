/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mail;

import java.io.File;
import java.text.ParseException;
import java.text.ParsePosition;
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


    public RFCFileMail( File _msg, Date _date )
    {
        msg = _msg;
        date = _date;
    }
    public RFCFileMail( File _msg )
    {
        this( _msg, new Date() );
    }

    public File getFile()
    {
        return msg;
    }
    public Date getDate()
    {
        return date;
    }

    synchronized public File create_unique_mailfile( String parent_path)
    {
        File trg_file = null;

        do
        {
            trg_file = new File( parent_path + "/data" + sdf.format(date) );

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
    public static long get_time_from_mailfile( String path)
    {
        try
        {
            Date d = sdf.parse(path);
            return d.getTime();
        }
        catch (ParseException parseException)
        {
        }
        return 0;
    }

    public String get_unique_id()
    {
        return sdf.format(date);
    }


}
