/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.auth;

import home.shared.CS_Constants;
import home.shared.hibernate.AccountConnector;
import java.util.ArrayList;
import java.util.Properties;
import javax.naming.NamingException;

/**
 *
 * @author mw
 */

           /*
             * CLIENT HAS THESE:
                new AccountConnectorTypeEntry("ldap","LDAP"),
                new AccountConnectorTypeEntry("smtp","SMTP"),
                new AccountConnectorTypeEntry("pop","POP3"),
                new AccountConnectorTypeEntry("imap","IMAP"),
             * */


 
public abstract class GenericRealmAuth
{
    public static final int CONN_MODE_MASK = 0x000f;
    public static final int CONN_MODE_INSECURE = 0x0001;
    public static final int CONN_MODE_FALLBACK = 0x0002;
    public static final int CONN_MODE_TLS = 0x0003;
    public static final int CONN_MODE_SSL = 0x0004;

    private final String DEFAULT_SSL_FACTORY = "dimm.home.auth.DefaultSSLSocketFactory";
    private final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

  //  Role role;
    AccountConnector act;
    String error_txt;
    int flags;
    String host;
    int port;

    void set_params( /*Role role,*/ AccountConnector act )
    {
    //    this.role = role;
        this.act = act;
    }

    public GenericRealmAuth( int flags, String host, int port )
    {
        this.flags = flags;
        this.host = host;
        this.port = port;
    }

    public static GenericRealmAuth factory_create_realm( /*Role role,*/ AccountConnector act )
    {
        GenericRealmAuth realm = null;
        if (act.getType().compareTo("ldap") == 0)
        {

            realm = new LDAPAuth( act.getUsername(), act.getPwd(), act.getIp(), act.getPort(), act.getFlags() );
            realm.set_params(/*role,*/ act);
        }
        
        if (act.getType().compareTo("smtp") == 0)
        {
            realm = new SMTPAuth( act.getIp(), act.getPort(), act.getFlags() );
            realm.set_params(act);
        }
        if (act.getType().compareTo("pop") == 0)
        {
            realm = new POP3Auth( act.getIp(), act.getPort(), act.getFlags() );
            realm.set_params(act);
        }
        if (act.getType().compareTo("imap") == 0)
        {
            realm = new IMAPAuth( act.getIp(), act.getPort(), act.getFlags() );
            realm.set_params(act);
        }

        return realm;
    }


    public abstract boolean connect();
    public abstract boolean disconnect();
    public abstract boolean is_connected();
   
    public abstract boolean open_user_context( String user_principal, String pwd );
    public abstract void close_user_context();

       
    public String get_error_txt()
    {
        return error_txt;
    }

  

    public ArrayList<String> list_groups() throws NamingException
    {
        return new ArrayList<String>();
    }

    public ArrayList<String> list_mails_for_userlist( ArrayList<String> users ) throws NamingException
    {
        return new ArrayList<String>();
    }


    public ArrayList<String> list_users_for_group( String group ) throws NamingException
    {
        return new ArrayList<String>();
    }
    public String get_user_attribute( String attr_name )
    {
        return null;
    }

    public String get_ssl_socket_classname( boolean with_cert )
    {
        if (with_cert)
        {
            return SSL_FACTORY;
        }
        else
        {
            return DEFAULT_SSL_FACTORY;
        }
    }

    Properties set_conn_props( Properties props, String protocol, int port )
    {
        boolean with_cert = test_flag(CS_Constants.ACCT_HAS_TLS_CERT);

        if (test_flag( CS_Constants.ACCT_USE_TLS_IF_AVAIL))
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "true");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(with_cert));
        }
        else if (test_flag( CS_Constants.ACCT_USE_TLS_FORCE))
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(with_cert));
        }
        else if (test_flag( CS_Constants.ACCT_USE_SSL))
        {
            protocol = protocol + "s";
            props.put("mail." + protocol + ".socketFactory.class", get_ssl_socket_classname(with_cert));
            props.put("mail." + protocol + ".socketFactory.port", port);
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
        }

        // DEFAULTTIMOUT IS 10 S
        props.put("mail." + protocol + ".connectiontimeout", 10 * 1000);
        props.put("mail." + protocol + ".timeout", 10 * 1000);

        return props;
    }

    boolean is_ssl()
    {
        return test_flag( CS_Constants.ACCT_USE_SSL );
    }

    private boolean test_flag( int test_flag )
    {
        return (flags & test_flag) == test_flag;
    }


}
