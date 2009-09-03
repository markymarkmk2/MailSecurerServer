/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mail;

import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.ParseException;
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

    public long get_length()
    {
        return msg.length();
    }

    public InputStream open_inputstream() throws FileNotFoundException
    {
        return new BufferedInputStream( new FileInputStream( msg));
    }

    public Date getDate()
    {
        return date;
    }

    synchronized public File create_unique_mailfile( String parent_path)
    {
        File trg_file = null;
        int retries = 100;
        do
        {
            trg_file = new File( get_mailpath_from_time(parent_path, date) );

            if (!trg_file.exists())
            {
                File parent = trg_file.getParentFile();
                if (!parent.exists())
                    parent.mkdirs();
                
                break;
            }
            trg_file = null;
            try
            {
                Thread.sleep(5);
            }
            catch (InterruptedException ex)
            {}

        } while (true && retries-- > 0);

        return trg_file;
    }
    public static long get_time_from_mailfile( String path) throws ParseException
    {
        Date d = sdf.parse(path);
        return d.getTime();
    }

    public static String get_mailpath_from_time( String parent_path, long time)
    {
        Date d = new Date(time);
        return get_mailpath_from_time(parent_path, d);
    }
    public static String get_mailpath_from_time( String parent_path, Date d)
    {
        String trg_file = parent_path + "/data" + sdf.format(d);
        return trg_file;
    }


    public String get_unique_id()
    {
        return sdf.format(date);
    }

    public void delete_msg()
    {
        try
        {
            msg.delete();
        }
        catch (Exception exc)
        {
            LogManager.err_log("Cannot delete mail after index: ", exc);
        }
    }

    public void rename_to( File index_msg ) throws IndexException
    {
        try
        {
            msg.renameTo(index_msg);
        }
        catch (Exception exc)
        {
            LogManager.err_log("Cannot rename mail for index: ", exc);
            throw new IndexException("Cannot rename mail for index: " + exc.getMessage());
        }
    }


}
