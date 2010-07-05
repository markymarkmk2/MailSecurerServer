/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import dimm.home.mailarchiv.Main;
import home.shared.Utilities.LogListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;


class LogTypeEntry
{
    String typ;
    int lvl;

    public LogTypeEntry( String typ )
    {
        this.typ = typ;
        this.lvl = LogManager.LVL_ERR;
    }

}
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

   

    static long dbg_level = LVL_WARN;
    private static String LOG_L4J = "logfj.log";

    static LogTypeEntry[] lte_array =
    {
        new LogTypeEntry(TYP_AUTH),
        new LogTypeEntry(TYP_EXTRACT),
        new LogTypeEntry(TYP_PROXY),
        new LogTypeEntry(TYP_CONNECTOR),
        new LogTypeEntry(TYP_HOTFOLDER),
        new LogTypeEntry(TYP_IMPORT),
        new LogTypeEntry(TYP_FETCHER),
        new LogTypeEntry(TYP_MILTER),
        new LogTypeEntry(TYP_IMAPS),
        new LogTypeEntry(TYP_CMD),
        new LogTypeEntry(TYP_VAULT),
        new LogTypeEntry(TYP_NOTIFICATION),
        new LogTypeEntry(TYP_SECURITY),
        new LogTypeEntry(TYP_SYSTEM),
        new LogTypeEntry(TYP_LICENSE),
        new LogTypeEntry(TYP_COMM),
        new LogTypeEntry(TYP_ARCHIVE),
        new LogTypeEntry(TYP_BACKUP),
        new LogTypeEntry(TYP_INDEX)
    };

    public static final String get_lvl_name( int lvl)
    {
        switch (lvl )
        {
            case LVL_INFO:    return "Info   ";
            case LVL_VERBOSE: return "Verbose";
            case LVL_DEBUG:   return "Debug  ";
            case LVL_WARN:    return "Warning";
            case LVL_ERR:     return "Error  ";
        }
        return "Unknown";

    }

    public static final String get_logfile( int lvl)
    {
        switch (lvl )
        {
            case LVL_INFO:    return LOG_INFO;
            case LVL_VERBOSE: return null;  // ONLY STDOUT
            case LVL_DEBUG:   return LOG_DEBUG;
            case LVL_WARN:    return LOG_WARN;
            case LVL_ERR:     return LOG_ERR;
        }
        return null;

    }


    public static void msg_auth( int lvl, String string, Exception exc )
    {
        msg( lvl, TYP_AUTH, string, exc);
    }
    public static void msg_auth( int lvl, String string )
    {
        msg_auth(lvl, string, null);
    }

    public static void msg_extract( int lvl, String string, Exception exc )
    {
        msg( lvl,  TYP_EXTRACT, string, exc);
    }
    public static void msg_extract( int lvl, String string )
    {
        msg_extract(lvl, string, null);
    }
    public static void msg_fetcher( int lvl, String string, Exception exc )
    {
        msg( lvl,  TYP_FETCHER, string, exc);
    }
    public static void msg_fetcher( int lvl, String string )
    {
        msg_fetcher(lvl, string, null);
    }
    public static void msg_milter( int lvl, String string, Exception exc )
    {
        msg( lvl,  TYP_MILTER, string, exc);
    }
    public static void msg_milter( int lvl, String string )
    {
        msg_milter(lvl, string, null);
    }
    public static void msg_proxy( int lvl, String string, Exception exc )
    {
        msg( lvl,  TYP_PROXY, string, exc);
    }
    public static void msg_proxy( int lvl, String string )
    {
        msg_proxy(lvl, string, null);
    }
    public static void msg_imaps( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_IMAPS, string, exc);
    }
    public static void msg_imaps( int lvl, String string )
    {
        msg_imaps(lvl, string, null);
    }
    public static void msg_index( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_INDEX, string, exc);
    }
    public static void msg_index( int lvl, String string )
    {
        msg_index(lvl, string, null);
    }
    public static void msg_cmd( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_CMD, string, exc);
    }
    public static void msg_cmd( int lvl, String string )
    {
        msg_cmd(lvl, string, null);
    }
    public static void msg_comm( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_COMM, string, exc);
    }
    public static void msg_comm( int lvl, String string )
    {
        msg_comm(lvl, string, null);
    }
    public static void msg_vault( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_VAULT, string, exc);
    }
    public static void msg_vault( int lvl, String string )
    {
        msg_cmd(lvl, string, null);
    }
    public static void msg_system( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_SYSTEM, string, exc);
    }
    public static void msg_system( int lvl, String string )
    {
        msg_system(lvl, string, null);
    }
    public static void msg_license( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_LICENSE, string, exc);
    }
    public static void msg_license( int lvl, String string )
    {
        msg_license(lvl, string, null);
    }
    public static void msg_archive( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_ARCHIVE, string, exc);
    }
    public static void msg_archive( int lvl, String string )
    {
        msg_archive(lvl, string, null);
    }
    public static void msg_backup( int lvl, String string, Exception exc )
    {
            msg( lvl,  TYP_BACKUP, string, exc);
    }
    public static void msg_backup( int lvl, String string )
    {
        msg_backup(lvl, string, null);
    }
    static LogTypeEntry get_lte( String s )
    {
        for (int i = 0; i < lte_array.length; i++)
        {
            LogTypeEntry logTypeEntry = lte_array[i];
            if (logTypeEntry.typ.compareTo(s) == 0)
                return logTypeEntry;
        }

        return null;
    }
    public static int get_lvl( String type )
    {
        LogTypeEntry logTypeEntry = get_lte( type );
        if (logTypeEntry != null)
            return logTypeEntry.lvl;

        return LVL_ERR;
    }
    public static boolean has_lvl( String type, int lvl )
    {
        return (lvl >= get_lvl(type));
    }

    public static int get_auth_lvl()
    {
        return get_lvl(TYP_AUTH);
    }

    public static boolean has_auth_lvl( int lvl )
    {
        return (lvl >= get_lvl(TYP_AUTH));
    }

    
    public static void msg( int lvl, String type, String msg )
    {
        msg(lvl, type, msg, null);
    }
    public static void msg( int lvl, String type, String msg, Exception exc )
    {
        if (lvl == LVL_INFO)
        {
            _msg( lvl,  type, msg, exc);
        }
        else
        {
            if (lvl >= get_lvl(type))
            {
                _msg(lvl, type, msg, exc);
            }
        }
    }
    private static void _msg( int lvl, String type, String msg, Exception exc )
    {
        String s = get_lvl_name( lvl) + ": " + type + ": " + msg;
        if (exc != null)
        {
            s += exc.getLocalizedMessage();
        }
        String file = get_logfile(lvl);
        file_log( file, s );
    }

    private LogManager()
    {
    }

   

   
    public static void set_debug_lvl( long l)
    {
        dbg_level = l;
        
        if (l  == LVL_VERBOSE)
            main_logger.setLevel(org.apache.log4j.Level.ALL);
        else if (l == LVL_DEBUG)
            main_logger.setLevel(org.apache.log4j.Level.DEBUG);
        else if (l == LVL_WARN)
            main_logger.setLevel(org.apache.log4j.Level.WARN);
        else
            main_logger.setLevel(org.apache.log4j.Level.ERROR);
    }



   
    static SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");



    static synchronized void file_log( String file, String s )
    {
        System.out.println( s );

        if (file != null)
        {
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
    }

    public static LogManager get_instance()
    {
        return manager;
    }

    static Logger main_logger;
    static LogManager manager;
    public static final String MONTHLY_ROLL = "'.'yyyy-MM";
    public static final String WEEKLY_ROLL = "'.'yyyy-ww";
    static
    {
        main_logger = Logger.getLogger("dimm.MailSecurerServer");
        manager = new LogManager();
        

        try
        {
            PatternLayout layout = new PatternLayout("%-5p: %d{dd.MM.yyyy HH:mm:ss,SSS}: %m%n");
            CompressingDailyRollingFileAppender fileAppender = new CompressingDailyRollingFileAppender(layout, LOG_PATH + LOG_L4J, WEEKLY_ROLL);
            fileAppender.setMaxNumberOfDays("365");
            fileAppender.setKeepClosed(true);


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
        if (log_type.compareTo(SYNC) == 0)
            return new File( Main.work_dir + "/syncsrv.log");

        return null;
    }

    @Override
    public void log_msg( int lvl, String typ, String txt )
    {
        msg( lvl, typ, txt);
    }

    @Override
    public void log_msg( int lvl, String typ, String txt, Exception ex )
    {
        msg( lvl, typ, txt, ex);
    }

    @Override
    public boolean log_has_lvl( String typ, int lvl )
    {
        return has_lvl(typ, lvl);
    }

   

}
