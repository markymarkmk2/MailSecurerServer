package dimm.home.mailproxyclient;

/*
 * Main.java
 *
 * Created on 8. Oktober 2007, 10:30
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */



import javax.swing.UIManager;


/**
 *
 * @author Administrator
 */
public class Main
{

    static Main me;
    
    public static boolean scan_local = false;
    public static boolean enable_admin = false;
    
    public static final String PREFS_PATH = "";
    
    public  static final String version_str = "1.0.0";
    
    public static final String SERVERAPP = "BettyMailArchiver";
    public static final String CLIENTAPP = "BettyMailArchiverRemote";
    
    
    
    Preferences prefs;
    
    /** Creates a new instance of Main */
    public Main()
    {
            
        me = this;
        
        prefs = new Preferences();
    }
    
    static void print_system_property( String key )
    {
        System.out.println("Property " + key + ": " + System.getProperty(key) );
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        String fixed_ip = null;
        
        System.out.println( "Look and Feels:" );
        UIManager.LookAndFeelInfo[] lfi = UIManager.getInstalledLookAndFeels();
        for (int i = 0; i < lfi.length; i++)
        {
            System.out.println( lfi[i].toString() );
        }
        System.out.println();
        
        int lf_idx = -1;
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("-LF") == 0)
            {
                try
                {
                    lf_idx = Integer.parseInt(args[i + 1]);                    
                } 
                catch (Exception exception)
                {
                }
                break;
            }
        }
        if (lf_idx >= 0)
        {
            try
            {
                UIManager.setLookAndFeel(lfi[lf_idx].getClassName());
                System.out.println("Using L&F " + lfi[lf_idx].toString() );
                
            } 
            catch (Exception ex)
            {
                System.err.println(ex.getMessage());
            }
        }
        else
        {
            try
            {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel");
            }
            catch (Exception exc)
            {
                try
                {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                }
                catch (Exception eexc)
                {
                    try
                    {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    }
                    catch ( Exception ex )
                    {
                    }
                }
            }
        }        
        
        Main mm = new Main();
        

       
        long start = System.currentTimeMillis() / 1000;
        
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("-l") == 0)
                scan_local = true;       
            if (args[i].compareTo("-A") == 0)
                enable_admin = true;       
            if (args[i].compareTo("-i") == 0)
            {
                fixed_ip = args[i+1];
            }     
            
        }
        MainFrame frm = new MainFrame(true, true, fixed_ip);

        print_system_property( "java.version" );
        print_system_property( "java.vendor" );
        print_system_property( "java.home");
        print_system_property( "java.class.path");
        print_system_property( "os.name");
        print_system_property( "os.arch");
        print_system_property( "os.version");
        print_system_property( "user.dir");


        frm.setTitle( Main.CLIENTAPP + " V" + version_str);
        frm.setLocation(300, 200 );


        frm.setVisible( true );

        frm.fast_scan();
        
        
             
    }

    public static void err_log_warn(String string)
    {
        System.out.println( string );
    }
    public static void err_log(String string)
    {
        System.out.println( string );
    }
    
    
    
    static public String get_prop( String pref_name )
    {
        if (me != null)
        {
            return me.prefs.get_prop(pref_name);
        }
        return null;
    }
    
    static public long get_long_prop( String pref_name, long def )
    {
        if (me != null)
        {
            String ret = me.prefs.get_prop(pref_name);
            if (ret != null)
            {
                try
                {
                    return Long.parseLong( ret );
                }
                catch (Exception exc)
                {
                    Main.err_log( "Long preference " + pref_name + " has wrong format: " + exc.getMessage());
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
            me.prefs.set_prop(pref_name, v);
        }
    }
    static public void set_long_prop( String pref_name, long v )
    {
        if (me != null)
        {
            me.prefs.set_prop(pref_name, Long.toString(v));
        }
    }
    static public String get_prop( String pref_name, int channel )
    {
        String ret = get_prop(pref_name + "_" + channel);
        if (ret != null)
            return ret;
        
        return get_prop(pref_name);
    }
    static public void set_prop( String pref_name, String v, int channel )
    {
        set_prop(pref_name + "_" + channel, v);
    }
    static public long get_long_prop( String pref_name, int channel)
    {
        return get_long_prop( pref_name, channel, 0 );
    }
        
    static public long get_long_prop( String pref_name, int channel, long def  )
    {
        String v = get_prop( pref_name, channel );
        if (v != null)
        {
            try
            {
                return Long.parseLong( v );
            }
            catch (Exception exc)
            {
                Main.err_log( "Long preference " + pref_name + " has wrong format: " + exc.getMessage());
            }
        }
        return def;
    }
    static public void set_long_prop( String pref_name, long v, int channel )
    {
        set_prop(pref_name + "_" + channel, Long.toString(v) );
    }

    static public Preferences get_prefs()
    {
        if (me != null)
        {
            return me.prefs;
        }
        return null;
    }
    
    static public boolean is_proxy_enabled()
    {
        String px_enable = get_prop( Preferences.PXENABLE );
        if (px_enable != null && px_enable.length() > 0 && px_enable.charAt(0) == '1')
            return true;
        
        return false;
    }
    
    
    
}
