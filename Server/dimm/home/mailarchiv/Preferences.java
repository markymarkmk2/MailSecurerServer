/*
 * Preferences.java
 *
 * Created on 5. Oktober 2007, 18:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;


/****
 
 Bestehedne Config:
Playlists=/var/www/localhost/htdocs/websense/login/pls
Songs=/var/www/localhost/htdocs/websense/dev/music_v3

 ****/

/**
 *
 * @author Administrator
 */
public class Preferences
{
    
    ArrayList<String> prop_names;
    
    public static final String STATION_ID ="StationID";
    public static final String DEBUG = "Debug";
    public static final String PORT = "Port";
    public static final String SERVER = "Server";
    public static final String IP = "IP";
    public static final String GW = "Gateway";
    public static final String DNS = "NameServer";
    public static final String DHCP = "DHCP";
    public static final String MASK = "MASK";
    public static final String STATIONNAME ="StationName";
    public static final String NETINTERFACE = "NetworkInterface";
    public static final String VPN_SERVER = "VPNServer";
    public static final String VPN_PORT = "VPNPort";
    public static final String PXENABLE = "ProxyEnable";
    public static final String PXSERVER = "ProxyServer";
    public static final String PXPORT = "ProxyPort";
    public static final String PXSOCKSPORT = "ProxySocksPort";
    public static final String SERVER_SW_PATH = "SoftwareUpdateDir";
    public static final String RDATE_COMMAND = "RDateCommand";
    
    public static final String ALLOW_CONTINUE_ON_ERROR = "AllowContinueOnError";
    public static final String MAIL_ARCHIVA_URL = "MailArchivaURL";
    public static final String MAIL_ARCHIVA_AGENT_OPTS = "MailArchivaAgentOpts";
              
    java.util.Properties props;



    
    /** Creates a new instance of Preferences */
    public Preferences()
    {
        prop_names = new ArrayList<String>();
        
        prop_names.add( STATION_ID );
        prop_names.add( STATIONNAME );
        prop_names.add( DEBUG );
        prop_names.add( PORT );
        prop_names.add( SERVER );
        prop_names.add( IP );
        prop_names.add( GW );
        prop_names.add( DNS );
        prop_names.add( DHCP );
        prop_names.add( MASK );
        prop_names.add( NETINTERFACE );
        prop_names.add( PXENABLE );
        prop_names.add( PXSERVER );
        prop_names.add( PXPORT );
        prop_names.add( PXSOCKSPORT );
        prop_names.add( VPN_SERVER );
        prop_names.add( VPN_PORT );
        prop_names.add( SERVER_SW_PATH );
        prop_names.add( RDATE_COMMAND );
        prop_names.add( MAIL_ARCHIVA_URL );
        
        prop_names.add( ALLOW_CONTINUE_ON_ERROR );
        prop_names.add( MAIL_ARCHIVA_AGENT_OPTS );
        
                
        read_props();
    }
    
    ArrayList<String> get_prop_list()
    {
        return prop_names;
    }
    
    String base_prop_name(String s)
    {
        int idx = s.lastIndexOf( "_" );
        if (idx >= 0)
        {
            try
            {
                int n = Integer.parseInt( s.substring( idx + 1 ) );
                return s.substring(0, idx );
            }
            catch (Exception exc ) {}
        }
        return s;
    }
    
    boolean check_prop( String s )
    {
        for (int i = 0; i < prop_names.size(); i++)
        {
            String base_prop = base_prop_name(s);
            if (prop_names.get(i).compareTo(base_prop) == 0)
                return true;            
        }
        return false;
    }
        
    public String get_prop(String p)
    {
        if (!check_prop(p))
        {
            Main.err_log_warn("Unbekannte property <" + p + ">" );
            return null;
        }
        String ret = props.getProperty( p );
        return ret;
    }
    
    public void set_prop(String p, String v)
    {
        if (!check_prop(p))
        {
            Main.err_log_warn("Unbekannte property <" + p + ">" );
        }
        props.setProperty( p, v );
    }
    
    
    public void read_props()
    {
        File prop_file = new File( Main.PREFS_PATH + "preferences.dat" );
        props = new java.util.Properties();        
        try
        {
            FileInputStream istr = new FileInputStream( prop_file );
            props.load( istr );   
            istr.close();
        }        
        catch (Exception exc)
        {
            System.out.println("Kann Properties nicht lesen: " + exc.getMessage() );
        }
        
        
//        String db_server = props.getProperty("DBServer");
        
    
    }
    public boolean store_props()
    {        
        File prop_file = new File(Main.PREFS_PATH + "preferences.dat");
        try
        {
            FileOutputStream ostr = new FileOutputStream( prop_file );
            props.store( ostr, "JMailProxy Properties, please do not edit" );        
            ostr.close();
            return true;
        }        
        catch (Exception exc)
        {
            Main.err_log("Kann Properties nicht schreiben: " + exc.getMessage() );
        }
        return false;
    }
    
    
}
