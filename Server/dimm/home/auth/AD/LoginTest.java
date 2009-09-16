/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth.AD;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

class Test2
{

    DirContext context;

    void init() throws NamingException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_PRINCIPAL, "Administrator");
        env.put(Context.SECURITY_CREDENTIALS, "helikon");
        env.put(Context.PROVIDER_URL, "ldap://my.server.address");

        env.put("java.naming.ldap.attributes.binary", "objectSid");
        env.put(Context.REFERRAL, "follow");

        context = new InitialDirContext(env);

    }

    void search() throws NamingException
    {
        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        ctrl.setReturningAttributes(new String[]
                {
                    "objectSid"
                });

        Attributes attributes = context.getAttributes(context.getNameInNamespace());
        Attribute attribute = attributes.get("defaultNamingContext");
        String rootSearchBase = attribute.get().toString();

        NamingEnumeration<SearchResult> enumeration =
                context.search(rootSearchBase, "(objectclass=user)", ctrl);

        // And to invoke the method using a SID from one of the entries fetched earlier
        SearchResult o = enumeration.next(); //This is the enumeration object obtained on step II above
        Attributes res_attr = o.getAttributes();
        String SID = getSIDAsString((byte[]) res_attr.get("objectsid").get());

    }

    public static String getSIDAsString( byte[] SID )
    {
        // Add the 'S' prefix
        StringBuilder strSID = new StringBuilder("S-");

        // bytes[0] : in the array is the version (must be 1 but might
        // change in the future)
        strSID.append(SID[0]).append('-');

        // bytes[2..7] : the Authority
        StringBuilder tmpBuff = new StringBuilder();
        for (int t = 2; t <= 7; t++)
        {
            String hexString = Integer.toHexString(SID[t] & 0xFF);
            tmpBuff.append(hexString);
        }
        strSID.append(Long.parseLong(tmpBuff.toString(), 16));

        // bytes[1] : the sub authorities count
        int count = SID[1];

        // bytes[8..end] : the sub authorities (these are Integers - notice
        // the endian)
        for (int i = 0; i < count; i++)
        {
            int currSubAuthOffset = i * 4;
            tmpBuff.setLength(0);
            tmpBuff.append(String.format("%02X%02X%02X%02X",
                    (SID[11 + currSubAuthOffset] & 0xFF),
                    (SID[10 + currSubAuthOffset] & 0xFF),
                    (SID[9 + currSubAuthOffset] & 0xFF),
                    (SID[8 + currSubAuthOffset] & 0xFF)));

            strSID.append('-').append(Long.parseLong(tmpBuff.toString(), 16));
        }

        // That's it - we have the SID
        return strSID.toString();
    }
}

class JndiAction
{
    //Der Vollqualifizierte Name des Administrators im AD
    // final static String ADMIN_NAME = "CN=Administrator,CN=Users,DC=home,DC=dimm";

    final static String ADMIN_NAME = "Administrator";
    final static String ADMIN_PASSWORD = "helikon";
    //User Standardpfad von dem die Suche im AD ausgehen soll
    static LdapContext ctx;

    public Integer run() throws Exception
    {

        init();
        List list = findUsersByAccountName("");
        for (Iterator iter = list.iterator(); iter.hasNext();)
        {
            String element = (String) iter.next();
            System.out.println(element);
        }
        ctx.close();
        return 0;
    }

    static void init() throws Exception
    {

        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
//        env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");
        env.put(Context.SECURITY_PRINCIPAL, ADMIN_NAME);
        env.put(Context.SECURITY_CREDENTIALS, ADMIN_PASSWORD);
        //Der entsprechende Domänen-Controller:LDAP-Port
        env.put(Context.PROVIDER_URL, "ldap://192.168.1.120:389");
        ctx = new InitialLdapContext(env, null);
    }

    static List findUsersByAccountName( String accountName ) throws Exception
    {
        List list = new ArrayList();


        //Unsere LDAP Abfrage...
        String searchFilter = "(&(objectCategory=person)(objectClass=user)(name=M*))";


        //System.out.println(searchFilter);

        //Wir definiren den "Suchapparat" für die LDAP Suche...
        SearchControls searchControls = new SearchControls();
        String[] resultAttributes =
        {
            "sn", "givenName", "sAMAccountName"
        };
        searchControls.setReturningAttributes(resultAttributes);
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        //Wir führen die Suche im LDAP durch
        String search_base = "CN=Users,DC=dimm,DC=home";

        NamingEnumeration results = ctx.search(search_base, searchFilter, searchControls);

        //Wir iterieren über alle Resultate und speichern die gefundenen Namen
        //in einer Liste.
        while (results.hasMoreElements())
        {
            SearchResult searchResult = (SearchResult) results.nextElement();
            list.add(searchResult.toString());
        }
        return list;
    }
}

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

public class LoginTest
{

    String admin_name;
    String admin_pwd;
    String ldap_host;
    int ldap_port;
    boolean ssl;
    LdapContext ctx;
    String error_txt;

    public LoginTest( String admin_name, String admin_pwd, String ldap_host, int ldap_port, boolean ssl )
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

    boolean is_connected()
    {
        return (ctx != null);
    }

    boolean connect_ldap()
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
/*        SearchControls ctrl = new SearchControls();
        ctrl.setSearchScope(SearchControls.OBJECT_SCOPE);
        ctrl.setReturningAttributes(new String[]
                {
                    attr_name
                });
*/

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
            LoginTest test = new LoginTest("Administrator", "helikon", "192.168.1.120", 0, /*ssl*/false);
            if (test.connect_ldap())
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
    /**
     * Wir initialisieren die Verbindung zum LDAP Service
     * Die zum Zugriff notwendigen Daten legen wir in einer
     * Hashtable ab und übergeben diese an den InitialLdapContext, der
     * mit den darin enthaltenen Informationen die Verbindung aufbaut.
     * @throws Exception
     */
    /**
     * Wir bekommen ein Namenskürzel hineingereicht und bauen uns damit einen
     * LDAP Suchfilter um nach dem vermeindlichen Benutzer zu suchen. Die gefundenen
     * Kandidaten werden in einer Liste zurückgegeben.
     * @param accountName
     * @return
     * @throws Exception
     */
}
