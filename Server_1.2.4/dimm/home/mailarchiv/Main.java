/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.Updater.Updater;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.workers.SQLWorker;
import home.shared.CS_Constants;
import home.shared.Utilities.LogConfigEntry;
import home.shared.Utilities.ZipUtilities;
import home.shared.mail.CryptAESInputStream;
import home.shared.mail.CryptAESOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.zip.ZipOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *
 * @author Administrator
 */
public final class Main
{
    
    private static final String VERSION = "1.5.4";
    
    public static final String LOG_ERR = "error.log";
    public static final String LOG_INFO = "info.log";
    public static final String LOG_WARN = "warn.log";
    public static final String LOG_SQL = "sql.log";
    public static final String LOG_L4J = "l4j.log";
    
    public static final String PREFS_PATH = "preferences/";
    public static final String TEMP_PATH = "temp/";
    public static final String LOG_PATH = "logs/";
    public static final String SCRIPT_PATH = "scripts/";
    public static final String DATABASEPATH = "db/";
//    public static final String IMPORTRELPATH = "import/";
    
    
    public static final String PROGNAME = "MailArchiv.jar";
    public static final String PROGNAME_LASTVALID = "MailArchiv.jar_last_valid";
    public static final String UPDATE_PATH = "update/";
    public static final String LICENSE_PATH = "license/";
    public static final String RFC_PATH = "rfc_temp/";
    public static final String SERVER_UPDATEWORKER_PATH = "/mailsecurer/update/";
    
    public static String STARTED_OK = "started_ok";
    public static String APPNAME = "MailSecurer";
    public static String DEFAULTSERVER = "www.mailsecurer.de";
    public static final String HTTPUSER = "mailsecurer";
    public static final String HTTPPWD = "123456";
    
    
    private static GeneralPreferences general_prefs;
    
    public static Main me;

    //private static int startup_debug_level = 1;
    public static String work_dir;
    
    public static boolean trace_mode = false;
   
    
    static LogicControl control;    
    public static String ws_ip = "127.0.0.1";
    public static int ws_port = 8050;
    public static long MIN_FREE_SPACE = (1024*1024*100); // MIN 100MB DISKSPACE
    
    
    static void print_system_property( String key )
    {
        LogManager.msg_system( LogManager.LVL_INFO, "Property " + key + ": " + System.getProperty(key) );
    }

    public static GeneralPreferences get_prefs()
    {
        return general_prefs;
    }
    public static void create_prefs()
    {
        general_prefs = new GeneralPreferences();
    }



    
    /** Creates a new instance of Main */
    @SuppressWarnings("LeakingThisInConstructor")
    public Main(String[] args)
    {
        me = this;
        work_dir = new File(".").getAbsolutePath();
        if (work_dir.endsWith("."))
            work_dir = work_dir.substring(0, work_dir.length() - 2);

        boolean init_db = false;
        
        
        print_system_property( "java.version" );
        print_system_property( "java.vendor" );
        print_system_property( "java.home");
        print_system_property( "java.class.path");
        print_system_property( "os.name");
        print_system_property( "os.arch");
        print_system_property( "os.version");
        print_system_property( "user.dir");



        try
        {
            File f = new File( LOG_PATH );
            if (!f.exists())
                f.mkdirs();

            f = new File( PREFS_PATH );
            if (!f.exists())
                f.mkdirs();
        
            f = new File( RFC_PATH );
            if (!f.exists())
                f.mkdirs();

            f = new File( LICENSE_PATH );
            if (!f.exists())
                f.mkdirs();

            f = new File( DATABASEPATH );
            if (!f.exists())
            {
                init_db = true;
                // DO NOT CREATE DIR, DERBY WANTS A NON-EXISTING DIRECTORY
                //f.mkdirs();
            }

            f = new File( TEMP_PATH );
            if (!f.exists())
                f.mkdirs();
        
            
        }
        catch ( Exception exc)
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Cannot create local dirs: " + exc.getMessage() );
        }

        // PREFS FOR ARGS, ARGS HABEN PRIO
        create_prefs();

