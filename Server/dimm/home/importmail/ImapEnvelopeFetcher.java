/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.mail.RFCMimeMail;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


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

    protected ImapEnvelopeFetcher( ImapFetcher _imfetcher )
    {
        super( _imfetcher );
    }

    Address[] get_envelope_recepients( String text, Address[] existing_rcpients ) throws MessagingException
    {
        int idx = text.indexOf(RCPIENTS);
        if (idx < 0)
            return null;

        ArrayList<Address> a_list = new ArrayList<Address>();

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
                LogManager.log(Level.WARNING, "Cannot find mail recipient in enevlope header line: " + line  );
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
            }
            catch (AddressException addressException)
            {
                LogManager.log(Level.WARNING, "Invalid recipient address in enevlope header line: " + line, addressException );
            }
            
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

            // ADD NEW ADR (BCC)
            if (adr != null)
                a_list.add(  adr);
        }

        Address[] ret = new Address[a_list.size()];
        for (int i = 0; i < a_list.size(); i++)
        {
            ret[i] = a_list.get(i);
        }
        return ret;
    }

    @Override
    protected void archive_message( Message message ) throws ArchiveMsgException, VaultException, IndexException
    {
        if ( message instanceof MimeMessage)
        {
            try
            {
                MimeMessage m = (MimeMessage) message;
                RFCMimeMail mail = new RFCMimeMail( m );
                
                String envelope_text = mail.get_text_content();


                // GET ATTACHMENT
                if (m.getContent() instanceof Multipart)
                {
                    Multipart mp = (Multipart) m.getContent();
                    for (int i = 0; i < mp.getCount(); i++)
                    {
                        BodyPart bp = mp.getBodyPart(i);
                        if (bp.getContentType().toLowerCase().compareTo("message/rfc822") == 0)
                        {
                            Object content = bp.getContent();
                            Message cm = null;
/*                            if (content instanceof MimeMessage)
                            {                               
                                // CREATE A DUPL OF THE IMAP-MESSAGE IT IS NOT CONNECTOD TO IMAPSERVER ANYMORE
                                cm = new MimeMessage((MimeMessage)content);
                            }
                            else*/
                            {
                                Session s = Session.getDefaultInstance( new Properties());
                                InputStream is = bp.getInputStream();

                                cm = new MimeMessage( s, is );
                                is.close();
                            }

                            Address[] existing_rcpients = cm.getAllRecipients();

                            // EXTRACT ALL ADDRESSES FROM ENV-MAIL THAR ARE NOT IN THIS MESSAGE
                            Address[] recp_list = get_envelope_recepients( envelope_text, existing_rcpients );

                            // ADD BCC
                            if (recp_list != null)
                            {
                                cm.addRecipients(Message.RecipientType.BCC, recp_list);
                            }

                            super.archive_message(cm);
                        
                        }
                    }
                }
                set_msg_deleted(message);
            }

            catch (IOException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }            catch (MessagingException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }

        }
        else
        {
            super.archive_message(message);
            set_msg_deleted(message);
        }
    }
}
