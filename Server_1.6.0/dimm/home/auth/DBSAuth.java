/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.hibernate.MailUser;
import home.shared.hibernate.Mandant;
import java.util.Iterator;
import java.util.Set;







class DBSUserContext
{
    MailUser muser;

    public DBSUserContext( MailUser muser )
    {
        this.muser = muser;
    }

}
public class DBSAuth extends GenericRealmAuth
{    
    
    Mandant mandant;
    DBSUserContext user_context;
    boolean connected = false;
    

    DBSAuth( Mandant mandant )
    {
        super(0, "", 0);
        this.mandant = mandant;
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
        connected = true;
        return connected;
    }
    @Override
    public boolean disconnect()
    {
        connected = false;
        return true;
    }
    @Override
    public boolean is_connected()
    {
        return connected;
    }

   

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
        return user_context == null ? false : true;
    }

   
    
    DBSUserContext open_user( String user_principal, String pwd )
    {
        Set<MailUser> mail_user = act.getMandant().getMailusers();
        for (Iterator<MailUser> it = mail_user.iterator(); it.hasNext();)
        {
            MailUser mailUser = it.next();

            // CHECK FOR USEERNAME / PWD
            if (mailUser.getUsername().compareTo(user_principal) == 0 &&
                mailUser.getPwd().compareTo(pwd) == 0)
            {
                return new DBSUserContext(mailUser);
            }
        }
        LogManager.msg_auth( LogManager.LVL_ERR, "DBS auth failed for user: " + user_principal);
        error_txt = Main.Txt("Authentication_failed");

        return null;
    }


    void close_user( DBSUserContext uctx )
    {       
    }

 

    
  
}
