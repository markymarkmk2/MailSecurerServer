/*  MailMessage implementation
 *  Copyright (C) 2006 Dirk friedenberger <projekte@frittenburger.de>
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package dimm.home.index.IMAP;

import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCMimeMail;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;

public class MWMailMessage implements MailInfo
{
    String uuid;
    String header = null;
    Vector mesg = null;
    int bodysize = 0;
    int mesgsize = 0;
    boolean ishead = true;
    int uid = 0;
    String messageid = null;
    MailKonto parent = null;
    MailFolder mailfile = null;
    boolean read = false;

    RFCFileMail rfc;
    RFCMimeMail mmail;
    SimpleDateFormat internaldate_sdf;

    public MWMailMessage()
    {
        throw new NullPointerException("Constructor not supported");
    }
/*
    public MailMessage( MailFile mailfile, MailKonto parent )
    {
        if (parent == null)
        {
            throw new NullPointerException("parent == null");
        }
        if (mailfile == null)
        {
            throw new NullPointerException("mailfile == null");
        }
        this.parent = parent;
        this.mailfile = mailfile;
    }
*/
    public MWMailMessage( MailFolder mailfile, MailKonto parent, String messageid, int uid )
    {
        this.parent = parent;
        this.mailfile = mailfile;
        this.messageid = messageid;
        this.uuid = messageid;
        this.uid = uid;

        //21-Apr-2009 16:50:44 +0100
        internaldate_sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z");

        rfc = new RFCFileMail(new File( messageid ), false);
        mmail = new RFCMimeMail();
        try
        {
            mmail.parse(rfc);
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

   

    @Override
    public int getUID()
    {
        return uid;
    }

    @Override
    public String getMID()
    {
        try
        {
            return mmail.getMsg().getMessageID();
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    @Override
    public String getFlags()
    {
        return "";
        //return parent.get(MailKonto.FLAGS, mailfile.getKey(), messageid) + " " + parent.get(MailKonto.FLAGS, MailKonto.GLOBAL, messageid);
    }
    //nur fuers abspeichern 
    //public void add(char [] buffer,int size)
    //{
    // header += new String(buffer,0,size);
    //ist uninterresant das alles in den header gespeichert wird, weill eh erst abgespeichert wird;
    //}
    Hashtable h = new Hashtable();


    public final static String FROM = "from";
    public final static String TO = "to";
    public final static String SUBJECT = "subject";
    public final static String Keys[] = new String[]
    {
        FROM, TO, SUBJECT
    };

    public String get( String key )
    {
        try
        {
            return mmail.getMsg().getHeader(key, null);
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

     @Override
    public int getRFC822size()
    {
        return (int)rfc.get_length();
    }

 
    @Override
    public String getRFC822header()
    {
        String ret = "";
        try
        {

            Enumeration en = mmail.getMsg().getAllHeaderLines();
            while (en.hasMoreElements())
            {
                ret += en.nextElement().toString();
                ret += "\n";
            }

        }
        catch (MessagingException ex)
        {
            Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ret;
    }

  
    public void getRFC822body( OutputStream os) throws IOException, MessagingException
    {
        mmail.getMsg().writeTo(os);
    }

    public static String getMessageId( String line )
    {
        String mid = null;
        if (line.trim().toLowerCase().startsWith("message-id"))
        {
            mid = line;
            int s = mid.indexOf("<");
            if (-1 < s)
            {
                mid = mid.substring(s + 1);
            }
            s = mid.lastIndexOf(">");
            if (-1 < s)
            {
                mid = mid.substring(0, s);
            }
        }
        return mid;
    }

    String get_internaldate()
    {
        Date d = null;

        try
        {
            mmail.getMsg().getSentDate();
            if (d == null)
            {
                d = mmail.getMsg().getReceivedDate();

            }
        }
        catch (MessagingException messagingException)
        {
        }
        
        if ( d == null)
            d = new Date();

        return internaldate_sdf.format(d);
    }

    /*
     * date, subject, from, sender, reply-to, to, cc, bcc,
         in-reply-to, and message-id.  The date, subject, in-reply-to,
         and message-id fields are strings.  The from, sender, reply-to,
         to, cc, and bcc fields are parenthesized lists of address
         structures.

ENVELOPE ("Tue, 21 Apr 2009 17:50:44 +0200" "Re: bbb" 
     (("Journal" NIL "exjournal" "dimm.home")) (("Journal" NIL "exjournal" "dimm.home")) (("Journal" NIL "exjournal" "dimm.home")) (("Mark Williams" NIL "mw" "dimm.home")) NIL (("Piet Borowski" NIL "pb" "dimm.home")) "<87823F8FD45C47EA8448BFD92F9F80A6@STOREVISTA>" NIL)         


     * */
    @Override
    public String getEnvelope()
    {
        StringBuffer sb = new StringBuffer();

        try
        {
            sb.append("\"");
            sb.append(get_internaldate());
            sb.append("\"");
            sb.append("\"");
            sb.append(mmail.getMsg().getSubject());
            sb.append("\"");
        }
        catch (MessagingException messagingException)
        {
            messagingException.printStackTrace();
        }


        return sb.toString();
    }

    String get_header_fields( String tag )
    {
        StringBuffer sb = new StringBuffer();

        int idx = tag.indexOf('(');
        int last_idx = tag.lastIndexOf(')');
        String header_list = tag.substring(idx + 1, last_idx);
        StringTokenizer str = new StringTokenizer( header_list, " ");
        try
        {
            while (str.hasMoreElements())
            {
                String hdr = str.nextToken();
                String val = mmail.getMsg().getHeader(hdr, ",");
                if (val != null)
                {
                    sb.append(hdr + ": " + val + "\r\n");
                }
            }
        }
        catch (MessagingException messagingException)
        {
            messagingException.printStackTrace();
        }

        return sb.toString();
    }
}
