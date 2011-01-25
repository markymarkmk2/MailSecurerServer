/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import com.sun.mail.pop3.POP3Store;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.net.Socket;

import java.util.Properties;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;




class POP3UserContext
{
}



public class POP3Auth extends GenericRealmAuth
{    
    Socket POP3_sock;
    POP3Store store;


    POP3UserContext user_context;

    POP3Auth(  String host, int port, int flags  )
    {
        super( flags, host, port);
        if (port == 0)
        {
            port = 110;
            if (is_ssl())
                port = 995;
        }
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
            POP3_sock = new Socket(host, port);
            if (POP3_sock.isConnected())
            {                
                ret = true;
                POP3_sock.close();
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
            if (store != null)
                store.close();
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
        return POP3_sock != null;
    }


    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
        return user_context == null ? false : true;
    }

    


    POP3UserContext open_user( String user_principal, String pwd )
    {

        Properties props = new Properties();
        props.put("mail.host", host);
        props.put("mail.port", port);

        props = set_conn_props(props, "pop3", port);


        try
        {
            Session mailConnection = Session.getInstance(props, null);
            URLName params = new URLName("pop3", host, port, null, user_principal, pwd);
            store = new POP3Store(mailConnection, params, "test", is_ssl());

            store.connect();

            if (store.isConnected())
            {
                return new POP3UserContext();
            }
        }
        catch (AuthenticationFailedException exc)
        {
            LogManager.msg_auth( LogManager.LVL_ERR, "SMTP auth failed", exc);
            error_txt = Main.Txt("Authentication_failed");
        }
        catch (MessagingException messagingException)
        {
             error_txt = messagingException.getMessage();
        }
        return null;
    }

    void close_user( POP3UserContext uctx )
    {
       
    }

    public static void main( String[] args)
    {
        POP3Auth auth = new POP3Auth("pop.onlinehome.de", 110, 0);

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
