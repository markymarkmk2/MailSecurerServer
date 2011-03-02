/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import home.shared.Utilities.ZipUtilities;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

class MsgCacheEntry
{
    String msg;
    int lvl;
    Date d;

    public MsgCacheEntry( int _lvl, String msg, Date d )
    {
        this.lvl = _lvl;
        this.msg = msg;
        this.d = d;
    }

}
/**
 *
 * @author mw
 */
public class LogTypeEntry
{
    String typ;
    private int lvl;
    ArrayList<MsgCacheEntry> msg_cache;
    long last_size_check;
    SimpleDateFormat log_sdf;

    private static final int SIZE_CHECK_CYCLE_MS = 30000;
    private static final long MAX_LOG_LENGTH = 100*1024*1024l; // 100 MiB


    public LogTypeEntry( String typ )
    {
        this.typ = typ;
        this.lvl = LogManager.LVL_ERR;
        msg_cache = new ArrayList<MsgCacheEntry>();
        log_sdf = new SimpleDateFormat("yyyy.MM.dd_hh.mm.ss");
    }

    int get_lvl()
    {
        return lvl;
    }

    void add_msg( int lvl, String s, Date date )
    {
        if (msg_cache.size() > LogManager.MAX_MSG_CACHE_SIZE)
        {
            msg_cache.remove(0);
        }
        msg_cache.add( new MsgCacheEntry(lvl, s, date ) );
    }
    public String get_msg( int idx )
    {
        if (idx >= msg_cache.size())
        {
            return "Invalid Index";
        }

        MsgCacheEntry mce = msg_cache.get(idx);

        StringBuffer sb = new StringBuffer();

        sb.append( LogManager.message_sdf.format( mce.d ) );
        sb.append( ": " );

        sb.append( LogManager.get_lvl_name( mce.lvl) );
        sb.append( ": " );
        sb.append( typ );
        sb.append( ": " );

        sb.append( mce.msg );

        return sb.toString();

    }

    File get_log_file()
    {
        return new File(LogManager.LOG_PATH + "LOG_" + typ +".log");
    }
    File get_zip_file()
    {
        String date = log_sdf.format( new Date() );

        return new File(LogManager.LOG_PATH + "LOG_" + typ + "_" + date + ".zip");
    }
    File get_tmp_file()
    {
        String date = log_sdf.format( new Date() );

        return new File(LogManager.LOG_PATH + "LOG_" + typ + "_" + date + ".log");
    }
    void check_max_size()
    {
        boolean test = false;

        long now = System.currentTimeMillis();
        if ((now - last_size_check) < SIZE_CHECK_CYCLE_MS)
            return;

        last_size_check = now;

        File log = get_log_file();

        if (log.length() > MAX_LOG_LENGTH || test)
        {
            final File dump = get_tmp_file();
            final File zip_dump = get_zip_file();


            if (log.renameTo(dump))
            {
                BackgroundWorker sw = new BackgroundWorker("LogZipper")
                {

                    @Override
                    public Object construct()
                    {
                        try
                        {
                           ZipUtilities zip = new ZipUtilities();
                            if (zip.zip(dump.getAbsolutePath(), zip_dump.getAbsolutePath()))
                            {
                                dump.delete();
                            }
                        }
                        catch (Exception e)
                        {
                            System.err.println("Error occured while zipping logfile " + dump.getAbsolutePath());
                        }
                        return null;
                    }
                };
                sw.start();
            }
        }
    }

    synchronized void file_log( String s )
    {

        File log = get_log_file();

        try
        {
            FileWriter fw = new FileWriter( log,  true );
            fw.write( s );
            fw.write( "\n" );
            fw.close();
        }
        catch ( Exception exc)
        {
            System.err.println(s);
        }
    }
    synchronized void file_log( String s, Throwable e )
    {
        File log = get_log_file();
        try
        {
            PrintWriter fw = new PrintWriter( new FileWriter(log,  true ) );
            fw.write( s );
            fw.write( "\n" );
            e.printStackTrace( fw );
            fw.write( "\n" );
            fw.close();
        }
        catch ( Exception exc)
        {
            System.err.println(s);
            e.printStackTrace(System.err );
        }
    }

    void set_lvl( int parseInt )
    {
        lvl = parseInt;
    }


}
