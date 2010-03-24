/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

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





class LDAPUserContext implements UserContext
{
    String dn;
    LdapContext ctx;

    public LDAPUserContext( String sid, LdapContext ctx )
    {
        this.dn = sid;
        this.ctx = ctx;
    }
}

public class LDAPAuth extends GenericRealmAuth
{

    String admin_name;
    String admin_pwd;
    String user_search_base;
    LdapContext ctx;

    String search_attribute;
    String mail_field_list;

    LDAPUserContext user_context;

    LDAPAuth( String admin_name, String admin_pwd, String ldap_host, String user_search_base, int ldap_port, int  flags, String search_attribute, String mfl )
    {
        super(flags, ldap_host, ldap_port);
        this.admin_name = admin_name;
        this.admin_pwd = admin_pwd;
        this.user_search_base = user_search_base;
        this.search_attribute = search_attribute;
        this.mail_field_list = mfl;
        if (mail_field_list == null || mail_field_list.length() == 0)
            mail_field_list = "mail";
        
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
            return user_search_base;
        
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

            connect_env.put(Context.SECURITY_PRINCIPAL, admin_name);
            connect_env.put(Context.SECURITY_CREDENTIALS, admin_pwd);
            

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
        return list_dn_qry("(&(objectClass=group)(name=*))");
    }

    @Override
    public ArrayList<String> list_mailaliases_for_userlist( ArrayList<String> users ) throws NamingException
    {
        // RETURN VALS
        String[] mail_fields_array = mail_field_list.split(",");
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        ctrl.setReturningAttributes(mail_fields_array);
        // USER ROOT
        String rootSearchBase = get_user_search_base();
        // BUILD ORED LIST OF DNs
        StringBuffer ldap_qry = new StringBuffer();
        ldap_qry.append("(&(objectClass=group)(|");
        for (int i = 0; i < users.size(); i++)
        {
            String string = users.get(i);
            ldap_qry.append("(" + search_attribute + "=" + string + ")");
        }
        ldap_qry.append("))");

        LogManager.debug_msg(4, "list_mailaliases_for_userlist: " + rootSearchBase + " " + ldap_qry);

        NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry.toString(), ctrl);
        ArrayList<String> mail_list = new ArrayList<String>();
        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            for (int i = 0; i < mail_fields_array.length; i++)
            {
                String field = mail_fields_array[i];
                String mail = searchResult.getAttributes().get(field).get().toString();
                if (mail != null && mail.length() > 0)
                {
                    if (!mail_list.contains(mail))
                        mail_list.add(mail);
                }
            }

        }
        return mail_list;
    }

    @Override
    public ArrayList<String> list_users_for_group( String group ) throws NamingException
    {
        if (group != null && group.length() > 0)
        {
            String rootSearchBase = get_user_search_base();
            return list_dn_qry("(&(objectClass=user)(memberOf=CN=" + group + "))");
        }
        return list_dn_qry("(objectClass=user)");
    }

    @Override
    public boolean open_user_context( String user_principal, String pwd )
    {
        user_context = open_user(user_principal, pwd);
        return user_context == null ? false : true;
    }


    LDAPUserContext open_user( String user_name, String pwd )
    {
        try
        {

            SearchControls ctrl = new SearchControls();
            ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctrl.setReturningAttributes(new String[]
                    {
                        search_attribute
                    });


            String rootSearchBase = get_user_search_base();

            String searchFilter = "(&(objectClass=user)(" + search_attribute + "=" + user_name + "))";
            int cnt  = 0;

            LogManager.debug_msg(4, "open_user: " + rootSearchBase + " " + searchFilter);
            NamingEnumeration<SearchResult> enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectClass=user)(name=" + user_name + "))";
                LogManager.debug_msg(4, "open_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectClass=user)(userPrincipalName=" + user_name + "))";
                LogManager.debug_msg(4, "open_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectCategory=person)(objectClass=user)(mail=" + user_name + "))";
                LogManager.debug_msg(4, "open_user: " + rootSearchBase + " " + searchFilter);
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            System.out.println("" + cnt);
            // NOT HERE
            if (!enumeration.hasMore())
            {
                return null;
            }

            // NOW GET THE DN FOR THIS USER
            SearchResult sr = enumeration.next(); //This is the enumeration object obtained on step II above
            Attributes res_attr = sr.getAttributes();
            Attribute adn = res_attr.get(search_attribute);
            String user_dn = adn.get().toString();
            

            // NOW GO FOR LOGIN USER WITH DN
            LdapContext user_ctx;
            Hashtable<String,String> connect_env = create_sec_env();


            connect_env.put(Context.SECURITY_PRINCIPAL, user_dn);
            connect_env.put(Context.SECURITY_CREDENTIALS, pwd);

            user_ctx = new InitialLdapContext(connect_env, null);
          
            return new LDAPUserContext(user_dn, user_ctx);
        }
        catch (Exception namingException)
        {
            error_txt = namingException.getMessage();
            namingException.printStackTrace();
        }
        return null;
    }


    String get_user_attribute( LDAPUserContext uctx, String attr_name )
    {
        Attributes search_attributes = new BasicAttributes(search_attribute, uctx.dn);

        String[] return_attributes = new String[1];
        return_attributes[0] = attr_name;

        try
        {
            String rootSearchBase = get_user_search_base();
            LogManager.debug_msg(4, "open_user: " + rootSearchBase + " " + search_attributes);
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

    void close_user( LDAPUserContext uctx )
    {
        try
        {
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
                    search_attribute
                });

        String rootSearchBase = get_user_search_base();

        LogManager.debug_msg(4, "DN_Qry: " + rootSearchBase + " " + ldap_qry);
        NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry, ctrl);

        ArrayList<String> dn_list = new ArrayList<String>();

        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            dn_list.add( searchResult.getAttributes().get(search_attribute).get().toString() );
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
            LDAPAuth test = new LDAPAuth("Administrator", "helikon", "192.168.1.120", "", 0, /*flags*/0, "uid", "mail");
            if (test.connect())
            {
                LDAPUserContext uctx = test.open_user( "mark@localhost", "12345" );
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
