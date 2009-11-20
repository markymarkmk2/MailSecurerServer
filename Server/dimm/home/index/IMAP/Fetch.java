/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.mail.MessagingException;

/**
 *
 * @author mw
 */
public class Fetch extends ImapCmd
{


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
            //debug
            //System.out.println("call "+command+" from:"+min+" to:"+max);


            success &= fetch(is, min, max, 1, false, part);
        }

        is.response(sid, success, "FETCH " + (success ? "completed" : "failed"));
        return success ? 0 : 1;
    }

    static boolean fetch( MWImapServer is, int min, int max, int offset, boolean is_uid, String part[] )
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
                    else if (tag.startsWith("body.peek[header.fields"))
                    {
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;

                        MWMailMessage msg = mailfolder.getMesg(i);
                        //message
                        String header = msg.get_header_fields(tag);

                        is.rawwrite(orig_tag + " {" + header.length() + "}\r\n");
                        is.rawwrite(header);
                    }
                    else if (tag.equals("rfc822") || tag.equals("rfc822.peek") || tag.equals("body.peek[]"))
                    {
                        MWMailMessage msg = mailfolder.getMesg(i);
                        //message
                        int tsize = msg.getRFC822size();
                        if (needs_space)
                        {
                            is.rawwrite(" ");
                        }
                        needs_space = true;


                        ByteArrayOutputStream byas = new ByteArrayOutputStream();

                        try
                        {
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
                        catch (IOException iOException)
                        {
                        }
                        catch (MessagingException messagingException)
                        {
                        }
                    }
                    else if (tag.equals("internaldate"))
                    {
                        MWMailMessage msg = mailfolder.getMesg(i);
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
