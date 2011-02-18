/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.mail.RFCMimeMail;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;
import net.freeutils.tnef.TNEFUtils;
import net.freeutils.tnef.mime.TNEFMime;


/*
 * FORMAT ENVELOPE:
 Level1: multipart/mixed
    Level2: multipart/alternative
        Level3: text/plain
        Level3: text/html
    Level2: message/rfc822

 In Level3 text/plain:
------_=_NextPart_002_01CA32B6.59D5B0B6
Content-Type: text/plain;
	charset="iso-8859-1"
Content-Transfer-Encoding: quoted-printable

Sender: "Mark Williams" <smtp:mark@localhost>
Message-ID: <99A5077ED69F4E44B4FBAB2935DACB7A@dimm.home>
Recipients:
	"smtp:mark@dimm.de" <smtp:mark@dimm.de>,
	"smtp:pb@dimm.de" <smtp:pb@dimm.de>,
	"smtp:cc2@dimm.de" <smtp:cc2@dimm.de>,
	"smtp:info@dimm.de" <smtp:info@dimm.de>,
	"smtp:dirk@dimm.de" <smtp:dirk@dimm.de>,
	"smtp:kacker@dimm.de" <smtp:kacker@dimm.de>,
	"smtp:bcclast@dimm.de" <smtp:bcclast@dimm.de>

------_=_NextPart_002_01CA32B6.59D5B0B6
*
 *
 *
/**
 *
 * @author mw
 */
public class ImapEnvelopeFetcher extends MailBoxFetcher
{
    public static final String RCPIENTS = "Recipients:";
    public static final String RCPIENT = "Recipient:";

    protected ImapEnvelopeFetcher( ImapFetcher _imfetcher )
    {
        super( _imfetcher );
    }

    Address[] get_envelope_recepients( String text, Address[] existing_rcpients ) throws MessagingException
    {
        ArrayList<Address> a_list = new ArrayList<Address>();

        /* Ex 2007/2010
        Recipient: mw@w2k8becom.dimm.home
        Recipient: mark1@dimm.de, Forward: mark@dimm.de
        Recipient: mark2@dimm.de, Expanded: sales@de.de
         * http://technet.microsoft.com/en-us/library/bb738122%28EXCHG.80%29.aspx
         * */



        String[] rcp_array = text.split("[\n\r]");
        if (rcp_array.length > 0)
        {
            for (int i = 0; i < rcp_array.length; i++)
            {
                String[] valid_r = {"Recipient:", "Bcc:", "Cc:", "To:"};
                String line = rcp_array[i];
                String recipient = null;

                // DETECT A VALID LINE
                for (int j = 0; j < valid_r.length; j++)
                {
                    String r = valid_r[j];
                    if (line.startsWith(r))
                    {
                        recipient = r;
                        break;
                    }
                }

                if (recipient == null)
                    continue;


                // START OF ADDRESS AFTER SPACE
                String mail = line.substring( recipient.length() + 1);

                // ALLOW BRACKETING OF ADDRESS
                StringTokenizer str = new StringTokenizer(mail.trim(), "\"<>,;'\n\r\t :");

                try
                {
                    // GET FIRST TOKEN AFTER RCPIENT
                    if(str.hasMoreTokens())
                    {
                        Address adr = new InternetAddress(str.nextToken().trim());

                        // CHECK IF ADR IS ALREADY IN RCPLIST
                        for (int j = 0; j < existing_rcpients.length; j++)
                        {
                            Address address = existing_rcpients[j];
                            if (address.equals( adr ))
                            {
                                // INVALIDATE
                                adr = null;
                                break;
                            }
                        }
                        if (adr != null)
                            a_list.add(  adr);
                    }
                }
                catch (AddressException addressException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Invalid recipient address in enevlope header line: " + mail, addressException );
                }
            }
        }

          /* Ex 2003
        Recipients:"smtp:info@dimm.de" <smtp:info@dimm.de>\n\r"smtp:info1@dimm.de" <smtp:info1@dimm.de>

         * */
        int idx = text.indexOf(RCPIENTS);
        if (idx >= 0)
        {
            text = text.substring(idx + RCPIENTS.length());

            StringTokenizer str = new StringTokenizer(text, "\n\r");
            while (str.hasMoreTokens())
            {
                String line = str.nextToken();
                if (line.length() == 0)  // SINGLE EMPTY LINE FINISHES
                    break;

                // FORMAT: "smtp:info@dimm.de" <smtp:info@dimm.de>,
                int mail_s = line.indexOf("<");
                int mail_e = line.indexOf(">");
                if (mail_s == -1 || mail_e == -1)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Cannot find mail recipient in enevlope header line: " + line  );
                    continue;
                }
                String mail = line.substring(mail_s + 1, mail_e);
                int prefix_idx = mail.indexOf(':');
                if (prefix_idx >= -1)
                {
                    mail = mail.substring(prefix_idx + 1);
                }
                Address adr = null;
                try
                {
                    adr = new InternetAddress(mail);

                    // CHECK IF ADR IS ALREADY IN RCPLIST
                    for (int i = 0; i < existing_rcpients.length; i++)
                    {
                        Address address = existing_rcpients[i];
                        if (address.equals( adr ))
                        {
                            // INVALIDATE
                            adr = null;
                            break;
                        }
                    }
                }
                catch (AddressException addressException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Invalid recipient address in enevlope header line: " + line, addressException );
                }


                // ADD NEW ADR (BCC)
                if (adr != null)
                    a_list.add(  adr);
            }
        }

