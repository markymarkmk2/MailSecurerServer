/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Notification;

import com.sun.mail.smtp.SMTPTransport;
import dimm.home.auth.SMTPAuth;
import dimm.home.auth.SMTPUserContext;
import dimm.home.mail.RFCCreateMimeMail;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import home.shared.hibernate.Mandant;
import home.shared.mail.RFCMimeMail;
import java.util.ArrayList;
import java.util.Properties;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

/**
 *
 * @author mw
 */
class NotificationOneShot
{
    long ma_id;
    String Txt;

    public NotificationOneShot( long ma_id, String Txt )
    {
        this.ma_id = ma_id;
        this.Txt = Txt;
    }

    public long getMa_id()
    {
        return ma_id;
    }

    public String getTxt()
    {
        return Txt;
    }

}
public class Notification
{
    public static final int NF_INFORMATIVE = 1;
    public static final int NF_WARNING = 2;
    public static final int NF_ERROR = 3;
    public static final int NF_FATAL_ERROR = 4;

    static final ArrayList<NotificationOneShot> one_shot_list = new ArrayList<NotificationOneShot>();
    private static void handle_notification( final Mandant m, final int lvl, final String t )
    {
        Runnable r = new Runnable() {

            @Override
            public void run()
            {
                run_handle_notification( m, lvl, t );
            }
        };

        Thread thr = new Thread(r, "Notification");

        thr.start();
    }

    static String get_text_for_level( int lvl)
    {
        switch( lvl )
        {
            case NF_INFORMATIVE: return Main.Txt("Informative");
            case NF_WARNING: return Main.Txt("Warning");
            case NF_ERROR: return Main.Txt("Error");
            case NF_FATAL_ERROR: return Main.Txt("Fatal_error");
            default: return "Unknown level";
        }
    }
    private static String build_subject( Mandant m, int lvl )
    {
        String sj = get_text_for_level( lvl) + " " + Main.Txt("mail from") + " " + "MailSecurer <" + m.getName() + ">";
        return sj;
    }
    
    private static void run_handle_notification( Mandant m, int lvl, String t )
    {

        MandantContext m_ctx = Main.get_control().get_m_context(m);
        
        String host = m_ctx.getMandant().getSmtp_host();
        int port = m_ctx.getMandant().getSmtp_port();
        String send_to = m_ctx.getMandant().getNotificationlist();
        if (send_to == null || send_to.length() == 0)
        {
            LogManager.err_log_fatal( Main.Txt("Cannot_find_to_adress_in_notification"));
            return;
        }


        SMTPAuth smtp = null;
        SMTPUserContext smtp_ctx = null;

        try
        {
            smtp = new SMTPAuth(host, port, m_ctx.getMandant().getSmtp_flags());

            if (!smtp.connect())
            {
                LogManager.err_log_fatal( Main.Txt("Cannot_connect_to_SMTP_host") + " " + host + ":" + port );
                return;
            }
            String user = m_ctx.getMandant().getSmtp_user();
            String pwd = m_ctx.getMandant().getSmtp_pwd();
            String from_mail = m_ctx.getMandant().getMailfrom();

            smtp_ctx = smtp.open_user(user, pwd, null);

            if (smtp_ctx == null)
            {
                LogManager.err_log_fatal( Main.Txt("Cannot_authenticate_at_SMTP_host") + " " + host + ":" + port + " user " + user + " to " + send_to );
                smtp.disconnect();
                return;
            }
            SMTPTransport transport = smtp_ctx.get_transport();

            String[] adr_list = send_to.split(CS_Constants.TEXTLIST_DELIM);
            String to = adr_list[0];
            String[] cc = null;
            if (adr_list.length > 1)
            {
                cc = new String[adr_list.length - 1];
                for (int i = 1; i < adr_list.length; i++)
                {
                    cc[i - 1] = adr_list[i];
                }
            }

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            RFCCreateMimeMail mail = new RFCCreateMimeMail(props);
            String subject = build_subject(m, lvl);
            
            mail.create(from_mail, to, cc, subject, t, null);

            Address[] addresses = new Address[adr_list.length];
            for (int i = 0; i < adr_list.length; i++)
            {
                InternetAddress ad;
                try
                {
                    ad = new InternetAddress(adr_list[i], false);
                }
                catch (AddressException addressException)
                {
                    LogManager.err_log_fatal( Main.Txt("Invalid_To_address") + ":" + adr_list[i], addressException);
                    ad = new InternetAddress("root@localhost", false);
                }

                addresses[i] = ad;
            }

            transport.sendMessage(mail.getMsg(), addresses);
        }
        catch (Exception exc )
        {
             LogManager.err_log_fatal( Main.Txt("Cannot_send_notification_mail") + " " + host + ":" + port + " to " + send_to + ": ", exc );
        }
        finally
        {
            smtp.close_user_context();
            smtp.disconnect();
        }
    }

    public static void throw_notification( Mandant m, int lvl, String t )
    {
        if (t == null)
            return;
        
        handle_notification(m, lvl, t);
    }

    public static void throw_notification_one_shot( Mandant m, int lvl, String t )
    {
        if (t == null)
            return;

        synchronized( one_shot_list )
        {
            if (exists_one_shot( m.getId(), t ))
                return;

            handle_notification(m, lvl, t);

            one_shot_list.add( new NotificationOneShot(m.getId(), t));
        }
    }

    public static void clear_notification_one_shot( Mandant mandant, String t )
    {
        long id = mandant.getId();
        
        for (int i = 0; i < one_shot_list.size(); i++)
        {
            NotificationOneShot notificationOneShot = one_shot_list.get(i);
            if (notificationOneShot.getMa_id() == id)
            {
                if (t.equals(notificationOneShot.getTxt()))
                {
                    one_shot_list.remove(notificationOneShot);
                }
            }
        }
    }

    private static boolean exists_one_shot( int id, String t )
    {
        for (int i = 0; i < one_shot_list.size(); i++)
        {
            NotificationOneShot notificationOneShot = one_shot_list.get(i);
            if (notificationOneShot.getMa_id() == id)
            {
                if (t.equals(notificationOneShot.getTxt()))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
