/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mail;

import dimm.home.mailarchiv.Main;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 *
 * @author mw
 */
public class RFCMimeMail
{

    private Message msg;
    private Session session;

    public RFCMimeMail()
    {
        java.util.Properties props = new java.util.Properties();
        props.put("mail.smtp.host", "localhost");
        session = Session.getDefaultInstance(props, null);
        msg = new MimeMessage(session);
    }

    public RFCMimeMail( java.util.Properties props )
    {
        session = Session.getDefaultInstance(props, null);
        msg = new MimeMessage(session);
    }

    public void create( String from, String to, String subject, String text, File attachment ) throws MessagingException
    {
        // Construct the message
        getMsg().setFrom(new InternetAddress(from));
        getMsg().setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        getMsg().setSubject("Hotfolder " + attachment.getName());
        // CREATE MULTIPART
        Multipart multipart = new MimeMultipart();
        // CREATE MESSAGE PART
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(Main.Txt("This_mail_was_created_by_an_archive_hotfolder"));
        messageBodyPart.setDisposition(MimeBodyPart.INLINE);
        // ADD TO MULTIPART
        multipart.addBodyPart(messageBodyPart);
        // CREATE ATTACHMENT PART
        MimeBodyPart dataBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(attachment);
        dataBodyPart.setDataHandler(new DataHandler(source));
        dataBodyPart.setFileName(attachment.getName());
        dataBodyPart.setDisposition(MimeBodyPart.ATTACHMENT);
        // ADD TO MULTIPART
        multipart.addBodyPart(dataBodyPart);
        // ADD MULTIPART TO MESSAGE
        getMsg().setContent(multipart);

    }

    public void parse(RFCFileMail mail_file )
    {
        try
        {
            
            
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mail_file.getFile()));
            msg = new MimeMessage(session, bis);
            
            bis.close();
        }

        catch (FileNotFoundException fileNotFoundException)
        {
        }
        catch (IOException iox)
        {
        }
        catch (MessagingException messagingException)
        {
        }


    }

    public void send() throws MessagingException
    {
        Transport.send(getMsg());

    }

    /**
     * @return the msg
     */
    public Message getMsg()
    {
        return msg;
    }
}
