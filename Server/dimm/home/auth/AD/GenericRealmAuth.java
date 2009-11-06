/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.auth.AD;

import home.shared.CS_Constants;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.Role;
import java.util.ArrayList;
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
  //  Role role;
    AccountConnector act;

    void set_params( /*Role role,*/ AccountConnector act )
    {
    //    this.role = role;
        this.act = act;
    }

    public static GenericRealmAuth factory_create_realm( /*Role role,*/ AccountConnector act )
    {
        GenericRealmAuth realm = null;
        if (act.getType().compareTo("ldap") == 0)
        {
            boolean use_ssl = (act.getFlags() & CS_Constants.ACCT_USE_SSL ) == CS_Constants.ACCT_USE_SSL;

            realm = new LDAPAuth( act.getUsername(), act.getPwd(), act.getIp(), act.getPort(), use_ssl );
            realm.set_params(/*role,*/ act);
        }
        /*
        if (act.getType().compareTo("smtp") == 0)
        {
            boolean use_ssl = (act.getFlags() & CS_Constants.ACCT_USE_SSL ) == CS_Constants.ACCT_USE_SSL;

            realm = new SMTPAuth( act.getUsername(), act.getPwd(), act.getIp(), act.getPort(), use_ssl );
            realm.set_params(role, act);
        }*/

        return realm;
    }


    public abstract boolean connect();
    public abstract boolean disconnect();
    public abstract boolean is_connected();
   
    public abstract boolean open_user_context( String user_principal, String pwd );
    public abstract void close_user_context();

    public abstract String get_error_txt();

    public abstract String get_user_attribute( String attr_name );

    public abstract ArrayList<String> list_groups() throws NamingException;
    public abstract ArrayList<String> list_mails_for_userlist( ArrayList<String> users ) throws NamingException;
    public abstract ArrayList<String> list_users_for_group( String group ) throws NamingException;


}
