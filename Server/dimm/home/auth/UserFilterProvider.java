/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import home.shared.filter.FilterValProvider;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public class UserFilterProvider implements FilterValProvider
{

    String user;
    ArrayList<String> mail_list;

    public UserFilterProvider( String user, ArrayList<String> mail_list )
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
                {
                    list.add(mail.substring(idx + 1));
                }
            }
        }
        return list;
    }
}
