/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.Httpd.Httpd;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.File;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */
public class Main 
{
    
    public static final String VERSION = "1.0.2";
    
    public static final String LOG_ERR = "error.log";
    public static final String LOG_INFO = "info.log";
    public static final String LOG_WARN = "warn.log";
    
    public static final String PREFS_PATH = "preferences/";
    public static final String LOG_PATH = "logs/";
    public static final String SCRIPT_PATH = "scripts/";
    
    
    public static final String PROGNAME = "MailProxy.jar";
    public static final String PROGNAME_LASTVALID = "MailProxy.jar_last_valid";
    public static final String UPDATE_PATH = "update/";
    public static final String RFC_PATH = "rfc_temp/";
    
    public static String STARTED_OK = "started_ok";
    public static String APPNAME = "BettyMailProxy";
    public static String DEFAULTSERVER = "www.gruppemedia.de";
    
    
    public static GeneralPreferences prefs;
    
    public static Main me;

    private static int startup_debug_level = 1;
    public static String work_dir;
    
    public static boolean trace_mode = false;
    
    static LogicControl control;
    
    public static boolean create_licensefile = false;
    public static String license_interface = "eth0";

    Httpd httpd;
    
    
    static void print_system_property( String key )
    {
        LogManager.info_msg("Property " + key + ": " + System.getProperty(key) );
    }

    
    /** Creates a new instance of Main */
    public Main(String[] args)
    {
        me = this;
        work_dir = new File(".").getAbsolutePath();
        
        
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
        
            
        }
        catch ( Exception exc)
        {
            Main.err_log_fatal("Cannot create local dirs: " + exc.getMessage() );
        } 
        
        info_msg("Starting " + APPNAME + " V" + VERSION );

        // PREFS FOR ARGS, ARGS HABEN PRIO
        prefs = new GeneralPreferences();
        
        read_args( args );
        
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("-t") == 0)
                trace_mode = true;
            
            if (args[i].compareTo("-L") == 0)
                create_licensefile = true;            
            
            if (args[i].compareTo("-e") == 0 && args[i + 1] != null)
            {
                license_interface = args[i + 1];            
                info_msg("Using interface license_interface for licensing");                
            }
        }            
        try
        {            
            String pxs_port = get_prop(GeneralPreferences.PXSOCKSPORT);
            
            if (is_proxy_enabled()  && Main.get_long_prop(GeneralPreferences.PXSOCKSPORT) > 0)
            {
                System.setProperty("proxyPort",get_prop(GeneralPreferences.PXSOCKSPORT));
                System.setProperty("proxyHost",get_prop(GeneralPreferences.PXSERVER));
                info_msg("Using Proxyserver " + get_prop(GeneralPreferences.PXSERVER) + ":" + get_prop( GeneralPreferences.PXSOCKSPORT ) );
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }

        httpd = new Httpd(8100);

        httpd.start();
        
        
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
                    Main.err_log_fatal(sb.toString() );
                else
                    Main.info_msg(sb.toString() );                
                
                
                control.run();
            }
            catch (Exception exc)
            {
                err_log_fatal( "Caught unhandled exception, restarting application:" + exc.getMessage() );
                exc.printStackTrace( );               
            }
        }        
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
        Main m = new Main(args);
        
        m.work();
     
    }    
    
    public static void sleep( int ms)
    {
        try
        {
            Thread.sleep( ms );
        } catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }
    
    
    static public boolean is_proxy_enabled()
    {
        String px_enable = get_prop( GeneralPreferences.PXENABLE );
        if (px_enable != null && px_enable.length() > 0 && px_enable.charAt(0) == '1')
            return true;
        
        return false;
    }
    static public long get_debug_lvl()
    {
        return get_long_prop( GeneralPreferences.DEBUG, startup_debug_level );
    }
    
    void read_args( String[] args )
    {
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("-d") == 0)
            {
                if ( i+1 < args.length)
                try
                {
                    startup_debug_level = Integer.parseInt( args[i+1] );
                }
                catch (Exception exc)
                {
                    startup_debug_level = 1;
                }
            }
        }
    }

    
    static public String get_prop( String pref_name )
    {
        if (me != null)
        {
            return Main.prefs.get_prop(pref_name);
        }
        return null;
    }
    static public String get_prop( String pref_name, int channel )
    {
        if (me != null)
        {
            return Main.prefs.get_prop(pref_name + "_" + channel);
        }
        return null;
    }
    
    static public long get_long_prop( String pref_name, long def )
    {
        if (me != null)
        {
            String ret = Main.prefs.get_prop(pref_name);
            if (ret != null)
            {
                try
                {
                    return Long.parseLong( ret );
                }
                catch (Exception exc)
                {
                    Main.err_log( "Long preference " + pref_name + " has wrong format");
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
            Main.prefs.set_prop(pref_name, v);
        }
    }
    static public void set_prop( String pref_name, String v, int channel )
    {
        if (me != null)
        {
            Main.prefs.set_prop(pref_name + "_" + channel, v);
        }
    }
    static public void set_long_prop( String pref_name, long v )
    {
        if (me != null)
        {
            Main.prefs.set_prop(pref_name, Long.toString(v));
        }
    }
    static public boolean get_bool_prop( String pref_name, boolean def )
    {
        String bool_true = "jJyY1";
        String bool_false = "nN0";
        
        if (me != null)
        {
            String ret = Main.prefs.get_prop(pref_name);
            if (ret != null)
            {
                if (bool_true.indexOf(ret.charAt(0)) >= 0)
                    return true;
                if (bool_false.indexOf(ret.charAt(0)) >= 0)
                    return false;
                
                Main.err_log( "Boolean preference " + pref_name + " has wrong format");
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
            Main.prefs.set_prop(pref_name, v ? "1" : "0");
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
        return prefs.store_props();
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
            exc.printStackTrace();
        }
       return false; 
    }
    public static File build_log_dump( boolean delete_after_fetch)
    {        
        try
        {
            String[] cp_cmd = {"cp", "/var/log/messages", LOG_PATH };
            CmdExecutor exe = new CmdExecutor(cp_cmd);
            exe.exec();
                
            String[] cmd = {"tar", "-czvf", "full_log.tgz", LOG_PATH };
            exe = new CmdExecutor(cmd);
            if (exe.exec() != 0)
                return null;
            
            if (delete_after_fetch)
            {
                String[] rmcmd = {"rm", "-f ", LOG_PATH + "*.log" };
                exe = new CmdExecutor(rmcmd);
                exe.exec();
            }
            
            return new File( "full_log.tgz" );
        }             
        catch ( Exception exc)
        {
            exc.printStackTrace();
        }
        return null; 
    }

    public static ArrayList<String> get_properties()
    {
        if (me != null)
        {
            return Main.prefs.get_prop_list();
        }
        return null;
    }
    
    public static String get_work_dir()
    {
        return work_dir;
    }

    public static void info_msg( String string )
    {
        LogManager.info_msg(string);
    }
    public static void err_log( String string )
    {
        LogManager.err_log(string);
    }

    public static void err_log_fatal( String string )
    {
        LogManager.err_log_fatal(string);
    }
    
    public static void debug_msg( int i, String string )
    {
        LogManager.debug_msg(i, string);
    }
    public static void err_log_warn( String string )
    {
         LogManager.err_log_warn(string);
    }
    public static String Txt(String key )
    {
        return key;
    }


}
