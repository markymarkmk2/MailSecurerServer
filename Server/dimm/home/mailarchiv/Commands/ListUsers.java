/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Commands;

import com.thoughtworks.xstream.XStream;
import dimm.home.auth.GenericRealmAuth;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import home.shared.Utilities.ZipUtilities;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.Mandant;
import home.shared.hibernate.Role;
import java.util.ArrayList;
import java.util.Iterator;
import javax.naming.NamingException;

/**
 *
 * @author mw
 */
public class ListUsers extends AbstractCommand
{

    public ListUsers()
    {
        super("ListUsers");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts(data);

        ParseToken pt = new ParseToken(opt);

        String command = pt.GetString("CMD:");
        if (command.compareTo("match_filter") == 0)
        {
            int m_id = (int) pt.GetLongValue("MA:");
            int ac_id = (int) pt.GetLongValue("AC:");
            String role_filter_compressed = pt.GetString("FLC:");

            MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
            Mandant mandant = m_ctx.getMandant();



            // PRUEFE FÜR ALLE ROLLEN DIESES MANDANTEN
            for (Iterator<AccountConnector> it = mandant.getAccountConnectors().iterator(); it.hasNext();)
            {
                AccountConnector acct = it.next();
                if (acct.getId() != ac_id)
                {
                    continue;
                }
                String realm_name = acct.getType() + "://" + acct.getIp() + ":" + acct.getPort();

                ArrayList<String> user_list = new ArrayList<String>();

                GenericRealmAuth auth_realm = null;
                ArrayList<String> result_list = new ArrayList<String>();
                try
                {
                    auth_realm = GenericRealmAuth.factory_create_realm(acct);
                    if (!auth_realm.connect())
                    {
                        LogManager.err_log("Cannot connect to realm " + acct.getType() + ":" + acct.getIp());
                        continue;
                    }

                    ArrayList<String> group_list = auth_realm.list_groups();

                    try
                    {
                        ArrayList<String> local_user_list = auth_realm.list_users_for_group("");
                        user_list.addAll(local_user_list);
                    }
                    catch (Exception exc)
                    {
                        LogManager.debug("list_users_for_group failed: " , exc);
                    }

/*

                    for (int i = 0; i < group_list.size(); i++)
                    {
                        String group = group_list.get(i);
                        try
                        {
                            ArrayList<String> local_user_list = auth_realm.list_users_for_group(group);
                            user_list.addAll(local_user_list);
                        }
                        catch (Exception exc)
                        {
                            exc.printStackTrace();
                            LogManager.debug("list_users_for_group failed for group <" + group + ">: " , exc);
                        }
                        
                    }
*/
                    Role role = new Role(-1);
                    role.setMandant(mandant);
                    role.setAccountConnector(acct);
                    role.setAccountmatch(role_filter_compressed);
                    role.setFlags(Integer.toString(CS_Constants.ROLE_ACM_COMPRESSED));




                    for (int i = 0; i < user_list.size(); i++)
                    {
                        String user = user_list.get(i);

                        ArrayList<String> mail_alias_list = null;
                        try
                        {
                            mail_alias_list = auth_realm.get_mailaliaslist_for_user(user);
                        }
                        catch (NamingException namingException)
                        {
                            System.out.println("naming Exception: " + namingException.getLocalizedMessage());
                        }

                        if (MandantContext.user_is_member_of(role, user, mail_alias_list))
                        {
                            StringBuffer sb = new StringBuffer();
                            sb.append(user);
                            sb.append(" <");
                            for (int j = 0; j < mail_alias_list.size(); j++)
                            {
                                String mail = mail_alias_list.get(j);
                                if (j > 0)
                                {
                                    sb.append(",");
                                }

                                sb.append(mail);
                            }
                            sb.append(">");
                            result_list.add(sb.toString());
                        }
                    }

                }
                catch (Exception namingException)
                {
                    namingException.printStackTrace();
                    LogManager.err_log("Error while getting userlist from realm " + realm_name, namingException);
                }
                finally
                {
                    if (auth_realm != null)
                    {
                        auth_realm.disconnect();
                    }
                }

                XStream xs = new XStream();
                String xml = xs.toXML(result_list);
                String cxml = null;
                cxml = ZipUtilities.compress(xml);

                answer = "0: " + cxml;
                return true;
            }
            answer = "1: Unknown accountconnector";
            return true;
        }
        answer = "2: Unknown subcommand: " + data;
        return true;
    }
}
