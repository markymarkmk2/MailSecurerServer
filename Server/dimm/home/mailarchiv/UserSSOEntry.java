/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv;

import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.Role;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public class UserSSOEntry
{

    String user;
    String pwd;
    Role role;
    AccountConnector acct;
    long checked;
    long last_auth;
    ArrayList<String> mail_list;
    int user_sso_id;

    public UserSSOEntry( String user, String pwd, Role role, AccountConnector acct, long checked, long last_auth, int _id )
    {
        this.user = user;
        this.pwd = pwd;
        this.role = role;
        this.acct = acct;
        this.checked = checked;
        this.last_auth = last_auth;
        user_sso_id = _id;
    }

    public AccountConnector getAcct()
    {
        return acct;
    }

    public Role getRole()
    {
        return role;
    }

    public ArrayList<String> getMail_list()
    {
        return mail_list;
    }
    
    // ADMIN HAS NO REGULAR AUTH
    public boolean is_admin()
    {
        return (role == null && acct == null);
    }
    
}
