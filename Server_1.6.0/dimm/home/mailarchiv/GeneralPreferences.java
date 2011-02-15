/*
 * Preferences.java
 *
 * Created on 5. Oktober 2007, 18:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.Preferences;


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
    
    public static final String NAME ="Name";
    public static final String STATION_ID ="StationID";
    public static final String DEBUG = "Debug";
    public static final String PORT = "Port";
    public static final String SERVER = "Server";
    public static final String IP = "IP";
    public static final String GW = "Gateway";
    public static final String DNS = "NameServer";
    public static final String DHCP = "DHCP";
    public static final String MASK = "MASK";
    public static final String NETINTERFACE = "NetworkInterface";
    public static final String VPN_SERVER = "VPNServer";
    public static final String VPN_PORT = "VPNPort";
    public static final String PXENABLE = "ProxyEnable";
    public static final String PXSERVER = "ProxyServer";
    public static final String PXPORT = "ProxyPort";
    public static final String PXSOCKSPORT = "ProxySocksPort";
    public static final String RDATE_COMMAND = "RDateCommand";
    public static final String TEMPFILEDIR ="TempFileDir";
    public static final String SQL_CONN_TIMEOUT = "SQLConnTimeout";
    
    public static final String ALLOW_CONTINUE_ON_ERROR = "AllowContinueOnError";
    public static final String DB_USER  = "DBUser";
    public static final String DB_PWD = "DBPWD";
    public static final String SQL_CYCLETIME = "SQLCycleTime";
    public static final String WRITE_MAIL_IN_BG = "WriteMailBackground";
    public static final String INDEX_MAIL_IN_BG = "IndexMailBackground";
    public static final String INDEX_TIMEOUT = "IndexTimeoutSecs";
    public static final String SERVER_SSL = "ServerSSL";
    public static final String COUNTRYCODE = "CountryCode";
    public static final String OPTIMIZE_FLUSH_CYCLETIME = "OptimizeCycleSecs";
    public static final String AUTO_UPDATE = "AutoUpdate";
    public static final String UPDATESERVER = "UpdateServer";
    public static final String HTTPUSER = "HttpUser";
    public static final String HTTPPWD = "HttpPwd";
    public static final String MAX_STAY_VALID_DAYS = "MaxUserValidDays";
    public static final String SYSADMIN_NAME = "SysAdminName";
    public static final String SYNCSRV_PORT = "SyncServerPort";
    public static final String INDEX_MAIL_THREADS = "IndexMailThreads";
    public static final String FQDN = "FQDN";
    public static final String TRUSTSTORE = "TrustStore";
    public static final String AUDIT_DB_CONNECT = "AuditDBConnect";
    public static final String ONLY_FROM_ADRESS_LIC = "OnlyFromAddrLic";
    public static final String ALLOW_INKNOWN_DOMAIN_MAIL = "AllowInknownDomainMail";
    public static final String ALLOW_UNKNOWN_DOMAIN_MAIL = "AllowUnknownDomainMail";
    public static final String EXCHANGE_IMPORT_MAX_CHUNK_SIZE = "ExchangeImportMaxChunkSize";
    public static final String AUTO_SET_IP = "AutoSetIP";
    public static final String EXCHANGE_IMPORT_MAX_MAILCOUNT_TRESHOLD = "ExchangeImportMaxMailCountTreshold";
    public static final String TOUCH_MESSAGEID_ON_RESTORE = "TouchMessageIDOnRestore";
    public static final String HTTPD_PORT = "HttpdPort";
    public static final String RESTORE_ENVELOPE_FROM = "RestoreEnvelopeFrom";

    
    /** Creates a new instance of Preferences */
    public GeneralPreferences()
    {
        this( Main.PREFS_PATH );
    }

    public GeneralPreferences(String _path)
    {
        super(_path);
        
        prop_names.add( NAME );
        prop_names.add( STATION_ID );
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
        prop_names.add( RDATE_COMMAND );
        prop_names.add( TEMPFILEDIR );
        prop_names.add( SQL_CONN_TIMEOUT );
        prop_names.add( DB_USER );
        prop_names.add( DB_PWD );
        prop_names.add( SQL_CYCLETIME );        
        prop_names.add( ALLOW_CONTINUE_ON_ERROR );
        prop_names.add( WRITE_MAIL_IN_BG );
        prop_names.add( INDEX_MAIL_IN_BG );
        prop_names.add( SERVER_SSL );
        prop_names.add( COUNTRYCODE );
        prop_names.add( OPTIMIZE_FLUSH_CYCLETIME );
        prop_names.add( AUTO_UPDATE );
        prop_names.add( UPDATESERVER );
        prop_names.add( HTTPUSER );
        prop_names.add( HTTPPWD );
        prop_names.add( MAX_STAY_VALID_DAYS );
        prop_names.add( SYSADMIN_NAME );
        prop_names.add( SYNCSRV_PORT );
        prop_names.add( INDEX_MAIL_THREADS );
        prop_names.add( FQDN );
        prop_names.add( TRUSTSTORE );
        prop_names.add( INDEX_TIMEOUT );
        prop_names.add( AUDIT_DB_CONNECT );
        prop_names.add( ONLY_FROM_ADRESS_LIC );
        prop_names.add( ALLOW_INKNOWN_DOMAIN_MAIL );
        prop_names.add( ALLOW_UNKNOWN_DOMAIN_MAIL );
        prop_names.add( EXCHANGE_IMPORT_MAX_CHUNK_SIZE );
        prop_names.add( AUTO_SET_IP );
        prop_names.add( EXCHANGE_IMPORT_MAX_MAILCOUNT_TRESHOLD );
        prop_names.add( TOUCH_MESSAGEID_ON_RESTORE );
        prop_names.add( HTTPD_PORT );
        prop_names.add( RESTORE_ENVELOPE_FROM );



        String[] log_types = LogManager.get_log_types();
        for (int i = 0; i < log_types.length; i++)
        {
            String string = log_types[i];
            prop_names.add( "LOG_" + string );
        }
        
                
        read_props();
    }





    
}
