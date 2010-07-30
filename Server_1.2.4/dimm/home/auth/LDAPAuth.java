/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.util.ArrayList;
import java.util.Hashtable;

import java.util.StringTokenizer;
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

    public static final Hashtable<Integer,String> ldap_error_map = new Hashtable<Integer, String>();

    void init_ldap_error_list()
    {
        ldap_error_map.put( 0, "successful");
        ldap_error_map.put( 1, "operations error");
        ldap_error_map.put( 2, "protocol error");
        ldap_error_map.put( 3, "timelimit exceed");
        ldap_error_map.put( 4, "sizelimit exceeded");
        ldap_error_map.put( 5, "compare false");
        ldap_error_map.put( 6, "compare true");
        ldap_error_map.put( 7, "strong auth not supported");
        ldap_error_map.put( 8, "strong auth required");
        ldap_error_map.put( 9, "partial results");
        ldap_error_map.put( 10,"referral");
        ldap_error_map.put( 11,"admin limit exceeded");
        ldap_error_map.put( 16,"no such attribute");
        ldap_error_map.put( 17,"undefined type");
        ldap_error_map.put( 18,"inappropriate matching");
        ldap_error_map.put( 19,"constraint violation");
        ldap_error_map.put( 20,"type or value exists");
        ldap_error_map.put( 21,"invalid syntax");
        ldap_error_map.put( 32,"no such object");
        ldap_error_map.put( 33,"alias problem");
        ldap_error_map.put( 34,"invalid DN syntax");
        ldap_error_map.put( 35,"is leaf");
        ldap_error_map.put( 36,"alias deref problem");
        ldap_error_map.put( 48,"inappropriate auth");
        ldap_error_map.put( 49,"invalid credentials");
        ldap_error_map.put( 50,"insufficient access");
        ldap_error_map.put( 51,"busy");
        ldap_error_map.put( 52,"unavailable");
        ldap_error_map.put( 53,"unwilling to perform");
        ldap_error_map.put( 54,"loop detect");
        ldap_error_map.put( 64,"naming violation");
        ldap_error_map.put( 65,"object class violation");
        ldap_error_map.put( 66,"not allowed on nonleaf");
        ldap_error_map.put( 67,"not allowed on RDN");
        ldap_error_map.put( 68,"already exists");
        ldap_error_map.put( 69,"no object class mods");
        ldap_error_map.put( 70,"results too large");
        ldap_error_map.put( 80,"other");
        ldap_error_map.put( 81,"server down");
        ldap_error_map.put( 82,"local error");
        ldap_error_map.put( 83,"encoding error");
        ldap_error_map.put( 84,"decoding error");
        ldap_error_map.put( 85,"timeout");
        ldap_error_map.put( 86,"auth unknown");
        ldap_error_map.put( 87,"filter error");
        ldap_error_map.put( 88,"user cancelled");
        ldap_error_map.put( 89,"param error");
        ldap_error_map.put( 90,"no memory");
        ldap_error_map.put( 91,"connect error");
    }
    int get_ldap_err_from_exc( Exception exc )
    {
        int idx = exc.getMessage().indexOf("error code");
        if (idx >= 0)
        {
            StringTokenizer str = new StringTokenizer(exc.getMessage().substring(idx + 11), " " );
            if (str.hasMoreTokens())
            {
                try
                {
                    int err_code = Integer.parseInt(str.nextToken());
                    return err_code;
                }
                catch (NumberFormatException numberFormatException)
                {
                }
            }
        }
        return -1;
    }
    String get_ldap_err_text( Exception exc )
    {
        int ldap_err = get_ldap_err_from_exc(exc);
        String ret = ldap_error_map.get(ldap_err);
        if (ret == null)
            ret = exc.getMessage();

        return ret;
    }

    LDAPAuth( String admin_name, String admin_pwd, String ldap_host, String user_search_base, int ldap_port, int  flags, String search_attribute, String mfl )
    {
        super(flags, ldap_host, ldap_port);
        this.admin_name = admin_name;
        this.admin_pwd = admin_pwd;
        this.user_search_base = user_search_base;
        this.search_attribute = search_attribute;
        if (this.search_attribute == null  || this.search_attribute.length() == 0)
            this.search_attribute = "cn";

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

        if (ctx != null)
        {
            Attributes attributes = ctx.getAttributes(ctx.getNameInNamespace());
            String ret = "CN=Users";
            Attribute attribute = attributes.get("defaultNamingContext");
            if (attribute != null && attribute.get() != null)
                ret += "," + attribute.get().toString();
            return ret;
        }
        return "CN=Users";
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
            String rootSearchBase = get_user_search_base();

            String admin_dn = null;
            
            // ABSOLUTE DN?  (cn=manager,dc=test,dc=de)
            if (admin_name.toLowerCase().indexOf("dc=") >= 0)
            {
                admin_dn =  admin_name;
            }
            else
            {
                // NO, JUST ATTRIB NAME
                admin_dn =  "cn=" + admin_name + "," + rootSearchBase;
            }
           
            LogManager.msg_auth( LogManager.LVL_DEBUG, "connect: " + admin_dn);
            connect_env.put(Context.SECURITY_PRINCIPAL, admin_dn);
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
        return list_dn_qry("(" + search_attribute + "=*)");
    }

    @Override
    public ArrayList<String> list_mailaliases_for_userlist( ArrayList<String> users ) throws NamingException
    {
        ArrayList<String> mail_list = new ArrayList<String>();
        if (users.size() == 0)
            return mail_list;
        // RETURN VALS


        String[] mail_fields_array = mail_field_list.split(",");
        for ( int m = 0; m < mail_fields_array.length; m++)
        {
            SearchControls ctrl = new SearchControls();
            ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctrl.setReturningAttributes(new String[] {mail_fields_array[m]});
            // USER ROOT
            String rootSearchBase = get_user_search_base();
            // BUILD ORED LIST OF DNs
            StringBuffer ldap_qry = new StringBuffer();
            if (users.size() > 1)
            {
                ldap_qry.append("(|");
                for (int i = 0; i < users.size(); i++)
                {
                    String string = users.get(i);
                    ldap_qry.append("(" + search_attribute + "=" + string + ")");
                }
                ldap_qry.append(")");
            }
            else
            {
                ldap_qry.append("(" + search_attribute + "=" + users.get(0) + ")");
            }

            LogManager.msg_auth( LogManager.LVL_DEBUG, "list_mailaliases_for_userlist: " + rootSearchBase + " " + ldap_qry);

            NamingEnumeration<SearchResult> results = ctx.search(rootSearchBase, ldap_qry.toString(), ctrl);
            while (results.hasMoreElements())
            {
                SearchResult searchResult = (SearchResult) results.nextElement();
                
                String field = mail_fields_array[m];
                String mail = null;
                Attribute field_attribute = searchResult.getAttributes().get(field);
                if ( field_attribute != null)
                {
                    mail = field_attribute.get().toString();
                }
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
            return list_dn_qry("(&(" + search_attribute + "=*)(memberOf=CN=" + group + "))");
        }
        return list_dn_qry("(" + search_attribute + "=*)");
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
                        search_attribute, "cn"
                    });

            String rootSearchBase = get_user_search_base();

            String searchFilter = "(" + search_attribute + "=" + user_name + ")";
            int cnt  = 0;

            LogManager.msg_auth( LogManager.LVL_DEBUG, "open_user: " + rootSearchBase + " " + searchFilter);
            NamingEnumeration<SearchResult> enumeration = ctx.search(rootSearchBase, searchFilter, ctrl);
            if (!enumeration.hasMore())
            {
                return null;
            }

            // NOW GET THE DN FOR THIS USER
            SearchResult sr = enumeration.next(); //This is the enumeration object obtained on step II above
            Attributes res_attr = sr.getAttributes();
            Attribute adn = res_attr.get(search_attribute);
            String user_dn = adn.get().toString();
            Attribute cn_adn = res_attr.get("cn");
            String cn_user_dn = cn_adn.get().toString();
            String full_user_dn = search_attribute + "=" + user_dn + "," + rootSearchBase;
            

            // NOW GO FOR LOGIN USER WITH DN
            LdapContext user_ctx;
            Hashtable<String,String> connect_env = create_sec_env();


            connect_env.put(Context.SECURITY_PRINCIPAL, full_user_dn);
            connect_env.put(Context.SECURITY_CREDENTIALS, pwd);

            try
            {
                LogManager.msg_auth( LogManager.LVL_DEBUG, "auth_user: " + full_user_dn);
                user_ctx = new InitialLdapContext(connect_env, null);
            }
            catch (NamingException namingException)
            {
                // RETRY WITH cn ATTRIBUTE
                full_user_dn = "cn=" + cn_user_dn + "," + rootSearchBase;
                connect_env.put(Context.SECURITY_PRINCIPAL, full_user_dn);
                
                LogManager.msg_auth( LogManager.LVL_DEBUG, "auth_user: " + full_user_dn);
                user_ctx = new InitialLdapContext(connect_env, null);
            }
          
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

    void close_user( LDAPUserContext uctx )
    {
        try
        {
            if (uctx.ctx != null)
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

        LogManager.msg_auth( LogManager.LVL_DEBUG, "DN_Qry: " + rootSearchBase + " " + ldap_qry);
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
