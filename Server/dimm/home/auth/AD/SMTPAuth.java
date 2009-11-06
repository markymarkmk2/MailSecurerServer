/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth.AD;

import com.sun.mail.smtp.SMTPTransport;
import dimm.home.mailarchiv.Exceptions.AuthException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.String;
import java.net.Socket;
import java.util.ArrayList;

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.naming.NamingException;
import org.apache.commons.codec.binary.Base64;




class SMTPUserContext
{
}



public class SMTPAuth extends GenericRealmAuth
{

    
    String host;
    int port;
    boolean ssl;
    Socket smtp_sock;
    String error_txt;


    SMTPUserContext user_context;

    SMTPAuth(  String host, int port, boolean ssl )
    {
       
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        if (port == 0)
        {
            port = 25;
        }
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

    }

   

    @Override
    public void close_user_context()
    {
        close_user(user_context);
        user_context = null;
    }

    ArrayList<String> last_answer_list;
    boolean has_plain_login;
/*
    String get_last_answer_line()
    {
        if (last_answer_list.size() == 0)
            return null;

        return last_answer_list.get(last_answer_list.size() - 1);
    }
    String get_last_answer_text()
    {
        String line = get_last_answer_line();
        if (line == null)
            return null;

        int idx = line.indexOf(' ');
        if (idx > 0 && line.length() > idx)
        {
            return line.substring(idx + 1);
        }
        return null;
    }

    int smtp_command( String cmd ) throws AuthException
    {
        int ret = 0;
        last_answer_list = new ArrayList<String>();

        String s = cmd + "\n";
        try
        {
            smtp_sock.getOutputStream().write(s.getBytes());
            smtp_sock.getOutputStream().flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(smtp_sock.getInputStream()));

            String line = in.readLine();

            last_answer_list.add(line);
            while (in.ready())
            {
                line = in.readLine();
                last_answer_list.add(line);
            }
        }
        catch (IOException iOException)
        {
             throw new AuthException("Comm failed", iOException );
        }

        // CHECK ANSWER
        if (last_answer_list.size() > 0)
        {
            String result_line = get_last_answer_line();
            int idx = result_line.indexOf(' ');
            String number_txt = result_line.substring(0, idx);

            try
            {
                ret = Integer.parseInt(number_txt);
            }
            catch (NumberFormatException numberFormatException)
            {
                throw new AuthException("SMTP format error: " + result_line );
            }
        }

        return ret;
    }
*/
    
    @Override
    public boolean connect()
    {
        boolean ret = false;
        try
        {
            smtp_sock = new Socket(host, port);
            if (smtp_sock.isConnected())
            {
                /*
                int code = smtp_command( "EHLO" );

                if (code == 220)
                {
                    ret = true;
                    for (int i = 0; i < last_answer_list.size(); i++)
                    {
                        String string = last_answer_list.get(i);
                        if (string.toUpperCase().indexOf("PLAIN") >= 0)
                        {
                            has_plain_login = true;                            
                        }                        
                    }
                }*/
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
/*            if (smtp_sock != null)
            {
                smtp_command( "QUIT" );
                
                smtp_sock.close();
            }
            smtp_sock = null;
 * */
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
    public String get_error_txt()
    {
        return error_txt;
    }

    @Override
    public String get_user_attribute( String attr_name )
    {
        return get_user_attribute(user_context, attr_name);
    }

    @Override
    public boolean is_connected()
    {
        return smtp_sock != null;
    }

    @Override
    public ArrayList<String> list_groups() throws NamingException
    {
        return new ArrayList<String>();
    }

    @Override
    public ArrayList<String> list_mails_for_userlist( ArrayList<String> users ) throws NamingException
    {
        return new ArrayList<String>();
    }

    @Override
    public ArrayList<String> list_users_for_group( String group ) throws NamingException
    {
        return new ArrayList<String>();
    }

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
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
    SMTPUserContext open_user( String user_principal, String pwd )
    {
        Properties props = new Properties();
        props.put("mail.host", host);
        props.put("mail.port", port);
        props.put("mail.smtp.auth", true );

        try
        {
            Session mailConnection = Session.getInstance(props, null);
            URLName params = new URLName("smtp", host, port, null, user_principal, pwd);
            transport = new SMTPTransport(mailConnection, params);

            transport.connect(smtp_sock);

            int code = transport.getLastReturnCode();
            if (is_smtp_ok(code))
                return new SMTPUserContext();
        }
        catch (MessagingException messagingException)
        {
        }
        return null;
    }
/*
    SMTPUserContext my_open_user( String user_principal, String pwd )
    {
        try
        {
            if (has_plain_login)
            {
                int code = smtp_command("AUTH LOGIN");
                if (!is_smtp_request(code))
                {
                    error_txt = "Auth login failed: " + get_last_answer_line();
                    return null;
                }
                while ( is_smtp_request( code ))
                {

                    String answer = get_last_answer_text();

                    String request = new String( Base64.encodeBase64(answer.getBytes()) );
                    if (request.toLowerCase().startsWith("username"))
                    {
                        String data = new String( Base64.encodeBase64(user_principal.getBytes()) );
                        code = smtp_command(data);
                    }
                    if (request.toLowerCase().startsWith("password"))
                    {
                        String data = new String( Base64.encodeBase64(user_principal.getBytes()) );
                        code = smtp_command(data);
                    }
                }
                if (!is_smtp_ok( code ))
                {
                    if (code == 535)
                    {
                        error_txt = "Invalid user / password: " + get_last_answer_line();
                        return null;
                    }

                    error_txt = "Authetication error: " + get_last_answer_line();
                    return null;
                }
                SMTPUserContext ctx = new SMTPUserContext();
                return ctx;
            }
        }
        catch (Exception namingException)
        {
            namingException.printStackTrace();
        }
        return null;
    }
*/

    String get_user_attribute( SMTPUserContext uctx, String attr_name )
    {
        return null;
    }

    void close_user( SMTPUserContext uctx )
    {
       
    }

    public static void main( String[] args)
    {
        SMTPAuth auth = new SMTPAuth("auth.mail.onlinehome.de", 25, false);

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
