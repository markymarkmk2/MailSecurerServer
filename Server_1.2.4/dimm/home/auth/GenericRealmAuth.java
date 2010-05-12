/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.auth;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import home.shared.filter.FilterMatcher;
import home.shared.filter.FilterValProvider;
import home.shared.filter.LogicEntry;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.MailAddress;
import home.shared.hibernate.MailUser;
import home.shared.hibernate.Role;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
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
                new AccountConnectorTypeEntry("imap","IMAP"),
             * */


 
public abstract class GenericRealmAuth
{
    public static final int CONN_MODE_MASK = 0x000f;
    public static final int CONN_MODE_INSECURE = 0x0001;
    public static final int CONN_MODE_FALLBACK = 0x0002;
    public static final int CONN_MODE_TLS = 0x0003;
    public static final int CONN_MODE_SSL = 0x0004;

    private final String DEFAULT_SSL_FACTORY = "home.shared.Utilities.DefaultSSLSocketFactory";
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

    public GenericRealmAuth(  int flags, String host, int port )
    {
        this.flags = flags;
        this.host = host;
        this.port = port;
    }

    public static GenericRealmAuth factory_create_realm( /*Role role,*/ AccountConnector act )
    {
        GenericRealmAuth realm = null;
        
        if (act.getType().compareTo("dbs") == 0)
        {
            realm = new DBSAuth( act.getMandant() );
        }
        if (act.getType().compareTo("ad") == 0)
        {
            realm = new ActiveDirectoryAuth( act.getUsername(), act.getPwd(), act.getIp(), act.getSearchbase(), act.getPort(), act.getFlags() );
        }
        if (act.getType().compareTo("ldap") == 0)
        {
            realm = new LDAPAuth( act.getUsername(), act.getPwd(), act.getIp(), act.getSearchbase(), act.getPort(), act.getFlags(), act.getSearchattribute(), act.getMailattribute() );
        }
        if (act.getType().compareTo("smtp") == 0)
        {
            realm = new SMTPAuth( act.getIp(), act.getPort(), act.getFlags() );
        }
        if (act.getType().compareTo("pop") == 0)
        {
            realm = new POP3Auth( act.getIp(), act.getPort(), act.getFlags() );
        }
        if (act.getType().compareTo("imap") == 0)
        {
            realm = new IMAPAuth( act.getIp(), act.getPort(), act.getFlags() );            
        }
        
        if (realm != null)
            realm.set_params(act);

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

    // THIS IS OVERRIDDEN IN LDAP
    public ArrayList<String> list_mailaliases_for_userlist( ArrayList<String> users ) throws NamingException
    {
        ArrayList<String>mail_list = new ArrayList<String>();

        Set<MailUser> mail_user = act.getMandant().getMailusers();
        for (Iterator<MailUser> it = mail_user.iterator(); it.hasNext();)
        {
            MailUser mailUser = it.next();

            for (int i = 0; i < users.size(); i++)
            {
                String user = users.get(i);

                // IS IT CLEVER TO COMPARE W/O CASE ????
                if (mailUser.getUsername().compareToIgnoreCase(user) == 0)
                {
                    // ADD NATIVE EMAIL
                    mail_list.add(mailUser.getEmail());

                    // ADD ALIASES
                    Set<MailAddress> add_email = mailUser.getAddMailAddresses();
                    if (add_email != null)
                    {
                        for (Iterator<MailAddress> it1 = add_email.iterator(); it1.hasNext();)
                        {
                            MailAddress mailAddress = it1.next();
                            mail_list.add(mailAddress.getEmail());
                        }
                    }
                }
            }
        }
        return mail_list;
    }


    public ArrayList<String> list_users_for_group( String group ) throws NamingException
    {
        ArrayList<String>users = new ArrayList<String>();

        Set<MailUser> mail_users = act.getMandant().getMailusers();
        for (Iterator<MailUser> it = mail_users.iterator(); it.hasNext();)
        {
            MailUser mailUser = it.next();
            if ((mailUser.getFlags() & CS_Constants.ACCT_DISABLED) == CS_Constants.ACCT_DISABLED)
                continue;

            users.add( mailUser.getUsername() );
        }
        return users;
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
//            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "true");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(with_cert));
        }
        else if (test_flag( CS_Constants.ACCT_USE_TLS_FORCE))
        {
//            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(with_cert));
        }
        else if (test_flag( CS_Constants.ACCT_USE_SSL))
        {
            protocol = protocol + "s";
            props.put("mail." + protocol + ".socketFactory.port", port);
        }
        
        if (test_flag( CS_Constants.ACCT_HAS_TLS_CERT))
        {
            String ca_cert_file = System.getProperty("javax.net.ssl.trustStore");
            props.put("javax.net.ssl.trustStore", ca_cert_file);
        }

        // DEFAULTTIMOUT IS 10 S
        // FAILS ON IMAP LOGIN
        props.put("mail." + protocol + ".connectiontimeout", 10 * 1000);
        props.put("mail." + protocol + ".timeout", 300 * 1000);

        props.put( "mail.debug", "false");
        if (LogManager.get_debug_lvl() > 5)
            props.put( "mail.debug", "true");


        return props;
    }

