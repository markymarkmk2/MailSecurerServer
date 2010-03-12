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

import dimm.home.index.SearchResult;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCMimeMail;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;

public class MWMailMessage implements MailInfo
{
    String uuid;
    String header = null;
    Vector mesg = null;
    int bodysize = 0;
    int mesgsize = 0;
    boolean ishead = true;
    int uid = 0;
    //String messageid = null;
    MailKonto parent = null;
    MailFolder mailfile = null;
    boolean read = false;

    
    RFCMimeMail mmail;
    SimpleDateFormat internaldate_sdf;
    RFCGenericMail rfc;
    SearchResult sc_result;

    private MWMailMessage()
    {
    }
/*
    public MWMailMessage( MailFolder mailfile, MailKonto parent, String messageid, int uid )
    {
        this.parent = parent;
        this.mailfile = mailfile;
       // this.messageid = messageid;
        this.uuid = messageid;
        //this.uid = uid;

        //21-Apr-2009 16:50:44 +0100
        internaldate_sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z");

        rfc = new RFCFileMail(new File( messageid ), false);
       
    }*/
    /*
    public MWMailMessage( MailFolder mailfile, MailKonto parent, RFCMimeMail mm, SearchResult sc )
    {
        this.parent = parent;
        this.mailfile = mailfile;

        this.uuid = sc.getUuid();
        //this.uid = uid;
        sc_result = sc;

        //21-Apr-2009 16:50:44 +0100
        internaldate_sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z");

        mmail = mm;

    }*/
    void unload_rfc_mail()
    {
        mmail = null;
    }

