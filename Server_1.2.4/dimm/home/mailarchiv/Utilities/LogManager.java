/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import dimm.home.mailarchiv.Main;
import home.shared.CS_Constants;
import home.shared.Utilities.LogConfigEntry;
import home.shared.Utilities.LogListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import org.apache.log4j.ConsoleAppender;
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

    public static int MAX_MSG_CACHE_SIZE = 5000;
   

    static long dbg_level = LVL_WARN;
    private final static String LOG_L4J = "logfj.log";

    public static SimpleDateFormat message_sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");


    static LogTypeEntry[] lte_array =
    {
        new LogTypeEntry(TYP_SYSTEM),
        new LogTypeEntry(TYP_AUTH),
        new LogTypeEntry(TYP_VAULT),
        new LogTypeEntry(TYP_INDEX),
        new LogTypeEntry(TYP_EXTRACT),
        new LogTypeEntry(TYP_HOTFOLDER),
        new LogTypeEntry(TYP_IMPORT),
        new LogTypeEntry(TYP_FETCHER),
        new LogTypeEntry(TYP_PROXY),
        new LogTypeEntry(TYP_MILTER),
        new LogTypeEntry(TYP_IMAPS),
        new LogTypeEntry(TYP_CMD),
        new LogTypeEntry(TYP_NOTIFICATION),
        new LogTypeEntry(TYP_SECURITY),
        new LogTypeEntry(TYP_LICENSE),
        new LogTypeEntry(TYP_COMM),
        new LogTypeEntry(TYP_ARCHIVE),
        new LogTypeEntry(TYP_BACKUP),
        new LogTypeEntry(TYP_EXCHANGE),
        new LogTypeEntry(TYP_EXCEXPTIONS)
    };

    public static String[] get_log_types()
    {
        String[] ret = new String[lte_array.length];

        for (int i = 0; i < lte_array.length; i++)
        {
            String r = lte_array[i].typ;
            ret[i] = r;
        }
        return ret;

    }


    public static String get_lvl_name( int lvl)
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
    
    public static String get_logfile( String type)
    {
        return "LOG_" + type + ".log";
    }

    public static String get_logfile( int lvl)
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
    public static void msg_exchange( int lvl, String string, Exception exc )
    {
        msg( lvl,  TYP_EXCHANGE, string, exc);
    }
    public static void msg_exchange( int lvl, String string )
    {
        msg_exchange(lvl, string, null);
    }

    public static void printStackTrace( Exception e )
    {
        _msg( e);
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

private static void _msg( Exception exc )
    {
        java.util.Date now = new java.util.Date();
        StringBuilder sb = new StringBuilder();

        sb.append( message_sdf.format( now ) );
        sb.append( ": " );


        if (exc != null)
        {
            sb.append( exc.getLocalizedMessage() );
        }


        String s = sb.toString();

        // ADD TO CACHE
        LogTypeEntry lte = get_lte( TYP_EXCEXPTIONS );

        exc.printStackTrace(System.err);

        lte.check_max_size();

        lte.file_log( s, exc );
    }

    private static void _msg( int lvl, String type, String msg, Exception exc )
    {
        java.util.Date now = new java.util.Date();
        StringBuilder sb = new StringBuilder();

        sb.append( message_sdf.format( now ) );
        sb.append( ": " );


        sb.append( get_lvl_name( lvl) );
        sb.append( ": " );
        sb.append( type );
        sb.append( ": " );
        
        if (exc != null)
        {
            msg += ": " + exc.getLocalizedMessage();
        }

        sb.append( msg );
        String s = sb.toString();

        // ADD TO CACHE
        LogTypeEntry lte = get_lte( type );
        if (lte != null)
        {
            lte.add_msg(lvl, msg, now);
        }

        System.out.println( s );
        if (exc != null && exc instanceof RuntimeException)
            printStackTrace(exc);


        if (lvl != LVL_VERBOSE)
        {            

            lte.check_max_size();

            lte.file_log( s );
        }
    }


    public static void set_all( int lvl )
    {
        for (int i = 0; i < lte_array.length; i++)
        {
            LogTypeEntry logTypeEntry = lte_array[i];
            logTypeEntry.lvl = lvl;
        }
    }

    public static ArrayList<LogConfigEntry> get_log_config_arry()
    {
        ArrayList<LogConfigEntry> arr = new ArrayList<LogConfigEntry>();

        for (int i = 0; i < lte_array.length; i++)
        {
            arr.add( new LogConfigEntry( lte_array[i].typ, lte_array[i].lvl));
        }
        return arr;

    }
    public static  void set_log_config_arry(ArrayList<LogConfigEntry> arr, boolean do_write_config)
    {

        for (int i = 0; i < arr.size(); i++)
        {
            set_lvl(arr.get(i).typ, arr.get(i).level);
        }
        if (do_write_config)
        {
            write_config();
        }
    }


        static Properties log_props = new Properties();

    public static void read_config()
    {
        log_props = new Properties();
        File prop_file = new File( Main.PREFS_PATH + "log_prefs.dat" );

        try
        {
            FileInputStream istr = new FileInputStream( prop_file );
            log_props.load( istr );
            istr.close();
        }
        catch (Exception exc)
        {
            System.out.println("Kann Log-Properties nicht lesen: " + exc.getMessage() );
        }

        for (int i = 0; i < lte_array.length; i++)
        {
            LogTypeEntry logTypeEntry = lte_array[i];
            String str = log_props.getProperty("Level" + logTypeEntry.typ, "");
            if (str.length() > 0)
            {
                logTypeEntry.lvl = Integer.parseInt(str);
            }
        }
    }

    public static void write_config()
    {
        for (int i = 0; i < lte_array.length; i++)
        {
            LogTypeEntry logTypeEntry = lte_array[i];
            log_props.setProperty("Level" + logTypeEntry.typ, Integer.toString( logTypeEntry.lvl ));
        }

        File prop_file = new File(Main.PREFS_PATH + "log_prefs.dat");
        try
        {
            FileOutputStream ostr = new FileOutputStream( prop_file );
            log_props.store( ostr, "MailSecurer Log-Properties, please do not edit" );
            ostr.close();
        }
        catch (Exception exc)
        {
            System.out.println("Kann Properties nicht schreiben: " + exc.getMessage() );
        }
    }
   

    // SINGLETON
    private LogManager()
    {
    }

    public static void set_lvl( String typ, int lvl)
    {
        LogTypeEntry logTypeEntry = get_lte( typ );
        logTypeEntry.lvl = lvl;
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

  


    public static LogManager get_instance()
    {
        return manager;
    }

    final static Logger main_logger;
    final static LogManager manager;
    public static final String MONTHLY_ROLL = "'.'yyyy-MM";
    public static final String WEEKLY_ROLL = "'.'yyyy-ww";
    static
    {
        main_logger = Logger.getLogger("dimm.MailSecurerServer");
        manager = new LogManager();

        read_config();

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
        LogTypeEntry lte = get_lte(log_type);
        if (lte != null)
        {
            return lte.get_log_file();
        }
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



    /**
     * Given a byte array this method:
     * a. creates a String out of it
     * b. reverses the string
     * c. extracts the lines
     * d. characters in extracted line will be in reverse order,
     *    so it reverses the line just before storing in Vector.
     *
     *  On extracting required numer of lines, this method returns TRUE,
     *  Else it returns FALSE.
     *
     * @param bytearray
     * @param lineCount
     * @param lastNlines
     * @return
     */
    private static boolean parseLinesFromLast( byte[] bytearray, int lineCount, Vector<String> lastNlines )
    {
        String lastNChars = new String(bytearray);
        StringBuilder sb = new StringBuilder(lastNChars);
        lastNChars = sb.reverse().toString();
        StringTokenizer tokens = new StringTokenizer(lastNChars, "\n");
        while (tokens.hasMoreTokens())
        {
            StringBuilder sbLine = new StringBuilder((String) tokens.nextToken());
            lastNlines.add(sbLine.reverse().toString());
            if (lastNlines.size() == lineCount)
            {
                return true;//indicates we got 'lineCount' lines
            }
        }
        return false; //indicates didn't read 'lineCount' lines
    }

    /**
     * Reads last N lines from the given file. File reading is done in chunks.
     *
     * Constraints:
     * 1 Minimize the number of file reads -- Avoid reading the complete file
     * to get last few lines.
     * 2 Minimize the JVM in-memory usage -- Avoid storing the complete file
     * info in in-memory.
     *
     * Approach: Read a chunk of characters from end of file. One chunk should
     * contain multiple lines. Reverse this chunk and extract the lines.
     * Repeat this until you get required number of last N lines. In this way
     * we read and store only the required part of the file.
     *
     * 1 Create a RandomAccessFile.
     * 2 Get the position of last character using (i.e length-1). Let this be curPos.
     * 3 Move the cursor to fromPos = (curPos - chunkSize). Use seek().
     * 4 If fromPos is less than or equal to ZERO then go to step-5. Else go to step-6
     * 5 Read characters from beginning of file to curPos. Go to step-9.
     * 6 Read 'chunksize' characters from fromPos.
     * 7 Extract the lines. On reading required N lines go to step-9.
     * 8 Repeat step 3 to 7 until
     *			a. N lines are read.
     *		OR
     *			b. All lines are read when num of lines in file is less than N.
     * Last line may be a incomplete, so discard it. Modify curPos appropriately.
     * 9 Exit. Got N lines or less than that.
     *
     * @param fileName
     * @param lineCount
     * @param chunkSize
     * @return
     */
    public static Vector<String> tail( String log_type, long offset, int lineCount )
    {
        Vector<String> lastNlines = new Vector<String>();

        // GET FROM CACHE?
        LogTypeEntry lte = get_lte( log_type );

        if (lte != null && (lineCount  + offset) < lte.msg_cache.size())
        {
            for (int i = 0; i < lineCount; i++)
            {
                lastNlines.add( lte.get_msg((int)offset + i));
            }
            return lastNlines;
        }

        File log_file = LogManager.get_file_by_type(log_type);

        int chunkSize = CS_Constants.STREAM_BUFFER_LEN;
        RandomAccessFile raf = null;
        try
        {
            raf = new RandomAccessFile(log_file, "r");

            int delta = 0;
            long curPos = raf.length() - 1 - offset;
            long fromPos;
            byte[] bytearray;
            while (true)
            {
                fromPos = curPos - chunkSize;
                if (fromPos <= 0)
                {
                    raf.seek(0);
                    bytearray = new byte[(int) curPos];
                    raf.readFully(bytearray);
                    parseLinesFromLast(bytearray, lineCount, lastNlines);
                    break;
                }
                else
                {
                    raf.seek(fromPos);
                    bytearray = new byte[chunkSize];
                    raf.readFully(bytearray);
                    if (parseLinesFromLast(bytearray, lineCount, lastNlines))
                    {
                        break;
                    }
                    delta = ((String) lastNlines.get(lastNlines.size() - 1)).length();
                    lastNlines.remove(lastNlines.size() - 1);
                    curPos = fromPos + delta;
                }
            }

            return lastNlines;
        }
        catch (Exception e)
        {
            printStackTrace(e);
            return null;
        }
        finally
        {
            if (raf != null)
            {
                try
                {
                    raf.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
    }



}
