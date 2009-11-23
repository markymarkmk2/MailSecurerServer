/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;

/**
 *
 * @author mw
 */
class Range
{
    int start;
    int len;

    static Range range_factory(String s )
    {
        int sk_start  = s.indexOf('<');
        int sk_end =  s.indexOf('>');
        try
        {
            if (sk_start >= 0 && sk_end > sk_start)
            {
                String r_str = s.substring(sk_start + 1, sk_end);
                String[] r_arr = r_str.split("\\.");
                if (r_arr.length == 1)
                {
                    Range r = new Range();
                    r.start = 0;
                    r.len = Integer.parseInt(r_arr[0]);
                    return r;
                }
                if (r_arr.length == 2)
                {
                    Range r = new Range();
                    r.start = Integer.parseInt(r_arr[0]);
                    r.len = Integer.parseInt(r_arr[1]);
                    return r;
                }
            }
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        LogManager.err_log("Invalid IMAP range str: " + s);
        throw new IllegalArgumentException("Invalid Range");
    }

}
public class Fetch extends ImapCmd
{

    private static void write_header_fields( MWImapServer is, MWMailMessage msg, String orig_tag, String tag )
    {

                            //message
                            String header = msg.get_header_fields(tag);

                            is.rawwrite(orig_tag + " {" + header.length() + "}\r\n");
                            is.rawwrite(header);


    }

    private static void write_text( MWImapServer is, MWMailMessage msg, String orig_tag, String tag ) throws IOException, MessagingException
    {
        Range range = Range.range_factory(tag);
        InputStream stream = msg.mmail.getMsg().getInputStream();
        stream.skip(range.start);
        byte[] data = new byte[range.len];
        stream.read(data);
        String text = new String(data);
        is.rawwrite(orig_tag + " {" + text.length() + "}\r\n");
        is.rawwrite(text);
        is.close();
    }


    public Fetch()
    {
        super("fetch");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return raw_fetch(is, sid, parameter);
    }

    int raw_fetch( MWImapServer is, String sid, String par )
    {
        String part[] = imapsplit(par);
        String range = part[0];
        boolean success = true;
        while (!range.equals(""))
        {
            /* range could be 34:38,42:43,45 */
            String bereich = "";
            int i = range.indexOf(",");
            if (i < 0)
            {
                bereich = range;
                range = "";
            }
            else
            {
                bereich = range.substring(0, i);
                range = range.substring(i + 1);
            }
            int min = -1;
            int max = -1;
            i = bereich.indexOf(":");
            if (i < 0)
            {
                try
                {
                    min = max = Integer.parseInt(bereich);
                }
                catch (Exception e)
                {
                }
            }
            else
            {
                try
                {
                    min = Integer.parseInt(bereich.substring(0, i));
                }
                catch (Exception e)
                {
                }

                try
                {
                    max = Integer.parseInt(bereich.substring(i + 1));
                }
                catch (Exception e)
                {
                }

                if (min > 100000)
                {
                    min = 0; //Mop: ob das wohl richtig ist
                }
                if (max == 0)
                {
                    max = -1;
                }
            }
            try
            {
                //debug
                //System.out.println("call "+command+" from:"+min+" to:"+max);
                success &= fetch(is, min, max, 1, false, part);
                is.response(sid, success, "FETCH " + (success ? "completed" : "failed"));
                return success ? 0 : 1;
            }
            catch (IOException ex)
            {
                Logger.getLogger(Fetch.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (MessagingException ex)
            {
                Logger.getLogger(Fetch.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        is.response(sid, false, "FETCH failed");
        return 1;
    }

    static boolean fetch( MWImapServer is, int min, int max, int offset, boolean is_uid, String part[] ) throws IOException, MessagingException
    {
        int zaehler = 1;
        MailFolder mailfolder = is.mailfolder;

        for (int i = 0; i < mailfolder.anzMessages(); i++)
        {
            MailInfo msginfo = mailfolder.get_mail_message(i);
            if (msginfo == null)
            {
                continue;
            }
            int uid = msginfo.getUID();
            MWMailMessage msg = mailfolder.getMesg(i);

            if (is_uid)
            {
                if (uid < min)
                {
                    continue;
                }
                if (uid > max && max > -1)
                {
                    continue;
                }
            }
            else
            {
                if (max > 0)
                {
                    if (i > max)
                    {
                        break;
                    }
                }
            }

            is.rawwrite(RESTAG + (zaehler++) + " FETCH (");

            int size = msginfo.getRFC822size();
            String sflags = "(" + msginfo.getFlags() + ")"; //(\\Seen)

            boolean had_uid = false;
            boolean needs_space = false;

            for (int p = offset; p < part.length; p++)
            {
                String tags[] = imapsplit(part[p]);
                for (int x = 0; x < tags.length; x++)
                {

                    String orig_tag = tags[x].trim();
                    String tag = orig_tag.toLowerCase();


                    if (tag.equals("envelope"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;

                        String envelope = msginfo.getEnvelope();

                        is.rawwrite("ENVELOPE (" + envelope + ")");
                    }
                    if (tag.equals("flags"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;
                        is.rawwrite("FLAGS " + sflags);
                    }
                    else if (tag.equals("rfc822.header"))
                    {
                        //header
                        String theader = msginfo.getRFC822header();
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;
                        is.rawwrite("RFC822.HEADER {" + theader.length() + "}\r\n");
                        System.out.println("Writing Message header...");
                        try
                        {
                            is.s.getOutputStream().write(theader.getBytes());
                            System.out.print(theader);
                        }
                        catch (IOException iOException)
                        {
                        }
                    }
                    else if (tag.equals("rfc822.size"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;
                        is.rawwrite("RFC822.SIZE " + size);
                    }
                    else if (tag.equals("uid"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;
                        is.rawwrite("UID " + uid);
                        had_uid = true;
                    }
                    else if (tag.startsWith("body.peek["))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;

                        String peek_content = tag.substring(10);
                        if (peek_content.startsWith("header.fields"))
                        {
                            write_header_fields( is, msg, orig_tag, tag );
                        }
                        if (peek_content.startsWith("text"))
                        {
                            write_text( is, msg, orig_tag, tag );
                        }
                    }
                    else if (tag.equals("rfc822") || tag.equals("rfc822.peek") || tag.equals("body.peek[]"))
                    {
                        
                        //message
                        int tsize = msg.getRFC822size();
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;


                        ByteArrayOutputStream byas = new ByteArrayOutputStream();

//                            System.out.println("Writing Message body...");
                        //msg.getRFC822body(s.getOutputStream());
                        msg.getRFC822body(byas);
                        byte[] mdata = byas.toByteArray();
                        tsize = mdata.length;
                        is.rawwrite("" + tag.toUpperCase() + " {" + tsize + "}\r\n");
                        is.s.getOutputStream().write(mdata);
                        is.s.getOutputStream().flush();

                        System.out.print(byas.toString());

                    }
                    else if (tag.equals("internaldate"))
                    {
                       
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;
                        is.rawwrite("INTERNALDATE \"" + msg.get_internaldate() + "\"");
                    }
                }
            }
            if (!had_uid)
            {
                is.rawwrite(" UID " + uid);
                had_uid = true;
            }

            is.rawwrite(")\r\n");
        }

        return true;
    }
}
