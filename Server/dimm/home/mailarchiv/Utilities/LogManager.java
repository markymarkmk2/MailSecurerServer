/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.logging.Level;

/**
 *
 * @author mw
 */
public class LogManager
{

    public static final String LOG_ERR = "error.log";
    public static final String LOG_INFO = "info.log";
    public static final String LOG_WARN = "warn.log";

    public static final String PREFS_PATH = "preferences/";
    public static final String LOG_PATH = "logs/";

    static long dbg_level = 5;

    public static void debug( String string )
    {
        debug_msg( 0, string );
    }

    public static void debug( String string, Exception e )
    {
        debug_msg(  string, e );
    }

    public static void debug_msg( String s, Exception e )
    {
        debug_msg( s + ": " + e.getMessage() );
    }

    public static void err_log( String s, Exception e )
    {
        err_log( s + ": " + e.getMessage() );
    }

    public static void err_log_fatal( String s, Exception e )
    {
        err_log_fatal( s + ": " + e.getMessage() );
    }


    public static long get_debug_lvl()
    {
        return dbg_level;
    }
    public static void set_debug_lvl( long l)
    {
        dbg_level = l;
    }


    // CAN BE CALLED FROM INSIDE SQL-WORKER
    static void err_log_no_lock_fatal(String string)
    {
        log( "error.log", string );
    }

    public static void err_log_fatal(String string)
    {
        if (string == null || string.length() == 0)
            return;

        log( "error.log", string );

    }

    public static void err_log_warn(String string)
    {
        if (string == null || string.length() == 0)
            return;

        log( "warn.log", string );
    }

    public static void info_msg(String string)
    {
        if (string == null || string.length() == 0)
            return;

        log( "info.log", string );
    }
    public static void debug_msg(long level,  String string)
    {
        long debug_level= get_debug_lvl();

        if (level <= debug_level)
        {
            log( "debug.log", string );
        }
    }
    public static void debug_msg( String string )
    {
        debug_msg( 0, string );
    }


    public static void err_log(String string)
    {
        if (string == null || string.length() == 0)
            return;

        log( "error.log", string );

    }
    public static void log( Level level, String msg)
    {
        log(level, msg, null);
    }

    public static void log( Level level, String msg, Throwable ex )
    {
        String text = "";
        if ( msg != null)
            text += msg;

        if (ex != null)
        {
            text += ": " + ex.getMessage();
        }
        if (level == Level.SEVERE)
        {
            if (ex != null)
                ex.printStackTrace();
            err_log( text );
        }
        else if (level == Level.WARNING)
        {
            err_log_warn( text );
        }
        else if (level == Level.INFO)
        {
            info_msg( text );
        }
        else if (dbg_level > 0)
        {
            debug_msg( text );
        }
    }


    static SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");



    static synchronized void log( String file, String s )
    {
        System.out.println( s );

        File log = new File( LOG_PATH + file );
        try
        {
            FileWriter fw = new FileWriter( log, /*append*/ true );
            java.util.Date now = new java.util.Date();

            fw.write( sdf.format( now ) );
            fw.write( ": " );
            fw.write( s );
            fw.write( "\n" );
            fw.close();
        }
        catch ( Exception exc)
        {
            exc.printStackTrace();
        }


    }
}
