/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import com.sun.mail.smtp.SMTPTransport;
import java.net.Socket;

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;




class SMTPUserContext
{
}



public class SMTPAuth extends GenericRealmAuth
{    
    Socket smtp_sock;

    SMTPUserContext user_context;

    SMTPAuth(  String host, int port, int flags )
    {
        super(flags, host, port);
        
        if (port == 0)
        {
            port = 25;
            if (is_ssl())
                port = 465;
        }

        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    }

   

    @Override
    public void close_user_context()
    {
        close_user(user_context);
        user_context = null;
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
            transport.close();
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
        return smtp_sock != null;
    }

    String get_mail_for_user( String user_principal )
    {
        return "mark@dimm.de";
    }

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        String email = get_mail_for_user( user_principal );

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

    SMTPTransport transport;
    SMTPUserContext open_user( String user_principal, String pwd, String mailadr )
    {
        Properties props = new Properties();
        props.put("mail.host", host);
        props.put("mail.port", port);
        props.put("mail.smtp.auth", true );
        if ( is_ssl())
        {
            props.put("mail.smtp.ssl.enable", true );
        }
        
        props = set_conn_props(props, "smtp", port);

        try
        {
            Session mailConnection = Session.getInstance(props, null);
            URLName params = new URLName("smtp", host, port, null, user_principal, pwd);
            transport = new SMTPTransport(mailConnection, params);

            transport.connect(smtp_sock);

            int code = transport.getLastReturnCode();
            if (is_smtp_ok(code))
            {
                code = transport.simpleCommand("MAIL FROM:" + mailadr);
                String ret = transport.getLastServerResponse();
                System.out.println(ret);
                code = transport.simpleCommand("RCPT TO:" + mailadr);
                ret = transport.getLastServerResponse();
                System.out.println(ret);
                code = transport.simpleCommand("RSET");
                ret = transport.getLastServerResponse();
                System.out.println(ret);
            }
            return new SMTPUserContext();
        }
        catch (MessagingException messagingException)
        {
        }
        return null;
    }


    void close_user( SMTPUserContext uctx )
    {       
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
