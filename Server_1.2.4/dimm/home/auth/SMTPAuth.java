/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import com.sun.mail.smtp.SMTPTransport;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.net.Socket;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;







public class SMTPAuth extends GenericRealmAuth
{    
    Socket smtp_sock;

    SMTPUserContext user_context;

    public SMTPUserContext get_user_context()
    {
        return user_context;
    }

    public SMTPAuth(  String host, int port, int flags )
    {
        super(flags, host, port);
        
        if (port == 0)
        {
            port = 25;
            if (is_ssl())
                port = 465;
        }
    }

   

    @Override
    public void close_user_context()
    {
        if (user_context != null)
        {
            close_user(user_context);
            user_context = null;
        }
    }

    
    @Override
    public boolean connect()
    {
        boolean ret = false;
        try
        {
            smtp_sock = new Socket(host, port);
            if (smtp_sock.isConnected())
            {                
                ret = true;
            }            
        }
        catch (Exception exc)
        {
            error_txt = exc.getMessage();
            exc.printStackTrace();
        }
        return ret;
    }


    @Override
    public boolean disconnect()
    {
        try
        {
            smtp_sock.close();
            return true;
        }
        catch (Exception exc)
        {
            error_txt = exc.getMessage();
        }
        return false;
    }

   

    @Override
    public boolean is_connected()
    {
        if (smtp_sock != null)
        {
            return smtp_sock.isConnected();
        }
        return false;
    }

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        String email = get_dbs_mail_for_user( user_principal );

        user_context = open_user(user_principal, pwd, email);
        return user_context == null ? false : true;
    }

    boolean is_smtp_ok(int code)
    {
        if (code > 220 && code < 300)
            return true;

        return false;
    }
    boolean is_smtp_request(int code)
    {
        if (code >= 300 && code < 400)
            return true;
        return false;
    }

    
    public SMTPUserContext open_user( String user_principal, String pwd, String mailadr )
    {
        Properties props = new Properties();
        props.put("mail.host", host);
        props.put("mail.port", port);
        props.put("mail.smtp.auth", true );
        String protocol = "smtp";
        if ( is_ssl())
        {
            props.put("mail.smtp.ssl.enable", true );
            protocol = "smtps";
        }        
        props = set_conn_props(props, "smtp", port);

        try
        {
            Session mailConnection = Session.getInstance(props, null);
            URLName params = new URLName(protocol, host, port, null, user_principal, pwd);
            SMTPTransport transport = new SMTPTransport(mailConnection, params);

            transport.connect(smtp_sock);

            int code = transport.getLastReturnCode();
            //System.out.println("" + code);
            // CHECK MAIL ALSO
            if (mailadr != null && mailadr.length() > 0)
            {
                if (is_smtp_ok(code))
                {
                    code = transport.simpleCommand("MAIL FROM:" + mailadr);
                    String ret = transport.getLastServerResponse();
                    LogManager.debug_msg(4, ret);
                }
                if (is_smtp_ok(code))
                {
                    code = transport.simpleCommand("RCPT TO:" + mailadr);
                    String ret = transport.getLastServerResponse();
                    LogManager.debug_msg(4, ret);
                }
                if (is_smtp_ok(code))
                {
                    code = transport.simpleCommand("RSET");
                    String ret = transport.getLastServerResponse();
                    LogManager.debug_msg(4, ret);
                }
            }

            if (is_smtp_ok(code))
                return new SMTPUserContext(transport);
            else
            {
                String ret = transport.getLastServerResponse();
                LogManager.err_log( "SMTP auth failed: " + ret);
                error_txt = ret;
            }
        }
        catch (AuthenticationFailedException exc)
        {
            LogManager.info_msg( "SMTP auth failed");
            error_txt = Main.Txt("Authentication_failed");
        }
        catch (MessagingException messagingException)
        {
            LogManager.err_log( "SMTP auth aborted: ", messagingException);
            error_txt = messagingException.getMessage();
        }
        return null;
    }


    void close_user( SMTPUserContext uctx )
    {
        try
        {
            uctx.transport.close();
        }
        catch (MessagingException ex)
        {
            LogManager.err_log( "Cannot close SMTP connect", ex);
        }
    }

    public static void main( String[] args)
    {
        SMTPAuth auth = new SMTPAuth("auth.mail.onlinehome.de", 25, 0);

        if (auth.connect())
        {
            if (auth.open_user_context("1166-560-2", "helikon"))
            {
                System.out.println("Feini");
                auth.close_user_context();
            }
            auth.disconnect();
        }
    }
}
