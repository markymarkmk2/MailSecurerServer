/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;





class ADUserContext implements UserContext
{
    String dn;
    LdapContext ctx;

    public ADUserContext( String sid, LdapContext ctx )
    {
        this.dn = sid;
        this.ctx = ctx;
    }
}

public class ActiveDirectoryAuth extends GenericRealmAuth
{

    String admin_name;
    String admin_pwd;
    String user_search_base;
    LdapContext ctx;

    public static final String DN = "distinguishedName";

    ADUserContext user_context;

    ActiveDirectoryAuth( String admin_name, String admin_pwd, String ldap_host, String user_search_base, int ldap_port, int  flags )
    {
        super(flags, ldap_host, ldap_port);
        this.admin_name = admin_name;
        this.admin_pwd = admin_pwd;
        this.user_search_base = user_search_base;
        
        this.flags = flags;
        if (ldap_port == 0)
        {
            ldap_port = is_ssl() ? 636 : 389;
        }
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    }

    String get_user_search_base() throws NamingException
    {
        if (user_search_base != null && user_search_base.length() > 0)
        {
            // DO WE HAVE AN ABSOLUTE DN?
            if (user_search_base.toUpperCase().contains("DC="))
                return user_search_base;

            // NO, WE NEED OUT NAMING CONTEXT
            Attributes attributes = ctx.getAttributes(ctx.getNameInNamespace());
            Attribute attribute = attributes.get("defaultNamingContext");
            return user_search_base + "," + attribute.get().toString();
        }
        
        Attributes attributes = ctx.getAttributes(ctx.getNameInNamespace());
        Attribute attribute = attributes.get("defaultNamingContext");
        return "CN=Users," + attribute.get().toString();
    }


    @Override
    public void close_user_context()
    {
        close_user(user_context);
        user_context = null;
    }
//Gruppen für Rollenverwaltung

    Hashtable<String,String> create_sec_env()
    {
        Hashtable<String,String> env = new Hashtable<String,String>();
        String protokoll = "ldap://";
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        env.put("com.sun.jndi.ldap.connect.timeout", "10000");
        //        env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");

        if (is_ssl())
        {
            protokoll = "ldaps://";
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            String java_home = System.getProperty("java.home").trim();
            String ca_cert_file = java_home + "/lib/security/cacerts";
            System.setProperty("javax.net.ssl.trustStore", ca_cert_file);
            env.put("javax.net.ssl.trustStore", ca_cert_file);
        }
        //Der entsprechende Domänen-Controller:LDAP-Port
        env.put(Context.PROVIDER_URL, protokoll + host + ":" + port);
        return env;

    }

    
    @Override
    public boolean connect()
    {
        try
        {            

            Hashtable<String,String> connect_env = create_sec_env();
            String login_name = admin_name;
            if (act.getLdapdomain() != null && act.getLdapdomain().length() > 0 && admin_name.indexOf('@') == -1)
            {
                login_name += "@" + act.getLdapdomain();
            }

            connect_env.put(Context.SECURITY_PRINCIPAL, login_name);
            connect_env.put(Context.SECURITY_CREDENTIALS, admin_pwd);
           // connect_env.put("java.naming.ldap.version", "2");
            

            ctx = new InitialLdapContext(connect_env, null);
            return true;
        }
        catch (Exception exc)
        {
            error_txt = exc.getMessage();
            exc.printStackTrace();
        }
        return false;
    }
    @Override
    public boolean disconnect()
    {
        try
        {
            if (ctx != null)
            {
                ctx.close();
            }
            ctx = null;
            return true;
        }
        catch (Exception exc)
        {
            error_txt = exc.getMessage();
        }
        return false;
    }

   

    @Override
    public String get_user_attribute( String attr_name )
    {
        return get_user_attribute(user_context, attr_name);
    }

    @Override
    public boolean is_connected()
    {
        return ctx != null;
    }

    @Override
    public ArrayList<String> list_groups() throws NamingException
    {
        return list_attribute_qry("(&(objectClass=group)(name=*))", "name");
    }

