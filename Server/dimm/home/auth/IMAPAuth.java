/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import com.sun.mail.imap.IMAPStore;
import java.net.Socket;

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;




class IMAPUserContext
{
}



public class IMAPAuth extends GenericRealmAuth
{    
    Socket imap_sock;
    IMAPStore store;


    IMAPUserContext user_context;

    IMAPAuth(  String host, int port, int flags )
    {       
        super(flags, host, port);

        if (port == 0)
        {
            port = 143;
            if (is_ssl())
                port = 993;
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
            imap_sock = new Socket(host, port);
            if (imap_sock.isConnected())
            {                
                ret = true;
                imap_sock.close();
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
        return imap_sock != null;
    }


    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
        return user_context == null ? false : true;
    }

    


    IMAPUserContext open_user( String user_principal, String pwd )
    {
        Properties props = new Properties();
        props.put("mail.host", host);
        props.put("mail.port", port);

        props = set_conn_props(props, "imap", port);
       
        try
        {
            Session mailConnection = Session.getInstance(props, null);
            URLName params = new URLName("imap", host, port, null, user_principal, pwd);
            store = new IMAPStore(mailConnection, params);

            store.connect();

            if (store.isConnected())
            {
                return new IMAPUserContext();
            }
        }
        catch (MessagingException messagingException)
        {
        }
        return null;
    }

    void close_user( IMAPUserContext uctx )
    {
       
    }

    public static void main( String[] args)
    {
        IMAPAuth auth = new IMAPAuth("192.168.1.120", 143, 0);

        if (auth.connect())
        {
            if (auth.open_user_context("EXJournal", "12345"))
            {
                System.out.println("Feini");
                auth.close_user_context();
            }
            auth.disconnect();
        }
    }

    
  
}
