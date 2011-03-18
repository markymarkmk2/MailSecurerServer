/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
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

    ArrayList<Address> mk_adr_list( Address[] arr )
    {
        ArrayList<Address> list = new ArrayList<Address>();

        if (arr == null)
            return list;

        for (int i = 0; i < arr.length; i++)
        {
            Address address;
            try
            {
                address = new InternetAddress(arr[i].toString());
                list.add(address);
            }
            catch (AddressException ex)
            {
                LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Skipping invalid recipient address: " + arr[i].toString(), ex );
            }
        }
        return list;
    }
    ArrayList<Address> mk_envelope_adr_list( String token, String text )
    {

        ArrayList<Address> ret_list = new ArrayList<Address>();

        String[] rcp_array = text.split("[\n\r]");
        if (rcp_array.length > 0)
        {
            for (int i = 0; i < rcp_array.length; i++)
            {

                String line = rcp_array[i];
                String recipient = null;


                if (line.startsWith(token))
                {
                    recipient = token;                    
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
                        ret_list.add(adr);
                    }
                }
                catch (Exception addressException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Skipping invalid recipient address in envelope header line: " + mail, addressException );
                }
            }
        }
        return ret_list;
    }
    boolean is_in_adr_list( ArrayList<Address> a_list, Address adr )
    {
        for (int i = 0; i < a_list.size(); i++)
        {
            Address address = a_list.get(i);
            if (adr.equals(address))
                return true;

        }
        return false;
    }

    void merge_adr_lists(  ArrayList<Address> a_list, ArrayList<Address> b_list )
    {
        ArrayList<Address> ret_list = a_list;

        for (int i = 0; i < b_list.size(); i++)
        {
            Address address = b_list.get(i);
            if (!is_in_adr_list(ret_list, address))
            {
                b_list.remove(address);
                ret_list.add(address);
            }
        }       
    }
    ArrayList<Address> remove_existing_entries(  ArrayList<Address> a_list, ArrayList<Address> b_list )
    {
        for (int i = 0; i < b_list.size(); i++)
        {
            Address address = b_list.get(i);
            if (is_in_adr_list(a_list, address))
            {
                b_list.remove(address);
            }
        }
        return b_list;
    }


    void get_envelope_recepients( MimeMessage cm, RFCFileMail mail, String text  ) throws MessagingException
    {       

        ArrayList<Address> existing_all = mk_adr_list( cm.getAllRecipients() );

        /* Ex 2007/2010
        Recipient: mw@w2k8becom.dimm.home
        Recipient: mark1@dimm.de, Forward: mark@dimm.de
        Recipient: mark2@dimm.de, Expanded: sales@de.de
         * http://technet.microsoft.com/en-us/library/bb738122%28EXCHG.80%29.aspx
         *
         * Beispiel Ex 2010: Gruppe1 hat sbs_user und fritz raddatz, To der Mail war nur Gruppe1
         Sender: mw@w2k8becom.dimm.home
         Subject: To Gruppe
         Message-Id: <6F7939E3E42E794492D980F20820B06485B001EC@W2K8-BECOM.w2k8becom.dimm.home>
         To: sbs_user@w2k8becom.dimm.home, Expanded: Gruppe1@w2k8becom.dimm.home
         To: fritz.raddatz@w2k8becom.dimm.home, Expanded: Gruppe1@w2k8becom.dimm.home

         * */

        // FETCH ENEVELOPE PARAMS
        ArrayList<Address> envelope_to = mk_envelope_adr_list( "To:", text );
        ArrayList<Address> envelope_cc = mk_envelope_adr_list( "Cc:", text );
        ArrayList<Address> envelope_bcc = mk_envelope_adr_list( "Bcc:", text );
        ArrayList<Address> envelope_recepient = mk_envelope_adr_list( "Recipient:", text );


        // REMOVE EXISTING ENTRIES
        remove_existing_entries( existing_all, envelope_to );
        remove_existing_entries( existing_all, envelope_cc );
        remove_existing_entries( existing_all, envelope_bcc );
        remove_existing_entries( existing_all, envelope_recepient );
        

        // ADD REMAINING ENTRIES TO ATTRIBUTE-LIST
        for (int i = 0; i < envelope_to.size(); i++)
        {
            mail.add_attribute(RFCGenericMail.MATTR_LUCENE, CS_Constants.FLD_TO, envelope_to.get(i).toString() );
        }
        for (int i = 0; i < envelope_cc.size(); i++)
        {
            mail.add_attribute(RFCGenericMail.MATTR_LUCENE, CS_Constants.FLD_CC, envelope_cc.get(i).toString() );
        }
        for (int i = 0; i < envelope_bcc.size(); i++)
        {
            mail.add_attribute(RFCGenericMail.MATTR_LUCENE, CS_Constants.FLD_BCC, envelope_bcc.get(i).toString() );
        }

        // ADD RECIPIENTS TO ENVELOPE ENTRY, THIS WILL BE MERGED LATER AT INDEX TIME, WOULS BE EQUAL TO ADD IT AS BCC, BUT SO WE CAN TEST BOTH WAYS
        for (int i = 0; i < envelope_recepient.size(); i++)
        {
            mail.add_attribute(RFCGenericMail.MATTR_ENVELOPE, CS_Constants.FLD_ENVELOPE_TO, envelope_recepient.get(i).toString() );
        }


        

          /* Ex 2003
        Recipients:"smtp:info@dimm.de" <smtp:info@dimm.de>\n\r"smtp:info1@dimm.de" <smtp:info1@dimm.de>

         * */
        int idx = text.indexOf(RCPIENTS);
        if (idx >= 0)
        {
            Address[] existing_rcpients = cm.getAllRecipients();
            
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
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Cannot find mail recipient in envelope header line: " + line  );
                    continue;
                }
                String mail_addr = line.substring(mail_s + 1, mail_e);
                int prefix_idx = mail_addr.indexOf(':');
                if (prefix_idx >= -1)
                {
                    mail_addr = mail_addr.substring(prefix_idx + 1);
                }
                Address adr = null;
                try
                {
                    adr = new InternetAddress(mail_addr);

                    // CHECK IF ADR IS ALREADY IN RCPLIST
                    if (existing_rcpients != null)
                    {
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
                }
                catch (AddressException addressException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Invalid recipient address in envelope header line: " + line, addressException );
                }


                // ADD NEW ADR (BCC)
                if (adr != null)
                {
                    mail.add_attribute(RFCGenericMail.MATTR_ENVELOPE, CS_Constants.FLD_ENVELOPE_TO, adr.toString() );
                }                   
            }
        }
    }
    
    public static boolean contains_tnef( Part part) throws MessagingException, IOException
    {
        if (part.isMimeType("multipart/*"))
        {
            Multipart mp = (Multipart)part.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++)
            {
                Part mpPart = mp.getBodyPart(i);
                if ( contains_tnef( mpPart))
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

                // TRY TO DECODE EMBEDDED TNEF IF NECESSARY
                try
                {
                    if (contains_tnef(m))
                    {
                        m = TNEFMime.convert(s, m, /*embed*/ true);
                    }
                }
                catch (Exception iOException)
                {
                    LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_FETCHER, "Cannot decode TNEF content using Mime", iOException);
                }

                // NOW WE SHOULD BE ABLE TO READ CONTENTS INTO MIMEMAIL
                RFCMimeMail mail = new RFCMimeMail( m );
                String envelope_text = mail.get_text_content();
                String subject = m.getSubject();


                if (content_object instanceof Multipart)
                {
                    Multipart mp = (Multipart) content_object;
                    for (int i = 0; i < mp.getCount(); i++)
                    {
                        BodyPart bp = mp.getBodyPart(i);
                        if (bp.getContentType().toLowerCase().compareTo("message/rfc822") == 0)
                        {
                            MimeMessage cm = null;
                            InputStream is = bp.getInputStream();

                            cm = new MimeMessage( s, is );
                            is.close();

                           
                          

                            super_archive_message(cm, envelope_text);

/*                            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_FETCHER, "Testmode double import");
                            LogicControl.sleep(2000);
                            {
                                is = bp.getInputStream();

                                cm = new MimeMessage( s, is );
                                is.close();
                                super_archive_message(cm, envelope_text + "\nBcc: sbs_user@w2k8becom.dimm.home\nTo: mw@w2k8becom.dimm.home\n");
                            }*/
                            
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
                LogManager.printStackTrace(ex);
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_FETCHER, "archive_message failed", ex);
            }
            catch (MessagingException ex)
            {
                LogManager.printStackTrace(ex);
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_FETCHER, "archive_message failed", ex);
            }
        }
        else
        {
            super.archive_message(message);
            set_msg_deleted(message);
        }
    }

    protected void super_archive_message( MimeMessage message, String envelope_text ) throws ArchiveMsgException, VaultException, IndexException
    {

        RFCFileMail mail = null;
        try
        {
            status.set_status(StatusEntry.BUSY, "Archiving message <" + get_subject(message) + "> from Mail server <" + imfetcher.getServer() + ">");

            mail = Main.get_control().create_import_filemail_from_eml(imfetcher.getMandant(), message, "imf", imfetcher.getDiskArchive());

            try
            {
                get_envelope_recepients(message, mail, envelope_text);
            }
            catch (MessagingException messagingException)
            {
                LogManager.msg_fetcher(LogManager.LVL_ERR, "Cannot eval enevelope parameters", messagingException);
            }

            Main.get_control().add_rfc_file_mail(mail, imfetcher.getMandant(), imfetcher.getDiskArchive(), /*bg*/ true, /*del_after*/ true);

            set_msg_deleted(message);
        }
        catch (ArchiveMsgException ex)
        {
            status.set_status(StatusEntry.ERROR, "Cannot archive message from <" + imfetcher.getServer() + ">");
            LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), ex);
            if (mail != null)
            {
                try
                {
                    Main.get_control().move_to_quarantine(mail, imfetcher.getMandant());
                }
                catch (IOException iOException)
                {
                    LogManager.msg_fetcher(LogManager.LVL_ERR, "Cannot move mail to quarantine", iOException);
                }
            }
        }
    }
}