    @Override
    public ArrayList<String> list_mailaliases_for_userlist( ArrayList<String> users ) throws NamingException
    {
        // RETURN VALS
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        ctrl.setReturningAttributes(new String[]{"mail"});
        //ctrl.setReturningAttributes(new String[]{"mail", "userPrincipalName", "proxyAddresses", "otherMailbox"});
        // USER ROOT
        String rootSearchBase = get_user_search_base();
        // BUILD ORED LIST OF DNs
        StringBuffer ldap_qry = new StringBuffer();
        ldap_qry.append("(&(objectClass=user)");
        if (users.size() > 1)
            ldap_qry.append( "(|" );

        for (int i = 0; i < users.size(); i++)
        {
            String string = users.get(i);
            if (act.getLdapdomain() != null && act.getLdapdomain().length() > 0)
            {
                string += "@" + act.getLdapdomain();
            }
            ldap_qry.append("(userPrincipalName=" + string + ")");
        }
        if (users.size() > 1)
            ldap_qry.append( ")" );
        ldap_qry.append(")");
        String ldap_qry_txt = ldap_qry.toString();
        LogManager.msg_auth( LogManager.LVL_DEBUG, "list_mailaliases_for_userlist: " + rootSearchBase + " " + ldap_qry_txt);

        NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry_txt, ctrl);
        ArrayList<String> mail_list = new ArrayList<String>();
        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            Attribute attr = searchResult.getAttributes().get("mail");
            if (attr != null)
            {
                String mail = searchResult.getAttributes().get("mail").get().toString();
                if (mail != null && mail.length() > 0)
                {
                    if (!mail_list.contains(mail))
                            mail_list.add(mail);
                }
            }
            // proxyAddresses ARE CODED SMTP:mail@domain.com
            attr = searchResult.getAttributes().get("proxyAddresses");
            if (attr != null)
            {
                String proxyAddresses = attr.get().toString();
                if (proxyAddresses != null && proxyAddresses.length() > 0)
                {
                    if (proxyAddresses.toLowerCase().startsWith("smtp:"))
                    {
                        String m = proxyAddresses.substring(5);
                        if (!mail_list.contains(m))
                            mail_list.add(m);
                    }
                }
            }
            attr = searchResult.getAttributes().get("userPrincipalName");
            if (attr != null)
            {
                String upn = attr.get().toString();
                if (upn != null && upn.length() > 0)
                {
                    if (!mail_list.contains(upn))
                        mail_list.add(upn);
                }
            }
            attr = searchResult.getAttributes().get("otherMailbox");
            if (attr != null)
            {
                String other_mb = attr.get().toString();
                if (other_mb != null && other_mb.length() > 0)
                {
                    if (!mail_list.contains(other_mb))
                        mail_list.add(other_mb);
                }
            }
        }
        if (mail_list.size() == 0)
        {
            LogManager.msg_auth( LogManager.LVL_DEBUG, "No mail entries found for query: " + rootSearchBase + " " + ldap_qry);
        }
        return mail_list;
    }

    @Override
    public ArrayList<String> list_users_for_group( String group ) throws NamingException
    {
        if (group != null && group.length() > 0)
        {            
            return list_attribute_qry("(&(objectClass=user)(memberOf=" + group + "))", "name");
        }
        return list_attribute_qry("(objectClass=user)", "name");
    }

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
        return user_context == null ? false : true;
    }


    ADUserContext open_user( String user_principal, String pwd )
    {
        String rootSearchBase = "?";
        SearchResult sr = null;
        try
        {
            SearchControls ctrl = new SearchControls();
            ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctrl.setReturningAttributes(new String[]
                    {
                        DN
                    });

            rootSearchBase = get_user_search_base();

            String searchFilter = "(&(objectCategory=person)(objectClass=user)(cn=" + user_principal + "))";
            int cnt  = 0;

            LogManager.msg_auth( LogManager.LVL_DEBUG, "open_user: " + rootSearchBase + " " + searchFilter);
            NamingEnumeration<SearchResult> enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectCategory=person)(objectClass=user)(name=" + user_principal + "))";
                LogManager.msg_auth( LogManager.LVL_DEBUG, "search_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                String login_name = user_principal;
                if (act.getLdapdomain() != null && act.getLdapdomain().length() > 0 && login_name.indexOf('@') == -1)
                {
                    login_name += "@" + act.getLdapdomain();
                }

                searchFilter = "(&(objectCategory=person)(objectClass=user)(userPrincipalName=" + login_name + "))";
                LogManager.msg_auth( LogManager.LVL_DEBUG, "search_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                String login_name = user_principal;
                if (act.getLdapdomain() != null && act.getLdapdomain().length() > 0 && login_name.indexOf('@') == -1)
                {
                    login_name += "@" + act.getLdapdomain();
                }
                searchFilter = "(&(objectCategory=person)(objectClass=user)(mail=" + login_name + "))";
                LogManager.msg_auth( LogManager.LVL_DEBUG, "search_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            //System.out.println("" + cnt);
            // NOT HERE
            if (!enumeration.hasMore())
            {
                return null;
            }
            sr = enumeration.next(); //This is the enumeration object obtained on step II above

        }
        catch (Exception namingException)
        {
            if (namingException instanceof javax.naming.NameNotFoundException)
            {
                String searchFilter = "(&(objectCategory=person)(objectClass=user)(cn=" + user_principal + "))";
                error_txt = Main.Txt("User_konnte_nicht_im_AD_gefunden_werden") + ": " + rootSearchBase + " -> " + searchFilter;
            }
            else
            {
                error_txt = namingException.getMessage();
            }
            return null;
        }

        // sr CONTAINS THE SEARCH RESULT WITH THE USER DATA
        try
        {

            // NOW GET THE DN FOR THIS USER
            
            Attributes res_attr = sr.getAttributes();
            Attribute adn = res_attr.get(DN);
            String user_dn = adn.get().toString();
                      

            // NOW GO FOR LOGIN USER WITH DN
            LdapContext user_ctx;
            Hashtable<String,String> connect_env = create_sec_env();


            connect_env.put(Context.SECURITY_PRINCIPAL, user_dn);
            connect_env.put(Context.SECURITY_CREDENTIALS, pwd);

            LogManager.msg_auth( LogManager.LVL_DEBUG, "Found user trying to connect: " + user_dn );

            user_ctx = new InitialLdapContext(connect_env, null);

            LogManager.msg_auth( LogManager.LVL_DEBUG, "User connected successfully: " + user_dn );

            return new ADUserContext(user_dn, user_ctx);
        }
        catch (Exception namingException)
        {
            error_txt = namingException.getMessage();
            LogManager.msg_auth( LogManager.LVL_WARN, "User connect failed: " + error_txt );
        }
        return null;
    }


    String get_user_attribute( ADUserContext uctx, String attr_name )
    {
        Attributes search_attributes = new BasicAttributes(DN, uctx.dn);

        String[] return_attributes = new String[1];
        return_attributes[0] = attr_name;

        try
        {
            String rootSearchBase = get_user_search_base();
            LogManager.msg_auth( LogManager.LVL_DEBUG, "get_user_attribute: " + rootSearchBase + " " + search_attributes);
            NamingEnumeration<SearchResult> results = uctx.ctx.search(rootSearchBase, search_attributes, return_attributes);
            if (results.hasMoreElements())
            {
                SearchResult searchResult = (SearchResult) results.nextElement();
                return searchResult.getAttributes().get(attr_name).get().toString();
            }
        }
        catch (NamingException namingException)
        {
            namingException.printStackTrace();
        }
        return null;
    }

    void close_user( ADUserContext uctx )
    {
        try
        {
            if (uctx != null && uctx.ctx != null)
                uctx.ctx.close();
        }
        catch (NamingException namingException)
        {
        }
    }

    ArrayList<String> list_dn_qry( String ldap_qry ) throws NamingException
    {
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        ctrl.setReturningAttributes(new String[]
                {
                    DN
                });

        String rootSearchBase = get_user_search_base();

        LogManager.msg_auth( LogManager.LVL_DEBUG, "DN_Qry: " + rootSearchBase + " " + ldap_qry);
        NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry, ctrl);

        ArrayList<String> dn_list = new ArrayList<String>();

        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            dn_list.add( searchResult.getAttributes().get(DN).get().toString() );
        }

        return dn_list;
    }
    ArrayList<String> list_attribute_qry( String ldap_qry, String attributname ) throws NamingException
    {
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        ctrl.setReturningAttributes(new String[]
                {
                    attributname
                });

        String rootSearchBase = get_user_search_base();

        LogManager.msg_auth( LogManager.LVL_DEBUG, "DN_Qry: " + rootSearchBase + " " + ldap_qry);
        NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry, ctrl);

        ArrayList<String> dn_list = new ArrayList<String>();

        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            dn_list.add( searchResult.getAttributes().get(attributname).get().toString() );
        }

        return dn_list;
    }
    

    /**
     * @param args
     */
    public static void main( String[] args )
    {
        try
        {
            ActiveDirectoryAuth test = new ActiveDirectoryAuth("Administrator", "helikon", "192.168.1.120", "", 0, /*flags*/0);
            if (test.connect())
            {
                ADUserContext uctx = test.open_user( "mark@localhost", "12345" );
                if (uctx != null)
                {
                    String mail = test.get_user_attribute(uctx, "mail");

                    test.close_user(uctx);
                }
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }
    }  
}
