/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;


class LengthOutputStream extends OutputStream
{
    long length;
    long act_length;
    OutputStream os;
    boolean was_flushed = false;
    boolean was_toolong = false;
    boolean was_flushed()
    {
        return was_flushed;
    }
    boolean was_toolong()
    {
        return was_toolong;
    }

    public LengthOutputStream( long length, OutputStream os )
    {
        this.length = length;
        this.os = os;
        act_length = 0;
    }

    @Override
    public void write( int b ) throws IOException
    {
        if (act_length < length)
        {
            os.write(b);
            act_length++;
        }
        else
            was_toolong = true;
    }

    @Override
    public void write( byte[] b, int off, int len ) throws IOException
    {
        if (act_length + len <= length)
        {
            try
            {
                os.write(b, off, len);
            }
            catch (IOException iOException)
            {
                System.out.println("Error during los write (" + len + "," + act_length + "): " + iOException.getMessage());
            }
            act_length += len;
            return;
        }
        was_toolong = true;

        if (act_length >= length)
            return;

        int rest = (int)(length - act_length);
        os.write(b, off, rest);
        act_length += rest;
    }

    @Override
    public void write( byte[] b ) throws IOException
    {
        write(b, 0, b.length);
    }

    @Override
    public void flush() throws IOException
    {
        int rest = (int)(length - act_length);
        for (int i = 0; i < rest; i++)
        {
            was_flushed = true;
            os.write((byte)' ');
        }
        os.flush();
    }
}
/**
 *
 * @author mw
 */
class Range
{

    int start;
    int len;

    static Range range_factory( String s )
    {
        int sk_start = s.indexOf('<');
        int sk_end = s.indexOf('>');
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

    private static void write_header_fields( MWImapServer is, MWMailMessage msg, String orig_tag, String tag ) throws IOException
    {

        //message
        String header = msg.get_header_fields(tag);
        byte[] hdata = header.getBytes();
        String return_tag = orig_tag.replace(".PEEK[", "[");

        is.rawwrite(return_tag + " {" + hdata.length + "}\r\n");
        is.s.getOutputStream().write(hdata);
        //is.rawwrite(header);


    }

    private static void write_text( MWImapServer is, MWMailMessage msg, String orig_tag, String tag ) throws IOException, MessagingException
    {
        Range range = Range.range_factory(tag);
        InputStream stream = msg.mmail.getMsg().getInputStream();
        stream.skip(range.start);
        byte[] data = new byte[range.len];
        int rlen = stream.read(data);

        stream.close();

        String return_tag = orig_tag;
        return_tag = return_tag.replace(".PEEK[", "[");

        if (rlen < range.len)
        {
            int idx_range = return_tag.indexOf('<');
            if (idx_range > 0)
            {
                return_tag = return_tag.substring(0, idx_range) + '<' + range.start + '>';
            }
        }

        OutputStream os = is.s.getOutputStream();

        is.rawwrite( return_tag + " {" + rlen + "}\r\n");
        os.write(data, 0, rlen);
//        is.rawwrite(return_tag + " {" + rlen + "}\r\n");
//        is.rawwrite( text );
        //is.rawwrite(data, 0, rlen);
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

        resetCounter();
        
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
                    long l = Long.parseLong(bereich.substring(0, i));
                    min = (int) l;
                }
                catch (Exception e)
                {
                }

                try
                {
                    long l = Long.parseLong(bereich.substring(i + 1));
                    max = (int) l;
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
                success &= fetch(this, is, min, max, 1, false, part);
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


    static boolean fetch( ImapCmd cmd, MWImapServer is, int min, int max, int offset, boolean is_uid, String part[] ) throws IOException, MessagingException
    {
        
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

            is.rawwrite(RESTAG + cmd.getNextCounter() + " FETCH (");

            long size = msginfo.getRFC822size();
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

                    System.out.print("<<got tag " + tag + ">>");


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
                        byte[] hdata = theader.getBytes();
                        is.rawwrite("RFC822.HEADER {" + hdata.length + "}\r\n");
                        //System.out.println("Writing Message header...");
                        try
                        {
                            is.s.getOutputStream().write( hdata );
                          //  System.out.print(theader);
                        }
                        catch (IOException iOException)
                        {
                            System.out.print(iOException.getLocalizedMessage());
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
                    else if (tag.startsWith("body.peek[") && !tag.equals("body.peek[]"))
                    {

                        String peek_content = tag.substring(10);
                        if (peek_content.startsWith("header.fields"))
                        {
                            if (needs_space)
                            {
                                is.rawwrite(" ");
                            }
                            needs_space = true;
                            write_header_fields(is, msg, orig_tag, tag);
                        }
                        if (peek_content.startsWith("text"))
                        {

                            if (needs_space)
                            {
                                is.rawwrite(" ");
                            }
                            needs_space = true;

                            write_text(is, msg, orig_tag, tag);
                        }

                    }
                    else if (tag.equals("rfc822") || tag.equals("rfc822.peek") || tag.equals("body.peek[]"))
                    {

                        //message
                        long tsize = msg.getRFC822size();
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;


                      /*  ByteArrayOutputStream byas = new ByteArrayOutputStream();

//                            System.out.println("Writing Message body...");
                        //msg.getRFC822body(s.getOutputStream());
                        msg.getRFC822body(byas);
                        byte[] mdata = byas.toByteArray();
                        tsize = mdata.length;*/
                        is.rawwrite("" + tag.toUpperCase() + " {" + tsize + "}\r\n");

                        LengthOutputStream los = new LengthOutputStream(tsize, is.s.getOutputStream());
                        msg.getRFC822body( los );
                        los.flush();
/*                        is.s.getOutputStream().write(mdata);*/
                        //is.s.getOutputStream().flush();

                        //System.out.print(byas.toString());

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
                    else if (tag.equals("bodystructure"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;

                        String structure = msginfo.getBodystructure();

                        is.rawwrite("BODYSTRUCTURE (" + structure + ")");
                    }
                }
            }
            if (!had_uid)
            {
                is.rawwrite(" UID " + uid);
                had_uid = true;
            }
            
            // KEEP MEM FOOTPRINT LOW
            msg.unload_rfc_mail();

            is.rawwrite(")\r\n");
        }

        return true;
    }
}