        Address[] ret = new Address[a_list.size()];
        for (int i = 0; i < a_list.size(); i++)
        {
            ret[i] = a_list.get(i);
        }
        return ret;
    }
    public static boolean contains_tnef(Session session, Part part) throws MessagingException, IOException
    {
        if (part.isMimeType("multipart/*"))
        {
            Multipart mp = (Multipart)part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++)
            {
                Part mpPart = mp.getBodyPart(i);
                if ( contains_tnef(session, mpPart))
                {
                    return true;
                }
            }
        }
        else if (TNEFUtils.isTNEFMimeType(part.getContentType()))
        {
            return true;
        }
        return false;

    }

    @Override
    protected void archive_message( Message message ) throws ArchiveMsgException, VaultException, IndexException
    {
        if ( message instanceof MimeMessage)
        {
            try
            {
                boolean archived = false;
                MimeMessage m = (MimeMessage) message;
                Session s = Session.getDefaultInstance( new Properties());

                // GET ATTACHMENT
                Object content_object = null;
                try
                {
                    // THIS CAN FAIL IF IMAP MESSAGE IS NOT WELLFORMED
                    content_object = m.getContent();
                }
                catch (MessagingException mexc)
                {
                    // TRY TO READ DIRECTLY FROM STREAM
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Rereading broken IMAP message", mexc);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    m.writeTo(bos);
                    bos.close();
                    SharedByteArrayInputStream bis =
                                    new SharedByteArrayInputStream(bos.toByteArray());
                    
                    m = new MimeMessage(s, bis);
                    bis.close();
                    content_object = m.getContent();
                }

                // NOW WE SHOULD BE ABLE TO READ CONTENTS
                RFCMimeMail mail = new RFCMimeMail( m );
                String envelope_text = mail.get_text_content();
                String subject = m.getSubject();

                try
                {
                    if (contains_tnef(s, m))
                    {
                        m = TNEFMime.convert(s, m, /*embed*/ true);
                    }
                }
                catch (Exception iOException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Cannot decode TNEF content using Mime", iOException);
                }

                if (content_object instanceof Multipart)
                {
                    Multipart mp = (Multipart) content_object;
                    for (int i = 0; i < mp.getCount(); i++)
                    {
                        BodyPart bp = mp.getBodyPart(i);
                        if (bp.getContentType().toLowerCase().compareTo("message/rfc822") == 0)
                        {
                            Message cm = null;                            
                            InputStream is = bp.getInputStream();

                            cm = new MimeMessage( s, is );
                            is.close();

                            Address[] existing_rcpients = cm.getAllRecipients();

                            // EXTRACT ALL ADDRESSES FROM ENV-MAIL THAR ARE NOT IN THIS MESSAGE
                            Address[] recp_list = get_envelope_recepients( envelope_text, existing_rcpients );

                            // ADD BCC
                            if (recp_list != null)
                            {
                                cm.addRecipients(Message.RecipientType.BCC, recp_list);
                            }

                            super.archive_message(cm);
                            archived = true;                        
                        }
                    }
                }

                if (archived)
                {
                    set_msg_deleted(message);
                    //Notification.clear_notification_one_shot(imfetcher.getMandant(), Main.Txt("Invalid_envelope_format_while_fetching_msg_from") + " " + imfetcher.getServer());
                }
                else
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, Main.Txt("Invalid_envelope_format_while_fetching_msg_from") + " " + imfetcher.getServer() + ": " + subject);
                    
                    //Notification.throw_notification_one_shot(imfetcher.getMandant(), Notification.NF_ERROR, Main.Txt("Invalid_envelope_format_while_fetching_msg_from") + " " + imfetcher.getServer());
                }
            }

            catch (IOException ex)
            {
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_FETCHER, "archive_message failed", ex);
            }
            catch (MessagingException ex)
            {
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_FETCHER, "archive_message failed", ex);
            }

        }
        else
        {
            super.archive_message(message);
            set_msg_deleted(message);
        }
    }
}
