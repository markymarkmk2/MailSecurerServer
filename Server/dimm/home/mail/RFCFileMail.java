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
import java.util.Date;

/**
 *
 * @author mw
 */
public class RFCFileMail extends RFCGenericMail
{
    File msg;

    public RFCFileMail( File _msg, Date _date )
    {
        super(_date);
        msg = _msg;
    }
    public RFCFileMail( File _msg )
    {
        this( _msg, new Date() );
    }

    @Override
    public long get_length()
    {
        return msg.length();
    }

    @Override
    public InputStream open_inputstream() throws FileNotFoundException
    {
        return new BufferedInputStream( new FileInputStream( msg));
    }



   
    @Override
    public void delete()
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

    @Override
    public void move_to( File index_msg ) throws IndexException
    {
        try
        {
            if (msg.renameTo(index_msg))
                msg = index_msg;
        }
        catch (Exception exc)
        {
            LogManager.err_log("Cannot rename mail for index: ", exc);
            throw new IndexException("Cannot rename mail for index: " + exc.getMessage());
        }
    }

}