    boolean is_ssl()
    {
        return test_flag( CS_Constants.ACCT_USE_SSL );
    }

    protected boolean test_flag( int test_flag )
    {
        return (flags & test_flag) == test_flag;
    }

    public ArrayList<String> get_mailaliaslist_for_user( String user ) throws NamingException
    {

        ArrayList<String> user_list = new ArrayList<String>();
        user_list.add(user);
        ArrayList<String> mail_list = list_mailaliases_for_userlist(  user_list );
        if ((act.getFlags() & CS_Constants.ACCT_USER_IS_MAIL) == CS_Constants.ACCT_USER_IS_MAIL)
        {
            if (!mail_list.contains(user))
            {
                mail_list.add(user);
            }
        }
        return mail_list;
    }

    String get_dbs_mail_for_user( String user )
    {
        String ret = null;

        Set<MailUser> mail_users = act.getMandant().getMailusers();
        for (Iterator<MailUser> it = mail_users.iterator(); it.hasNext();)
        {
            MailUser mailUser = it.next();

            if (mailUser.getUsername().compareTo(user) == 0)
            {
                // ADD NATIVE EMAIL
                ret = mailUser.getEmail();
                break;
            }
        }
        // IF NOT IN DB WE CHECK IF FLAG (USER IS MAIL) IS SET
        if (ret == null && (act.getFlags() & CS_Constants.ACCT_USER_IS_MAIL) == CS_Constants.ACCT_USER_IS_MAIL)
        {
            ret = get_mail_from_user( ret );
        }
        return ret;
    }
    String get_mail_from_user( String user )
    {
        String ret = null;

        if ((act.getFlags() & CS_Constants.ACCT_USER_IS_MAIL) == CS_Constants.ACCT_USER_IS_MAIL)
        {
            if (act.getMailattribute() != null && act.getMailattribute().length() > 0)
            {
                if (user.indexOf('@') == -1 && act.getMailattribute().indexOf('@') == -1)
                    ret = user + "@" + act.getMailattribute();
                else
                    ret = user + act.getMailattribute();
            }
            else
            {
                ret = user;
            }
        }
        return ret;
    }



    public static boolean user_is_member_of( Role role, String user, ArrayList<String> mail_list )
    {
        // CREATE FILTER VALUE PROVIDER
        UserFilterProvider f_provider = new UserFilterProvider(user, mail_list );

        // GET FILTER STR AND PARSE TO ARRAYLIST
        String compressed_list_str = role.getAccountmatch();
        int role_flags = 0;
        try
        {
            role_flags = Integer.parseInt(role.getFlags());
        }
        catch (NumberFormatException numberFormatException)
        {
        }

        boolean compressed = (role_flags & CS_Constants.ROLE_ACM_COMPRESSED) == CS_Constants.ROLE_ACM_COMPRESSED;
        ArrayList<LogicEntry> logic_list = FilterMatcher.get_filter_list( compressed_list_str, compressed );
        if (logic_list == null)
        {
            LogManager.err_log(Main.Txt("Invalid_role_filter"));
            return false;
        }

        // CREATE FILTER AND EVAL FINALLY
        FilterMatcher matcher = new FilterMatcher( logic_list , f_provider);
        boolean ret = matcher.eval();

        LogManager.debug( "User " + user + " is " + (ret?"":"not ") + "member of role " + role.getName());

        return ret;
    }

}
class UserFilterProvider implements FilterValProvider
{
    String user;
    ArrayList<String> mail_list;

    UserFilterProvider( String user, ArrayList<String> mail_list )
    {
        this.user = user;
        this.mail_list = mail_list;
    }

    @Override
    public ArrayList<String> get_val_vor_name( String name )
    {
        ArrayList<String> list = null;
        if (name.toLowerCase().compareTo("username") == 0)
        {
            list = new ArrayList<String>();
            list.add(user);

        }
        if (name.toLowerCase().compareTo("email") == 0)
        {
            list = mail_list;
        }
        if (name.toLowerCase().compareTo("domain") == 0)
        {
            list = new ArrayList<String>();
            for (int i = 0; i < mail_list.size(); i++)
            {
                String mail = mail_list.get(i);
                int idx = mail.indexOf('@');
                if (idx > 0 && idx < mail.length() - 1)
                    list.add(mail.substring(idx + 1));
            }
        }
        return list;
    }
}