        try
        {
            Security.addProvider(new BouncyCastleProvider() );
        }
        catch ( Exception exc)
        {
            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_SECURITY, "Cannot use 256 bit encryption falling back to 128 bit: " + exc.getMessage() );
            CryptAESInputStream.lame_security = true;
            CryptAESOutputStream.lame_security = true;
        }
        
        LogManager.msg_system( LogManager.LVL_INFO, "Starting " + APPNAME + " V" + VERSION );



              

        // DEFAULT IS DERBY
        SQLWorker.set_to_derby_db();

        
        read_args( args );
        
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("-t") == 0)
                trace_mode = true;
            if (args[i].compareTo("-vvv") == 0)
            {
                LogManager.set_all(LogManager.LVL_VERBOSE);
            }
            if (args[i].compareTo("-vv") == 0)
            {
                LogManager.set_all(LogManager.LVL_DEBUG);
            }
                       
            if (args[i].compareTo("-server_ip") == 0 && args[i + 1] != null)
            {
                ws_ip = args[i + 1];

            }
            if (args[i].compareTo("-server_port") == 0 && args[i + 1] != null)
            {
                try
                {
                    ws_port = Integer.parseInt(args[i + 1]);
                }
                catch (NumberFormatException numberFormatException)
                {
                    LogManager.msg_system( LogManager.LVL_ERR, "Invalid portnumber on commandline, using default: " + ws_port);
                }
            }
            if (args[i].compareTo("-init_db") == 0)
            {
                init_db = true;
            }
            if (args[i].compareTo("-derby") == 0)
            {
                SQLWorker.set_to_derby_db();
            }

            
            // CREATE INSTALLER --sb lnx / mac / win
            if (args[i].compareTo("--sb") == 0 && (i + 1) < args.length)
            {
                Updater.build_sb_installer( args[i + 1], "mailsecurerserver", "MSSI");
                System.exit(0);
            }
        }            
        try
        {            
            if (is_proxy_enabled()  && Main.get_long_prop(GeneralPreferences.PXSOCKSPORT) > 0)
            {
                System.setProperty("proxyPort",get_prop(GeneralPreferences.PXSOCKSPORT));
                System.setProperty("proxyHost",get_prop(GeneralPreferences.PXSERVER));
                LogManager.msg_system( LogManager.LVL_INFO, "Using Proxyserver " + get_prop(GeneralPreferences.PXSERVER) + ":" + get_prop( GeneralPreferences.PXSOCKSPORT ) );
            }
        }
        catch (Exception exc)
        {
            LogManager.printStackTrace(exc);
        }

        if (init_db)
        {
            LogManager.msg_system( LogManager.LVL_INFO, "Building new database");
            SQLWorker.build_hibernate_tables();
        }
        
        // SETTING SECURITY PROPERTIES
        init_mail_security();

        // SETTING JAVA MAIL PARAMS
        init_mail_settings();
             


        LogManager.msg_system( LogManager.LVL_INFO, "Using DB connect " + SQLWorker.get_db_connect_string());

       
        
        
    }

    void init_mail_settings()
    {
        /*System.setProperty("mail.mime.address.strict", "false");
        System.setProperty("mail.mime.decodetext.strict", "false");
        System.setProperty("mail.mime.parameters.strict", "false");*/
        System.setProperty("mail.mime.applefilenames", "true");
        //System.setProperty("mail.mime.ignoreunknownencoding", "true");
    }

        // SETTING SECURITY PROPERTIES
    void init_mail_security()
    {

        Security.setProperty( "ssl.SocketFactory.provider", "home.shared.Utilities.DefaultSSLSocketFactory");
        Security.addProvider( new com.sun.net.ssl.internal.ssl.Provider());

        // NOW WE USE JAVAS DEFAULT SSL FACTORY, THIS IS OVERRIDDEN WITH OUR
        final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
        Properties props = System.getProperties();

        // IMAP provider
        props.setProperty( "mail.imaps.socketFactory.class", SSL_FACTORY);
        
        // POP3 provider
        props.setProperty( "mail.pop3.socketFactory.class", SSL_FACTORY);

        // MAYBEE NEEDED BY KERBEROS, I DUNNO
        ///        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        // SET DEFAULT TRUSTSTORE
        String java_home = System.getProperty("java.home").trim();
        String ca_cert_file = java_home + "/lib/security/cacerts";

        ca_cert_file = Main.get_prop(GeneralPreferences.TRUSTSTORE, ca_cert_file);
        System.setProperty("javax.net.ssl.trustStore", ca_cert_file);
        props.put("javax.net.ssl.trustStore", ca_cert_file);

   
    }
    
    void work()
    {
        while (true)
        {
            try
            {        
                control = new LogicControl();
                
                control.initialize();
                
                // EVERY OBJECT HANDLES THIS FOR ITS MEMBERS
                StringBuffer sb = new StringBuffer("Requirement Checks:\n");
                if (!control.check_requirements( sb ))
                    LogManager.msg_system( LogManager.LVL_ERR, sb.toString() );
                else
                    LogManager.msg_system( LogManager.LVL_DEBUG, sb.toString() );
                
                
                control.run();

                if (control.is_shutdown())
                {                    
                    break;
                }
            }
            catch (Exception exc)
            {
                LogManager.msg_system( LogManager.LVL_ERR,  "Caught unhandled exception, restarting application:" + exc.getMessage() );
                LogManager.printStackTrace(exc );
                LogicControl.sleep(5000);
            }
        }
        LogManager.msg_system( LogManager.LVL_INFO,  Main.APPNAME + " is shut down");
        try
        {
            File f = new File("shutdown_ok.txt");
            f.createNewFile();
        }
        catch (IOException iOException)
        {
        }

        AuditLog.getInstance().stop();

        System.exit(0);
    }
    
    public static LogicControl get_control()
    {
        if (me != null)
        {
            return control;
        }
        return null;
    }

     
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {



        if (args.length == 1 && args[0].compareTo("-version") == 0)
        {
            System.out.println(Main.get_version_str());
            return;
        }


        Main m = new Main(args);

        String key = "1234567890123456789012345";

        try
        {
            CryptAESOutputStream cos = new CryptAESOutputStream(System.out, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), key);
            String s = cos.toString();
            LogManager.msg( LogManager.LVL_INFO, LogManager.TYP_SECURITY, "Testing key length " + key.length() + " OK");
        }
        catch (Exception exc)
        {
            LogManager.msg( LogManager.LVL_ERR, LogManager.TYP_SECURITY,"Testing key length " + key.length() + " NOK: " + exc.getMessage());
        }

        AuditLog.getInstance().start( args );


        
        m.work();
     
    }    
    
    public static void sleep( int ms)
    {
        try
        {
            Thread.sleep( ms );
        } catch (InterruptedException ex)
        {
            LogManager.printStackTrace(ex);
        }
    }

    public static String get_name()
    {
        return get_prop( GeneralPreferences.NAME, Main.APPNAME );
    }
    
    static public boolean is_proxy_enabled()
    {
        String px_enable = get_prop( GeneralPreferences.PXENABLE );
        if (px_enable != null && px_enable.length() > 0 && px_enable.charAt(0) == '1')
            return true;
        
        return false;
    }
    
    
    void read_args( String[] args )
    {
        // READ LOG LEVELS FROM PREFS
        String[] log_types = LogManager.get_log_types();
        for (int i = 0; i < log_types.length; i++)
        {
            String typ = log_types[i];
            int lvl = (int)Main.get_long_prop( "LOG_" + typ, (long)LogManager.LVL_WARN );

            LogManager.set_lvl(typ, lvl);
        }

/*
ClassLoader classloader = org.apache.poi.poifs.filesystem.POIFSFileSystem.class.getClassLoader();
URL res = classloader.getResource("org/apache/poi/poifs/filesystem/POIFSFileSystem.class");
String path = res.getPath();
System.out.println("Core POI came from " + path);
*/
        LogManager.set_debug_lvl( get_long_prop( GeneralPreferences.DEBUG, (long)LogManager.LVL_WARN ) );
        String x = "";
        for (int i = 0; i < args.length; i++)
        {
            x += args[i] + " ";
            if (args[i].compareTo("-d") == 0)
            {
                if ( i+1 < args.length)
                try
                {
                    LogManager.set_debug_lvl( Integer.parseInt( args[i+1] ) );
                }
                catch (Exception exc)
                {
                    LogManager.set_debug_lvl(  LogManager.LVL_WARN );
                }

            }
        }

        LogManager.msg_system( LogManager.LVL_INFO,  "Args: " +x);
    }

    
    static public String get_prop( String pref_name )
    {
        if (me != null)
        {
            return Main.general_prefs.get_prop(pref_name);
        }
        return null;
    }
    static public String get_prop( String pref_name, int channel )
    {
        if (me != null)
        {
            return Main.general_prefs.get_prop(pref_name + "_" + channel);
        }
        return null;
    }
    
    static public long get_long_prop( String pref_name, long def )
    {
        if (me != null)
        {
            String ret = Main.general_prefs.get_prop(pref_name);
            if (ret != null)
            {
                try
                {
                    return Long.parseLong( ret );
                }
                catch (Exception exc)
                {
                    LogManager.msg_system( LogManager.LVL_ERR,  "Long preference " + pref_name + " has wrong format");
                }
            }
        }
        return def;
    }
    
    static public long get_long_prop( String pref_name )
    {
        return get_long_prop( pref_name, 0);
    }
    
    static public String get_prop( String pref_name, String def )
    {
        String ret = get_prop(pref_name);
        if (ret == null)
            ret = def;
        
        return ret;
    }
    static public void set_prop( String pref_name, String v )
    {
        if (me != null)
        {
            Main.general_prefs.set_prop(pref_name, v);
        }
    }
    static public void set_prop( String pref_name, String v, int channel )
    {
        if (me != null)
        {
            Main.general_prefs.set_prop(pref_name + "_" + channel, v);
        }
    }
    static public void set_long_prop( String pref_name, long v )
    {
        if (me != null)
        {
            Main.general_prefs.set_prop(pref_name, Long.toString(v));
        }
    }
    static public boolean get_bool_prop( String pref_name, boolean def )
    {
        String bool_true = "jJyY1";
        String bool_false = "nN0";
        
        if (me != null)
        {
            String ret = Main.general_prefs.get_prop(pref_name);
            if (ret != null)
            {
                if (bool_true.indexOf(ret.charAt(0)) >= 0)
                    return true;
                if (bool_false.indexOf(ret.charAt(0)) >= 0)
                    return false;
                
                LogManager.msg_system( LogManager.LVL_ERR,  "Boolean preference " + pref_name + " has wrong format");
            }
        }
        return def;
    }
    
    static public boolean get_bool_prop( String pref_name )
    {
        return get_bool_prop( pref_name, false);
    }
    static public void set_bool_prop( String pref_name, boolean  v )
    {
        if (me != null)
        {
            Main.general_prefs.set_prop(pref_name, v ? "1" : "0");
        }
    }
    


    public static int get_station_id()
    {
        int station = (int)get_long_prop(GeneralPreferences.STATION_ID, 0);
        return station;
    }

    public static String get_version_str()
    {
        return VERSION;
    }

    static public boolean write_prefs()
    {
        return general_prefs.store_props();
    }

    public static boolean read_log(String file, long lines, StringBuffer sb)
    {
        File log = null;
        
        if (file.compareTo("messages") == 0)
        {
            log = new File( "/var/log/messages");
        }
        else
        {              
            log = new File( LOG_PATH + file );
        }
        if (!log.exists())
            return false;       
        
        try
        {
            String[] cmd = {"tail", "-" + lines, log.getAbsolutePath() };
            CmdExecutor exe = new CmdExecutor(cmd);
            exe.set_no_debug( true );
            if (exe.exec() != 0)
                return false;
            
            sb.append( exe.get_out_text() );
            return true;
        }
        catch ( Exception exc)
        {
            LogManager.printStackTrace(exc);
        }
       return false; 
    }
    public static final String LOGDUMPFILE = "LogDump.zip";

    public static File build_log_dump( boolean delete_after_fetch)
    {
        ZipOutputStream zos = null;
        try
        {
            ZipUtilities zip = new ZipUtilities();

            //create a ZipOutputStream to zip the data to
            zos = new ZipOutputStream(new BufferedOutputStream( new FileOutputStream(LOGDUMPFILE), CS_Constants.STREAM_BUFFER_LEN));
            File messages = new File("/var/log/messages");
            if (messages.exists())
            {
                zip.zipFile( messages.getParent(), messages.getAbsolutePath(), zos);
            }
            File log = LogManager.get_file_by_type(LogManager.L4J);
            if (log.exists())
            {
                zip.zipFile( log.getParentFile().getAbsolutePath(), log.getAbsolutePath(), zos);
            }

            ArrayList<LogConfigEntry> log_array = LogManager.get_log_config_arry();
            for (int i = 0; i < log_array.size(); i++)
            {
                LogConfigEntry lce = log_array.get(i);

                log = LogManager.get_file_by_type(lce.typ);
                if (log.exists())
                {
                    zip.zipFile( log.getParentFile().getAbsolutePath(), log.getAbsolutePath(), zos);
                }
            }
            log = new File("syncsrv.log");
            if (log.exists())
            {
                zip.zipFile( log.getAbsoluteFile().getParentFile().getAbsolutePath(), log.getAbsolutePath(), zos);
            }
            log = new File("agent.log");
            if (log.exists())
            {
                zip.zipFile( log.getAbsoluteFile().getParentFile().getAbsolutePath(), log.getAbsolutePath(), zos);
            }

            if (delete_after_fetch)
            {
                if (messages.exists())
                {
                    messages.delete();
                }
                if (log.exists())
                {
                    log.delete();
                }
            }
            
            return new File( LOGDUMPFILE );
        }             
        catch ( Exception exc)
        {
            LogManager.printStackTrace(exc);
        }
        finally
        {
            if (zos != null)
            {
                try
                {
                    zos.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
        return null; 
    }

    public static ArrayList<String> get_properties()
    {
        if (me != null)
        {
            return Main.general_prefs.get_prop_list();
        }
        return null;
    }
    
    public static String get_work_dir()
    {
        return work_dir;
    }
/*
    public static void info_msg( String string )
    {
        LogManager.msg_system( LogManager.LVL_INFO, string);
    }
    public static void err_log( String string )
    {
        LogManager.msg_system( LogManager.LVL_ERR, string);
    }

    public static void err_log_fatal( String string )
    {
        LogManager.msg_system( LogManager.LVL_ERR, string);
    }
    
    public static void debug_msg( String string )
    {
        LogManager.msg_system( LogManager.LVL_DEBUG, string);
    }
    public static void verbose_msg( String string )
    {
        LogManager.msg_system( LogManager.LVL_VERBOSE, string);
    }
    public static void err_log_warn( String string )
    {
        LogManager.msg_system( LogManager.LVL_WARN, string);
    }
*/
    static ArrayList<String> missing_transl_tokens = new ArrayList<String>();

    public static String Txt(String string )
    {

        try
        {
            if (bundle != null)
                return bundle.getString(string);
        }
        catch (Exception exc)
        {
        }


        if (!missing_transl_tokens.contains(string))
        {
            LogManager.msg_system(LogManager.LVL_DEBUG, "Missing translation resource: " + string);

            missing_transl_tokens.add(string);
            try
            {
                FileWriter fw = new FileWriter("MissingTransl.txt", true);
                fw.append(string + "\n");
                fw.close();
            }
            catch (IOException iOException)
            {
            }
        }

        // REMOVE UNDERSCORES FROM KEY
        string = string.replace('_', ' ');
        return string;
    }

    static ResourceBundle bundle;

    static public void init_text_interface(String lcode)
    {
        if (lcode == null || lcode.length() == 0)
            lcode = Main.get_prop( GeneralPreferences.COUNTRYCODE, "EN" );

        if (lcode.compareTo("DE") == 0)
        {
            Locale l = new Locale("de", "DE", "");
            Locale.setDefault(l);
        }
        if (lcode.compareTo("EN") == 0)
        {
            Locale l = new Locale("en", "EN", "");
            Locale.setDefault(l);
        }
        if (lcode.compareTo("DK") == 0)
        {
            Locale l = new Locale("da", "DK", "");
            Locale.setDefault(l);
        }
        bundle = null;
        try
        {
            bundle = ResourceBundle.getBundle("./mainrsrc",Locale.getDefault());
        }
        catch (Exception exc)
        {
            try
            {
                bundle = ResourceBundle.getBundle("dimm/home/mainrsrc",Locale.getDefault());
            }
            catch (Exception _exc)
            {}
        }
    }

    public static boolean is_win()
    {
        return (System.getProperty("os.name").startsWith("Win"));
    }

    public static boolean is_linux()
    {
        return (System.getProperty("os.name").startsWith("Linux"));
    }

    public static boolean is_osx()
    {
        return (System.getProperty("os.name").startsWith("Mac"));
    }

  
    public static String get_fqdn()
    {
        String fqdn = Main.get_prop(GeneralPreferences.FQDN);

        try
        {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

            while (en.hasMoreElements())
            {
                NetworkInterface ni = en.nextElement();
                if (ni.getName().startsWith("lo") || ni.getHardwareAddress() == null || ni.getHardwareAddress().length == 0)
                {
                    continue;
                }
                Enumeration<InetAddress> adr_set = ni.getInetAddresses();

                while (adr_set.hasMoreElements())
                {
                    InetAddress adr = adr_set.nextElement();
                    String chn = adr.getCanonicalHostName();
                    if (chn == null || chn.length() == 0)
                        continue;
                    String[] host_parts = chn.split("\\.");
                    if (host_parts.length > 2)
                    {
                        fqdn = chn;
                        break;
                    }
                }
                if (fqdn != null)
                    break;
            }
        }
        catch (SocketException socketException)
        {
        }
        return fqdn;

    }


}

