/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import com.sun.mail.smtp.SMTPTransport;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;

/**
 *
 * @author Administrator
 */
public class SmtpMailFromTransport extends SMTPTransport
{
    String mail_from;

    public SmtpMailFromTransport( Session session, URLName url )
    {
        this(session, url, null);
    }
    public SmtpMailFromTransport( Session session, URLName url, String mail_from )
    {
        super(session, url);
        this.mail_from = mail_from;
    }

    @Override
    protected void mailFrom() throws MessagingException
    {
        if (mail_from == null || mail_from.length() == 0)
        {
            super.mailFrom();
            return;
        }

        int code = simpleCommand("MAIL FROM:" + mail_from);

        if (code < 200 || code >= 300)
            throw new MessagingException("Mail from failed");
    }

    public void set_mail_from( String s)
    {
        mail_from = s;
    }



}
