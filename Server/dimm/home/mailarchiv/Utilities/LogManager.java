/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import home.shared.Utilities.LogListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;

/**
 *
 * @author mw
 */
public class LogManager implements  LogListener
{

    public static final String LOG_ERR = "error.log";
    public static final String LOG_DEBUG = "debug.log";
    public static final String LOG_INFO = "info.log";
    public static final String LOG_WARN = "warn.log";

    public static final String PREFS_PATH = "preferences/";
    public static final String LOG_PATH = "logs/";

    static long dbg_level = 1;
    private static String LOG_L4J = "logfj.log";

    private LogManager()
    {
    }

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

    @Override
    public void error_log( String s, Exception e )
    {
        e.printStackTrace();
        error_log( s + ": " + e.getMessage() );
    }
    public static void err_log( String s, Exception e )
    {
        e.printStackTrace();
        err_log( s + ": " + e.getMessage() );
    }

    public static void err_log_fatal( String s, Exception e )
    {
        e.printStackTrace();
        err_log_fatal( s + ": " + e.getMessage() );
    }


    public static long get_debug_lvl()
    {
        return dbg_level;
    }
    public static void set_debug_lvl( long l)
    {
        dbg_level = l;
        
        if (l >8)
            main_logger.setLevel(org.apache.log4j.Level.ALL);
        else if (l >6)
            main_logger.setLevel(org.apache.log4j.Level.TRACE);
        else if (l >4)
            main_logger.setLevel(org.apache.log4j.Level.DEBUG);
        else if (l >1)
            main_logger.setLevel(org.apache.log4j.Level.INFO);
        else
            main_logger.setLevel(org.apache.log4j.Level.WARN);
    }


    // CAN BE CALLED FROM INSIDE SQL-WORKER
    static void err_log_no_lock_fatal(String string)
    {
        main_logger.fatal(string);
        //log( "error.log", string );
    }

    public static void err_log_fatal(String string)
    {
        if (string == null || string.length() == 0)
            return;

        main_logger.fatal(string);
//        log( "error.log", string );

    }

    public static void err_log_warn(String string)
    {
        if (string == null || string.length() == 0)
            return;

        main_logger.warn(string);
//      log( "warn.log", string );
    }

    public static void info_msg(String string)
    {
        if (string == null || string.length() == 0)
            return;

        main_logger.info(string);
        //log( "info.log", string );
    }
    public static void debug_msg(long level,  String string)
    {
        long debug_level= get_debug_lvl();

        if (level <= debug_level)
        {
            main_logger.debug(string);
//            file_log( "debug.log", string );
        }
    }
    public static void debug_msg( String string )
    {
          main_logger.debug(string);
          //debug_msg( 0, string );
    }

    public static void err_log(String string)
    {
        if (string == null || string.length() == 0)
            return;

          main_logger.error(string);

    }

    @Override
    public void error_log(String string)
    {
        if (string == null || string.length() == 0)
            return;

          main_logger.error(string);

    }
    public static void log( Level level, String msg)
    {
         main_logger.log(org.apache.log4j.Level.toLevel(level.intValue()), msg);
        //log(level, msg, null);
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



    static synchronized void file_log( String file, String s )
    {
        System.out.println( s );

        File log = new File( LOG_PATH + file );
        try
        {
            FileWriter fw = new FileWriter( log,  true );
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

    public static LogManager get_instance()
    {
        return manager;
    }

    static Logger main_logger;
    static LogManager manager;
    static
    {
        main_logger = Logger.getLogger("dimm.MailSecurerServer");
        manager = new LogManager();
        

        try
        {
            PatternLayout layout = new PatternLayout("%-5p: %d{dd.MM.yyyy HH:mm:ss,SSS}: %m%n");
            FileAppender fileAppender = new FileAppender(layout, LOG_PATH + LOG_L4J, true);

            main_logger.addAppender(fileAppender);
            //Logger.getRootLogger().addAppender(fileAppender);

            
            ConsoleAppender con = new ConsoleAppender( new SimpleLayout(), ConsoleAppender.SYSTEM_OUT );
            main_logger.addAppender(con);
          //  Logger.getRootLogger().addAppender(con);
        }
        catch (IOException iOException)
        {
            System.out.println("Logger init failed! " + iOException.getMessage());
        }

    }

    public static final String L4J = "L4J";
    public static final String ERR = "ERR";
    public static final String INFO = "INFO";
    public static final String WRN = "WRN";
    public static final String DBG = "DBG";
    public static final String SYS = "SYS";
    public static File get_file_by_type( String log_type )
    {
        if (log_type.compareTo(L4J) == 0)
            return new File(LOG_PATH + LOG_L4J);
        if (log_type.compareTo(ERR) == 0)
            return new File(LOG_PATH + LOG_ERR);
        if (log_type.compareTo(INFO) == 0)
            return new File(LOG_PATH + LOG_INFO);
        if (log_type.compareTo(WRN) == 0)
            return new File(LOG_PATH + LOG_WARN);
        if (log_type.compareTo(DBG) == 0)
            return new File(LOG_PATH + LOG_DEBUG);
        if (log_type.compareTo(SYS) == 0)
            return new File(LOG_PATH + LOG_DEBUG);

        return null;
    }

    @Override
    public void warn_log( String txt )
    {
        err_log_warn(txt);
    }

    @Override
    public void info_log( String txt )
    {
        info_msg(txt);
    }

    @Override
    public void debug_log( String txt )
    {
        debug(txt);
    }

    @Override
    public boolean is_debug()
    {
        return (get_debug_lvl() > 0);
    }


}
