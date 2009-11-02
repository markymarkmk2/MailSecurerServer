/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.mime.FileSource;
import SK.gnome.dwarf.mail.mime.MimePart;
import SK.gnome.dwarf.mail.mime.StreamMimePart;
import SK.gnome.dwarf.mail.store.MailFlag;
import SK.gnome.dwarf.mail.store.MailFolder;
import SK.gnome.dwarf.mail.store.MailMessage;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author mw
 */
public class SKMailMessage extends MailMessage
{

    long uid;
    File dummy;
    static StreamMimePart mimepart;
    static int uid_offset = 0;
    static FileSource fs;

    public SKMailMessage( MailFolder f, long uid )
    {
        super(f);
        this.uid = uid + uid_offset++;
        dummy = new File("Z:\\Mailtest\\test.eml");

        if (fs == null)
        {
            try
            {
                fs = new FileSource(dummy, 1000 * 1024);
            }
            catch (IOException ex)
            {
                LogManager.err_log("Err opening FileSource", ex);
            }
        }

        try
        {
            mimepart = new StreamMimePart(fs);
        }
        catch (MailException mailException)
        {
            mailException.printStackTrace();
        }
    }

    public SKMailMessage( MailFolder f, String file, long uid )
    {
        super(f);
        this.uid = uid + uid_offset++;
        dummy = new File("Z:\\Mailtest\\" + file);

        FileSource _fs = null;
        try
        {
            _fs = new FileSource(dummy, 1000 * 1024);
        }
        catch (IOException ex)
        {
            LogManager.err_log("Err opening FileSource", ex);
        }


        try
        {
            mimepart = new StreamMimePart(_fs);

        }
        catch (Exception mailException)
        {
            mailException.printStackTrace();
        }
    }

    @Override
    public long getUID()
    {
        return uid;
    }

    @Override
    public int getNumber()
    {
        return uid_offset;

    }

    @Override
    public long getSize() throws MailException
    {

        return dummy.length();
    }

    @Override
    public long getInternalDate() throws MailException
    {
        return dummy.lastModified();
    }

    @Override
    public boolean isExpunged()
    {
        return false;
    }

    @Override
    public boolean isSet( MailFlag arg0 ) throws MailException
    {
        System.out.println("Is set " + arg0.toString());
        return false;
    }

    @Override
    public void setFlag( MailFlag arg0, boolean arg1 ) throws MailException
    {
        System.out.println("Set " + arg0.toString() + " " + (arg1 ? "true" : "false"));
    }

    @Override
    public InputStream getInputStream() throws IOException, MailException
    {
        return new FileInputStream(dummy);
    }

    @Override
    public MimePart getMimePart() throws MailException
    {
        return mimepart;
    }

    String get_subject()
    {
        try
        {
            String[] r = mimepart.getHeader("Subject");
            if (r != null && r.length > 0)
            {
                return r[0];

            }
        }
        catch (Exception mimeException)
        {
            mimeException.printStackTrace();
        }

        return "??";
    }
}
