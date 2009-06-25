/*
 * Preferences.java
 *
 * Created on 5. Oktober 2007, 18:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Utilities.Preferences;
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
public class GeneralPreferences extends Preferences
{
    
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
    public static final String TEMPFILEDIR ="TempFileDir";
    
    public static final String ALLOW_CONTINUE_ON_ERROR = "AllowContinueOnError";
    public static final String MAIL_ARCHIVA_URL = "MailArchivaURL";
    public static final String MAIL_ARCHIVA_AGENT_OPTS = "MailArchivaAgentOpts";

    
    /** Creates a new instance of Preferences */
    public GeneralPreferences()
    {
        this( Main.PREFS_PATH );
    }

    public GeneralPreferences(String _path)
    {
        super(_path);
        
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
        prop_names.add( TEMPFILEDIR );
        
        prop_names.add( ALLOW_CONTINUE_ON_ERROR );
        prop_names.add( MAIL_ARCHIVA_AGENT_OPTS );
        
                
        read_props();
    }




    public String get_KeyAlgorithm()
    {
        return "PBEWithMD5AndDES";
    }

    // 8-byte Salt
    static byte[] salt =
    {
        (byte) 0x19, (byte) 0x09, (byte) 0x58, (byte) 0x0f,
        (byte) 'h', (byte) 'e', (byte) 'l', (byte) 'i'
    };

    // THIS IS FIXED, IF USER LOOSES THIS, DATA IS LOST FOR EVER
    public byte[] get_KeyPBESalt()
    {
        return salt;
    }
    public int get_KeyPBEIteration()
    {
        return 13;
    }
    
    // USED FOR ENCRYPTION END DECRYPTION OF INTERNAL SECRETS
    public String get_InternalPassPhrase()
    {
        return "hrXblks4G_oip9!zf";
    }

    
}