    boolean try_load_mail()
    {
        Thread t = new Thread("load_rfc_mail")
        {

            @Override
            public void run()
            {
                mmail = new RFCMimeMail();
                try
                {
                    InputStream is = rfc.open_inputstream();
                    mmail.parse(is);
                    is.close();
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

        };
        try
        {
            t.start();
            t.join(120 * 1000);
            return true;
        }
        catch (Exception interruptedException)
        {
            LogManager.err_log_fatal("Error while loading mail file", interruptedException);
        }
        return false;

    }
    void load_rfc_mail() throws IOException
    {
        if (mmail != null)
            return;

        int retries = 5;
        while (retries > 0 && !try_load_mail())
        {
            retries--;
        }
        if (mmail == null)
            throw new IOException("Cannot lead mail");

    }

    public MWMailMessage( MailFolder mailfile, MailKonto parent, RFCGenericMail rfc,  int uid, SearchResult sc  )
    {
        this.parent = parent;
        this.mailfile = mailfile;

        if (sc != null)
            this.uuid = sc.getUuid();
        else
            this.uuid = Integer.toString(uid);
        
        this.uid = uid;
        this.rfc = rfc;
        this.sc_result = sc;

        //21-Apr-2009 16:50:44 +0100
        internaldate_sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z");
       

    }



    @Override
    public int getUID()
    {
        return uid;
    }

    @Override
    public String getMID() throws IOException
    {
        load_rfc_mail();

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

/*
    public final static String FROM = "from";
    public final static String TO = "to";
    public final static String SUBJECT = "subject";
    public final static String Keys[] = new String[]
    {
        FROM, TO, SUBJECT
    };
*/
    public String get( String key ) throws IOException
    {
        load_rfc_mail();
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
    public long getRFC822size()
    {
         if (sc_result != null)
            return sc_result.getSize();

        try
        {
            load_rfc_mail();

            int s = mmail.getMsg().getSize();
            if (s == -1)
            {
                ByteArrayOutputStream byas = new ByteArrayOutputStream();
                mmail.getMsg().writeTo(byas);
                byas.close();
                s = byas.size();
            }
            int header_size = 0;
            Enumeration en = mmail.getMsg().getAllHeaderLines();
            while (en.hasMoreElements())
            {
                header_size += en.nextElement().toString().length() + 1; // '\n'
            }
            return s + header_size;

        }
        catch (Exception messagingException)
        {
            messagingException.printStackTrace();
        }
        return -1;
    }

 
    @Override
    public String getRFC822header() throws IOException
    {
        load_rfc_mail();
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
        load_rfc_mail();
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
        Date d = new Date();
        if (sc_result != null)
            d.setTime( sc_result.getTime() );
        else
        {
            try
            {
                if (mmail.getMsg().getReceivedDate() != null)
                {
                    d = mmail.getMsg().getReceivedDate();

                }
                else if (mmail.getMsg().getSentDate() != null)
                {
                    d = mmail.getMsg().getSentDate();

                }
            }
            catch (MessagingException messagingException)
            {
            }
        }
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
    public String getEnvelope() throws IOException
    {
        load_rfc_mail();
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

    String get_header_fields( String tag ) throws IOException
    {
        load_rfc_mail();
        StringBuffer sb = new StringBuffer();

        if (tag == null)
        {
            try
            {
                Enumeration e = mmail.getMsg().getAllHeaderLines();
                while (e.hasMoreElements())
                {
                    Object o = e.nextElement();
                    if (o instanceof Header)
                    {
                        Header header_entry = (Header) o;
                        sb.append(header_entry.getName());
                        sb.append("=");
                        sb.append(header_entry.getValue());
                        sb.append("\r\n");
                    }
                    if (o instanceof String)
                    {
                        sb.append(o.toString());
                        sb.append("\r\n");
                    }
                }
            }
            catch (MessagingException ex)
            {
                ex.printStackTrace();
                Logger.getLogger(MWMailMessage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else
        {


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
        }
        return sb.toString();
    }

    void add_attr( StringBuffer sb, int attr )
    {
        char last_char  = 0;
        if (sb.length() > 0)
            last_char = sb.charAt(sb.length() - 1);

        boolean needs_space = false;

        // INSIDE PARENTHESIS NO SPACE
        if (last_char != '(')
            needs_space = true;

        if (needs_space)
            sb.append(' ');

        sb.append(attr);
    }
    void add_attr( StringBuffer sb, String attr )
    {
        char last_char  = 0;
        if (sb.length() > 0)
            last_char = sb.charAt(sb.length() - 1);

        boolean needs_space = false;

        // INSIDE PARENTHESIS NO SPACE
        if (last_char != '(' && (attr == null || !attr.equals(")")) && sb.length() > 0 )
            needs_space = true;

        // NO SPACE BETWEEN BRACKET CLOSE / OPEN
        if (attr != null && attr.equals("(") && last_char == ')')
            needs_space = false;

        if (needs_space)
            sb.append(' ');

        if (attr == null || attr.equals("(" ) || attr.equals(")" ))
        {
            if (attr ==null)
                attr = "NIL";

            sb.append(attr);
        }
        else
        {
            sb.append("\"");
            sb.append(attr);
            sb.append("\"");
        }
    }
    private String get_content_type( Part p ) throws MessagingException
    {
        String mt = p.getContentType();
        if (mt != null && mt.length() > 0)
        {
            String[] cts = mt.split("/");
            return cts[0];
        }
        return null;
    }
    private String get_content_subtype( Part p ) throws MessagingException
    {
        String mt = p.getContentType();
        if (mt != null && mt.length() > 1)
        {
            String[] cts = mt.split("[/;\" ]");
            return cts[1];
        }
        return null;
    }
    private String get_content_attributes( Part p ) throws MessagingException
    {
        StringBuffer sb = new StringBuffer();

        String mt = p.getContentType();
        int atr_idx = mt.indexOf(';');
        if (atr_idx == -1)
            atr_idx = mt.indexOf('\n');
        if (atr_idx == -1)
            return "";


        String attr = mt.substring(atr_idx) + 1;

        String delim = "[/;\"=\n\r\t ]";
        StringTokenizer st = new StringTokenizer(attr, delim);
        String name = st.nextToken();
        String eq = null;
        if (st.hasMoreTokens())
            eq = st.nextToken("\"\n\r");
        String val = null;
        if (st.hasMoreTokens())
            st.nextToken("\"\n\r");

        if (name != null)
        {
            add_attr( sb, name );
        }
        if (val == null && eq != null)
            val = eq.substring(1);
        if (val != null)
        {
            add_attr( sb, val );
        }
        return sb.toString();
    }
    public String get_charset( Part p ) throws MessagingException
    {
        if (p == null)
            return null;
        
        String mt = p.getContentType();
        int atr_idx = mt.indexOf(';');
        if (atr_idx == -1)
            atr_idx = mt.indexOf('\n');
        if (atr_idx == -1)
            return "";


        String attr = mt.substring(atr_idx) + 1;

        String delim = "[/;\"=\n\r\t ]";
        StringTokenizer st = new StringTokenizer(attr, delim);
        String name = st.nextToken();
        try
        {
            if (name.compareToIgnoreCase("charset") == 0)
            {
                String eq = st.nextToken("\"\n\r");
                String val = st.nextToken("\"\n\r");
                return javax.mail.internet.MimeUtility.javaCharset(val);
            }
        }
        catch (Exception e)
        {
            System.out.println("Invalid Charset: " + mt);
        }
        return "UTF-8";
    }

    /*
    private String get_mailheaders( Part p ) throws MessagingException
    {
        StringBuffer sb = new StringBuffer();
        Enumeration mail_header_list = p.getAllHeaders();
        if (mail_header_list.hasMoreElements())
        {
            add_attr( sb, "(" );

            while (mail_header_list.hasMoreElements())
            {
                Object header_entry = mail_header_list.nextElement();
                if (header_entry instanceof Header)
                {
                    Header ih = (Header) header_entry;
                    String value = ih.getValue();
                    if ( value == null)
                        continue;


                    // THIS ONE IS HANDLED OUTSIDE
                    if (ih.getName().toLowerCase().contains("content-type"))
                    {
                        int idx = value.indexOf("charset");
                        if (idx > 0)
                        {
                            value = value.substring(idx);
                        }
                        else
                            continue;
                    }
                    else
                        continue;
                    

                    String[] arg_list = value.split("[=\"\']");
                    for (int i = 0; i < arg_list.length; i++)
                    {
                        if (arg_list[i].length() > 0)
                            add_attr( sb, arg_list[i] );
                    }
                }
            }
            add_attr( sb, ")" );
        }
        return sb.toString();
    }
     * */

    private void get_part_struct( StringBuffer sb, Object content ) throws MessagingException, IOException
    {
        if (content instanceof Multipart)
        {
            Multipart mp = (Multipart) content;
            try
            {
                if (mp.getCount() == 1)
                {
                    Part p = mp.getBodyPart(0);
                    get_part_struct( sb, p );
                }
                for (int i = 0; i < mp.getCount(); i++)
                {
                    Part p = mp.getBodyPart(i);
                    get_part_struct( sb, p );
                }
            }
            catch (Exception messagingException)
            {
                LogManager.log(Level.WARNING, "Error in index_mp_content for " + uid + ": " + messagingException.getMessage());
            }

        }
        else if (content instanceof MimePart)
        {
            MimePart p = (MimePart) content;

            if (p.isMimeType("multipart/*"))
            {
                add_attr( sb, "(" );

                Multipart mp = (Multipart) p.getContent();
                get_part_struct( sb, mp );
               
                add_attr( sb, get_content_subtype(p) );
                
                String h_entry = get_content_attributes(p);
                if (h_entry.length() >0)
                {
                    add_attr( sb, "(" );
                    sb.append(h_entry);
                    add_attr( sb, ")" );
                }
                else
                    add_attr( sb, null );


                add_attr( sb, null );
                add_attr( sb, null );
                
                
                add_attr( sb, ")" );
                
            }
            else
            {
                // BODY TYPE + SOBTYPE
                add_attr( sb, "(" );

                String[] cts = new String[2];

                String mt = p.getContentType();
                if (mt != null && mt.length() > 0)
                {
                    String[] _cts = mt.split("/");
                    for ( int i = 0; i < cts.length && i < _cts.length; i++)
                    {
                        cts[i] = _cts[i];
                    }
                }
                add_attr( sb, get_content_type(p) );
                add_attr( sb, get_content_subtype(p) );


                // BODY PARAMETERS
                String h_entry = get_content_attributes(p);
                if (h_entry.length() >0)
                {
                    add_attr( sb, "(" );
                    sb.append(h_entry);
                    add_attr( sb, ")" );
                }
                else
                    add_attr( sb, null );

                // ID
                add_attr( sb, p.getContentID() );

                // DESCR
                add_attr(sb, p.getDescription() );

                // ENCODING
                add_attr( sb, p.getEncoding() );

                // SIZE
                add_attr( sb, p.getSize() );

                // TYPE SPECIFIC
                if (p.isMimeType("text/*"))
                {
                    int lc = p.getLineCount();
                    if (lc == -1)
                    {
                        lc = 0;
                        String t = p.getContent().toString();
                        for (int l = 0; l < t.length(); l++)
                        {
                            if (t.charAt(l) == '\n')
                                lc++;
                        }
                    }
                    add_attr( sb, lc );
                }

                add_attr( sb, null );
                add_attr( sb, null );
                add_attr( sb, null );
                add_attr( sb, null );
                // MESSAGE/RFC822 IS MISSING

                add_attr( sb, ")" );
            }
        }
    }
    
    Part get_part( Object content,  String id ) throws MessagingException, IOException
    {
        Multipart mp = null;
        if (content instanceof Multipart)
        {
            mp = (Multipart)content;
        }
        else if (content instanceof Part)
        {
            Part p = (Part)content;
            if (p.isMimeType("multipart/*"))
            {
                if (p.getContent() instanceof Multipart)
                {
                    mp = (Multipart)p.getContent();
                }
            }
        }
        if (mp == null)
        {
            System.out.println("Invalid content in get_part ");
            return null;
        }
        
        
        
        String[] plist = id.split("\\.");
        if (plist.length == 0)
        {
            System.out.println("Invalid id in get_part ");
            return null;
        }

        int this_part = Integer.parseInt(plist[0]) - 1;
        Part bp  = mp.getBodyPart(this_part);
        
        if (plist.length > 1 )
        {
            int idx = id.indexOf('.');
            Part pp = get_part( bp, id.substring(idx + 1) );
            return pp;
        }
        return bp;
    }
    public Part get_body_part( String id ) throws IOException
    {
        load_rfc_mail();

        MimeMessage msg = mmail.getMsg();

        try
        {
            Part p = get_part(msg.getContent(), id);

            return p;
        }
        catch (MessagingException messagingException)
        {
            messagingException.printStackTrace();
        }

        return null;
    }

    
    @Override
    public String getBodystructure() throws IOException
    {
        load_rfc_mail();

        MimeMessage msg = mmail.getMsg();

        StringBuffer sb = new StringBuffer();

        try
        {
//            Object content = msg.getContent();

            get_part_struct( sb, msg );
        }
        catch (MessagingException messagingException)
        {
            messagingException.printStackTrace();
        }


        return sb.toString();

    }
}
