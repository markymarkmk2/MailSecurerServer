/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.auth;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
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

public class TestLDAP
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

        Hashtable<String,String> env = new Hashtable<String,String>();
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

    static ArrayList<String> findUsersByAccountName( String accountName ) throws Exception
    {
        ArrayList<String> list = new ArrayList<String>();


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



