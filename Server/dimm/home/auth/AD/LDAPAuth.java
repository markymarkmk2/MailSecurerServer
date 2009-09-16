/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth.AD;

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



class UserContext
{
    String dn;
    LdapContext ctx;

    public UserContext( String sid, LdapContext ctx )
    {
        this.dn = sid;
        this.ctx = ctx;
    }
}

public class LDAPAuth
{

    String admin_name;
    String admin_pwd;
    String ldap_host;
    int ldap_port;
    boolean ssl;
    LdapContext ctx;
    String error_txt;

    public LDAPAuth( String admin_name, String admin_pwd, String ldap_host, int ldap_port, boolean ssl )
    {
        this.admin_name = admin_name;
        this.admin_pwd = admin_pwd;
        this.ldap_host = ldap_host;
        this.ldap_port = ldap_port;
        this.ssl = ssl;
        if (ldap_port == 0)
        {
            ldap_port = (ssl) ? 636 : 389;
        }
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
    }

    public boolean is_connected()
    {
        return (ctx != null);
    }

    public boolean connect()
    {
        try
        {
            Hashtable env = new Hashtable();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            //        env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");

            env.put(Context.SECURITY_PRINCIPAL, admin_name);
            env.put(Context.SECURITY_CREDENTIALS, admin_pwd);

            if (ssl)
            {
                env.put(Context.SECURITY_PROTOCOL, "ssl");
            }

            //Der entsprechende Domänen-Controller:LDAP-Port
            env.put(Context.PROVIDER_URL, "ldap://" + ldap_host + ":" + ldap_port);

            ctx = new InitialLdapContext(env, null);
            return true;
        }
        catch (Exception exc)
        {
            error_txt = exc.getMessage();
        }
        return false;
    }

    UserContext open_user( String user_principal, String pwd )
    {
        try
        {

            SearchControls ctrl = new SearchControls();
            ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctrl.setReturningAttributes(new String[]
                    {
                        "distinguishedName"
                    });


            Attributes attributes = ctx.getAttributes(ctx.getNameInNamespace());
            Attribute attribute = attributes.get("defaultNamingContext");
            String rootSearchBase =  "CN=Users," + attribute.get().toString();

            String searchFilter = "(&(objectCategory=person)(objectClass=user)(cn=" + user_principal + "))";
            int cnt  = 0;

            NamingEnumeration<SearchResult> enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectCategory=person)(objectClass=user)(name=" + user_principal + "))";
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectCategory=person)(objectClass=user)(userPrincipalName=" + user_principal + "))";
                enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            }
            else
            {
                cnt++;
            }
            if (!enumeration.hasMore())
            {
                searchFilter = "(&(objectCategory=person)(objectClass=user)(mail=" + user_principal + "))";
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
            Attribute adn = res_attr.get("distinguishedName");
            String user_dn = adn.get().toString();
            

            // NOW GO FOR LOGIN USER WITH DN
            LdapContext user_ctx;
            Hashtable env = new Hashtable();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            //        env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");

            env.put(Context.SECURITY_PRINCIPAL, user_dn);
            env.put(Context.SECURITY_CREDENTIALS, pwd);
            //        env.put(Context.SECURITY_PROTOCOL, "ssl");

            
            env.put(Context.PROVIDER_URL, "ldap://" + ldap_host + ":" + ldap_port);

            user_ctx = new InitialLdapContext(env, null);
          
            return new UserContext(user_dn, user_ctx);
        }
        catch (Exception namingException)
        {
            namingException.printStackTrace();
        }
        return null;
    }

    String get_user_attribute( UserContext uctx, String attr_name )
    {
        Attributes search_attributes = new BasicAttributes("distinguishedName", uctx.dn);

        String[] return_attributes = new String[1];
        return_attributes[0] = attr_name;

        try
        {
            Attributes ctx_attributes = ctx.getAttributes(ctx.getNameInNamespace());
            Attribute ctx_attribute = ctx_attributes.get("defaultNamingContext");
            String rootSearchBase = "CN=Users," + ctx_attribute.get().toString();
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

    void close_user( UserContext uctx )
    {
        try
        {
            uctx.ctx.close();
        }
        catch (NamingException namingException)
        {
        }
    }

    /**
     * @param args
     */
    public static void main( String[] args )
    {
        try
        {
            LDAPAuth test = new LDAPAuth("Administrator", "helikon", "192.168.1.120", 0, /*ssl*/false);
            if (test.connect())
            {
                UserContext uctx = test.open_user( "mark@localhost", "12345" );
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
