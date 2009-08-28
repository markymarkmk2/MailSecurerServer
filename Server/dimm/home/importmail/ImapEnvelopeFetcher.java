/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import home.shared.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author mw
 */
public class ImapEnvelopeFetcher extends MailBoxFetcher
{
    public ImapEnvelopeFetcher( ImapFetcher _imfetcher )
    {
        super( _imfetcher );
    }

    @Override
    protected void archive_message( Message message ) throws ArchiveMsgException, VaultException
    {
        if ( message instanceof MimeMessage)
        {
            try
            {
                MimeMessage m = (MimeMessage) message;
                if (m.getContent() instanceof Multipart)
                {
                    Multipart mp = (Multipart) m.getContent();
                    for (int i = 0; i < mp.getCount(); i++)
                    {
                        BodyPart bp = mp.getBodyPart(i);
                        if (bp.getContentType().compareTo("message/rfc822") == 0)
                        {
                            Object content = bp.getContent();
                            if (content instanceof Message)
                            {
                                Message cm = (Message)content;
                                super.archive_message(cm);
                            }
                            else
                            {
                                Session s = Session.getDefaultInstance( new Properties());
                                InputStream is = bp.getInputStream();

                                Message cm = new MimeMessage( s, is );
                                super.archive_message(cm);
                            }
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
