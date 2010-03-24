/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mail;

import home.shared.mail.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 *
 * @author mw
 */
public class RFCCreateMimeMail extends RFCMimeMail
{

    private MimeMessage msg;
    private Session session;
    ArrayList<RFCMailAddress> email_list;

    public RFCCreateMimeMail()
    {
        super();
    }
    public RFCCreateMimeMail(Properties p)
    {
        super(p);
    }



    public void create( String from, String to, String[] cc, String subject, String text, File attachment ) throws MessagingException
    {
        email_list.add(new RFCMailAddress(from, RFCMailAddress.ADR_TYPE.FROM));
        email_list.add(new RFCMailAddress(to, RFCMailAddress.ADR_TYPE.TO));


        // Construct the message
        InternetAddress[] ia_from =
        {
            new InternetAddress(from)
        };
        InternetAddress[] ia_to =
        {
            new InternetAddress(to)
        };

        msg.addFrom(ia_from);
        msg.addRecipients(Message.RecipientType.TO, ia_to);
        if (subject != null)
        {
            msg.setSubject(subject);
        }
        if (cc != null && cc.length > 0)
        {
            InternetAddress[] ia_cc = new InternetAddress[cc.length];
            for (int i = 0; i < cc.length; i++)
            {
                email_list.add(new RFCMailAddress(cc[i], RFCMailAddress.ADR_TYPE.CC));
                ia_cc[i] = new InternetAddress( cc[i] );
            }
            msg.addRecipients(Message.RecipientType.CC, ia_cc);
        }

        // CREATE MULTIPART
        Multipart multipart = new MimeMultipart();

        // CREATE MESSAGE PART
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(text);
        messageBodyPart.setDisposition(MimeBodyPart.INLINE);

        // ADD TO MULTIPART
        multipart.addBodyPart(messageBodyPart);


        if (attachment != null)
        {
            // CREATE ATTACHMENT PART
            MimeBodyPart dataBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachment);
            dataBodyPart.setDataHandler(new DataHandler(source));
            dataBodyPart.setFileName(attachment.getName());
            dataBodyPart.setDisposition(MimeBodyPart.ATTACHMENT);
            // ADD TO MULTIPART
            multipart.addBodyPart(dataBodyPart);
        }

        // ADD MULTIPART TO MESSAGE
        msg.setContent(multipart);

    }
}
