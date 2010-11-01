/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hibernate.HibernateUtil;
import dimm.home.mailarchiv.AuditLog;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Notification.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import dimm.home.serverconnect.DimmCommand;
import home.shared.CS_Constants;
import home.shared.Utilities.ParseToken;
import home.shared.hibernate.Backup;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;



class SourceTargetEntry
{
    String name;
    File source;
    String target;
    boolean is_file;
    boolean allow_errors;

    public SourceTargetEntry( String name, File source, String target, boolean is_file, boolean allow_errors )
    {
        this.name = name;
        this.source = source;
        this.target = target;
        this.is_file = is_file;
        this.allow_errors = allow_errors;
    }
    public SourceTargetEntry( String name, File source, String target, boolean is_file )
    {
        this( name, source, target, is_file, false );
    }

    public String getName()
    {
        return name;
    }


    public File getSource()
    {
        return source;
    }

    public String getTarget()
    {
        return target;
    }

    public boolean is_file()
    {
        return is_file;
    }
    boolean post_action()
    {
        return true;
    }

    boolean pre_action()
    {
        return true;
    }
}
class ParamDBSourceTargetEntry extends SourceTargetEntry
{
    public ParamDBSourceTargetEntry( String name, File source, String target, boolean is_file )
    {
        super( name, source, target, is_file );
    }

    @Override
    boolean post_action()
    {
         HibernateUtil.reopen_db();
         return true;
    }

    @Override
    boolean pre_action()
    {
       // SHUTDOWN DB DURING SYNC
        HibernateUtil.shutdown_db();
        return true;
    }


}
class AuditDBSourceTargetEntry extends SourceTargetEntry
{
    public AuditDBSourceTargetEntry( String name, File source, String target, boolean is_file )
    {
        super( name, source, target, is_file );
    }
    @Override
    boolean post_action()
    {
         AuditLog.getInstance().reopen_db();
         return true;
    }

    @Override
    boolean pre_action()
    {
         AuditLog.getInstance().close_db();
         return true;
    }

}
/**
 *
 * @author mw
 */
public class BackupScript extends WorkerParentChild
{
    private static final int BA_TIME_WINDOW_S = 60;
    private static final int SY_FILEJOB_ID = 9999;

    Backup backup;
    long next_ba_time_s;
    boolean backup_was_started;
    Date base_date;
    Thread ba_thread;
    String job_status;
    boolean last_result_ok;
    boolean last_result_nok;

    public BackupScript( Backup _ba )
    {
        backup = _ba;

        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat(CS_Constants.BACK_STARTDATE_FORMAT);
            base_date = sdf.parse(backup.getValidfrom());
        }
        catch (Exception parseException)
        {
            LogManager.msg_backup( LogManager.LVL_ERR, "Startdatum_ist_nicht_okay", parseException);
            base_date = new Date();
        }
    }

    boolean test_flag( int f )
    {
        try
        {
            int flags = Integer.parseInt(backup.getFlags());
            return (flags & f) == f;
        }
        catch (Exception numberFormatException)
        {
        }
        return false;
    }
   

    @Override
    public void run_loop()
    {
        started = true;

        next_ba_time_s = calc_next_ba_time();

        while (!do_finish)
        {
            sleep_seconds(1);

            if (test_flag(CS_Constants.BACK_DISABLED))
                continue;
            

            long now_s = System.currentTimeMillis() / 1000;
            // NOT IN TIMEWINDOW ?
            if (now_s < next_ba_time_s || now_s > (next_ba_time_s + BA_TIME_WINDOW_S ))
            {
                // FIRST TIME AFTER BACKUP ?
                if (backup_was_started)
                {
                    // RESET FLAG AND CALC NEXT START
                    backup_was_started = false;
                    next_ba_time_s = calc_next_ba_time();
                }
                continue;
            }

            // WE WERE STARTED ALREADY IN THIS TIMEWINDOW
            if (backup_was_started)
            {
                continue;
            }

            // YEEHAW, WE DO SOME BACKUP HERE
            backup_was_started = true;

            start_backup();

        }
        finished = true;
    }

    @Override
    public void idle_check()
    {
        synchronized (this)
        {
        }
    }

    public Backup get_backup()
    {
        return backup;
    }


    @Override
    public String get_task_status_txt()
    {
        return "";
    }

    @Override
    public Object get_db_object()
    {
        return backup;
    }



    @Override
    public String get_name()
    {
        return "BackupScript";
    }

    private long calc_next_ba_time()
    {
        if (test_flag( CS_Constants.BACK_CYCLE))
        {
            return calc_next_cycle_time_s();
        }
        else
        {
            return calc_next_schedule_time_s();
        }        
    }

    private long calc_next_cycle_time_s()
    {
        long now_s = System.currentTimeMillis() / 1000;

        long base_start_time_s = base_date.getTime() / 1000;

        int secs_from_unit = get_secs_from_unit( backup.getCycleunit() );

        int cycle_secs = secs_from_unit * backup.getCycleval().intValue();
        if (cycle_secs == 0)
        {
            // BETTER A STRANGE BACKUP THAN NONE...
            cycle_secs = (6*60*60);
        }
        
        // CALC DIFFERENZ TO BASE DATE
        long rel_s = now_s - base_start_time_s;

        // IN THE FUTURE
        if (rel_s < 0)
        {
            // THIS IS SIMPLE THE
            return base_start_time_s;
        }
        
        // OK NEXT START IS BASE PLUS ACT TIME MODULO CyCLE
        long n_cycles = rel_s / cycle_secs;
        return base_start_time_s + (n_cycles + 1) * cycle_secs;
    }

    private long calc_next_schedule_time_s()
    {
        long now_s = System.currentTimeMillis() / 1000;
        long base_start_time_s = base_date.getTime() / 1000;

        String [] time_list = backup.getSchedtime().split(CS_Constants.TEXTLIST_DELIM);
        String [] enable_list = backup.getSchedenable().split(CS_Constants.TEXTLIST_DELIM);
        GregorianCalendar cal = new GregorianCalendar();
        // CAL NOW
        cal.setTime(new Date());


        long ba_time_s = 0;

        int day_idx = 0;
        // WE HAVE TO CHECK 2 WEEKS ( THIS ONE AND THE NEXT ONE)
        while (day_idx < enable_list.length*2)
        {
            int day = day_idx % 7;
            boolean enable = enable_list[day] != null && enable_list[day].length() > 0 && enable_list[day].charAt(0) == '1';
            if (!enable)
            {
                day_idx++;
                continue;
            }

            int h = 0;
            int m = 0;
            try
            {
                String[] time = time_list[day].split(":");
                h = Integer.parseInt(time[0]);
                m = Integer.parseInt(time[1]);
                cal.set( GregorianCalendar.DAY_OF_WEEK, get_greg_day_of_week(day)  );
                cal.set( GregorianCalendar.HOUR_OF_DAY, h );
                cal.set( GregorianCalendar.MINUTE, m );
                cal.set( GregorianCalendar.SECOND, 0 );
            }
            catch( Exception exc)
            {
                day_idx++;
                continue;
            }
            ba_time_s = cal.getTime().getTime() / 1000;
            
            // BASETIME IN FUTURE: TAKE FIRST ENABLED ENTRY
            if (base_start_time_s > now_s)
            {
                cal.setTime(base_date);
                cal.set( GregorianCalendar.DAY_OF_WEEK, get_greg_day_of_week(day)  );
                cal.set( GregorianCalendar.HOUR_OF_DAY, h );
                cal.set( GregorianCalendar.MINUTE, m );
                cal.set( GregorianCalendar.SECOND, 0 );
                return cal.getTime().getTime() / 1000;
            }

            // FOUND FIRST TIME IN THE FUTURE
            if (ba_time_s > now_s)
                break;

                // SWITCH TO NEXT WEEK
            if (day_idx == 6)
            {
                cal.add( GregorianCalendar.WEEK_OF_YEAR, 1 );
            }
            day_idx++;
        }
        
        return ba_time_s;
    }

    private int get_secs_from_unit( String cycleunit )
    {
        for (int i = 0; i < CS_Constants.BY_CYCLE_UNITS.length; i++)
        {
            if (cycleunit.equals(CS_Constants.BY_CYCLE_UNITS[i]))
            {
                return CS_Constants.BY_CYCLE_UNITS_SECS[i];
            }
        }
        return (24*60*60); // ONE DAY ON ERROR
    }

    private int get_ba_day( int get )
    {
        switch( get )
        {
            case GregorianCalendar.MONDAY: return 0;
            case GregorianCalendar.TUESDAY: return 1;
            case GregorianCalendar.WEDNESDAY: return 2;
            case GregorianCalendar.THURSDAY: return 3;
            case GregorianCalendar.FRIDAY: return 4;
            case GregorianCalendar.SATURDAY: return 5;
            case GregorianCalendar.SUNDAY: return 6;
        }
        return -1;
    }

    private int get_greg_day_of_week( int i )
    {
        switch( i )
        {
            case 0: return GregorianCalendar.MONDAY;
            case 1: return GregorianCalendar.TUESDAY;
            case 2: return GregorianCalendar.WEDNESDAY;
            case 3: return GregorianCalendar.THURSDAY;
            case 4: return GregorianCalendar.FRIDAY;
            case 5: return GregorianCalendar.SATURDAY;
            case 6: return GregorianCalendar.SUNDAY;
        }
        return GregorianCalendar.MONDAY;
    }

    public boolean start_backup()
    {
        final MandantContext m_ctx = Main.get_control().get_mandant_by_id(backup.getMandant().getId());

        long da_id = backup.getDiskArchive().getId();
        Vault vault = m_ctx.get_vault_by_da_id(da_id);

        last_result_ok = false;
        last_result_nok = false;


        // WE WEANT TO COPY ALL DISKSPACES AND MAYBE THE SYSTEMDATA
        final ArrayList<SourceTargetEntry> backup_dir_list = new ArrayList<SourceTargetEntry>();
        if (!(vault instanceof DiskVault))
        {
            LogManager.msg_backup( LogManager.LVL_ERR, "Backup ist only supported for Diskvaults");
            return false;
        }
        final DiskVault dv = (DiskVault) vault;

        if (test_flag(CS_Constants.BACK_SYS))
        {
            backup_dir_list.add( new ParamDBSourceTargetEntry( "System db", new File( Main.work_dir + "/MailArchiv"), get_system_subpath(m_ctx), false) );
            backup_dir_list.add( new AuditDBSourceTargetEntry( "System auditdb", new File( Main.work_dir + "/AuditDB"), get_system_subpath(m_ctx), false) );
            backup_dir_list.add( new SourceTargetEntry( "System prefs", new File( Main.work_dir + "/preferences"), get_system_subpath(m_ctx), false) );
            backup_dir_list.add( new SourceTargetEntry( "System lib", new File( Main.work_dir + "/lib"), get_system_subpath(m_ctx), false) );
            backup_dir_list.add( new SourceTargetEntry( "System lic", new File( Main.work_dir + "/license"), get_system_subpath(m_ctx), false) );
            backup_dir_list.add( new SourceTargetEntry( "System Server", new File( Main.work_dir + "/MailArchiv.jar"), get_system_subpath(m_ctx), true) );
            backup_dir_list.add( new SourceTargetEntry( "System logs", new File( Main.work_dir + "/logs"), get_system_subpath(m_ctx), false, /*allow_errors*/true) );
        }

        for (int i = 0; i < dv.get_dsh_list().size(); i++)
        {
            DiskSpaceHandler dsh = dv.get_dsh_list().get(i);
            String name = dv.get_name() + " DS " + dsh.getDs().getId();
            File src = new File( dsh.getDs().getPath());
            String trg = get_ds_subpath(m_ctx, dv, dsh);

            SourceTargetEntry ste = new SourceTargetEntry( name, src, trg, false );


            backup_dir_list.add( ste  );
        }

        if (ba_thread != null && ba_thread.isAlive())
        {
            LogManager.msg_backup( LogManager.LVL_ERR, "Backup already running");
            return false;
        }



        final String ag_ip = backup.getAgentip();
        final int ag_port = backup.getAgentport();
        final String ag_path = backup.getAgentpath();

        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {

                    backup_entrylist( m_ctx, dv, backup_dir_list, ag_ip, ag_port, ag_path );
                }
                catch (Exception ex)
                {
                     LogManager.msg_backup( LogManager.LVL_ERR, "Error occured while running backup script " + backup.getId() + ": ", ex);
                }
                
            }
        };

        ba_thread = new Thread( r, "BackupScript " + backup.getId() );
        ba_thread.start();

        return true;
       
    }


    String get_ds_subpath( MandantContext m_ctx, DiskVault dv, DiskSpaceHandler dsh )
    {
        return m_ctx.getMandant().getId() + "." + dv.get_da().getId() + "." + dsh.getDs().getId();
    }
    String get_system_subpath( MandantContext m_ctx )
    {
        return "" + m_ctx.getMandant().getId();
    }

    boolean do_abort = false;
    private boolean backup_entrylist( MandantContext m_ctx, DiskVault dv, ArrayList<SourceTargetEntry> backup_dir_list, String ag_ip, int ag_port, String ag_path )
    {
        boolean ok = true;
        do_abort = false;

        DimmCommand sync_cmd = new DimmCommand( (int)Main.get_long_prop(GeneralPreferences.SYNCSRV_PORT, 11170 ) );
        if (!sync_cmd.connect())
        {
            return false;
        }
        try
        {
       // { "copy_files", "		    SRC_NAME SRC_IP SRC_PORT SRC_PATH TRG_NAME TRG_IP TRG_PORT TRG_PATH", handle_copy_files, SYSTEM_FUNC},
            for (int i = 0; i < backup_dir_list.size(); i++)
            {
                SourceTargetEntry ste = backup_dir_list.get(i);
                

                String targetpath =  ag_path + "/" + ste.getTarget();
                String src_path = ste.getSource().getAbsolutePath();
                if (src_path.length() > 3 && src_path.endsWith("."))
                {
                    src_path = src_path.substring(0, src_path.length() - 2);
                }
                String no_err_txt = " NO_ERRORS";
                if (ste.allow_errors)
                    no_err_txt = "";

                String cmd = "copy_files "+ (ste.is_file() ? "FILE":"FOLDER") + " \"" + ste.getName() + "\" 127.0.0.1 11172 \"" + src_path + "\" \"" +
                            ste.getName() + "\" " + ag_ip + " " + ag_port + " \"" + targetpath + "\"" + no_err_txt + " NOWAIT CP:0";

                cmd = cmd.replace('\\', '/');
                set_status(StatusEntry.BUSY, Main.Txt("Starting_backup_of") + " "+ ste.getName());

                String ret = null;
                try
                {
                    ste.pre_action();

                    ret = sync_cmd.send_cmd(cmd, 60 * 60);
                }
                catch (Exception e)
                {
                    LogManager.msg_backup( LogManager.LVL_ERR, "Error during sync", e);
                }
               
                if (ret == null)
                {
                    ste.post_action();

                    ok = false;
                    LogManager.msg_backup( LogManager.LVL_ERR, "Error while contacting backup server");
                    break;
                }
                if (ret.charAt(0) != '0')
                {
                    ste.post_action();

                    LogManager.msg_backup( LogManager.LVL_ERR, Main.Txt("Error_during_start_backup_of") + " " + ste.getName() + ": " + ret);
                    ok = false;
                    break;
                }
                
                if (!wait_for_sync_ready( sync_cmd ))
                {
                    ok = false;
                }
                ste.post_action();


                if (do_abort)
                    break;

            }
        }
        catch (Exception exc )
        {
            LogManager.msg_backup( LogManager.LVL_ERR, Main.Txt("Error_during_start_backup"), exc);
        }
        finally
        {
            sync_cmd.disconnect();
            do_abort = false;
        }

        if (ok)
        {
            last_result_ok = true;
            set_status(StatusEntry.SLEEPING, Main.Txt("Last_backup_succeeded"));
        }
        else
        {
            last_result_nok = true;
            Notification.throw_notification(m_ctx.getMandant(), Notification.NF_ERROR, Main.Txt("Backup_for_disk_archive_failed") + ": " + dv.get_name() );
            set_status(StatusEntry.ERROR, Main.Txt("Last_backup_failed"));
        }
        
        return ok;
    }
    public void abort_backup( )
    {
        // STOP PENDING JOBLIST
         do_abort = true;

         DimmCommand sync_cmd = new DimmCommand( (int)Main.get_long_prop(GeneralPreferences.SYNCSRV_PORT, 11170 ) );
         sync_cmd.connect();
         abort_backup(sync_cmd);
         sync_cmd.disconnect();
    }
    
    void abort_backup(DimmCommand sync_cmd )
    {
        job_status = null;
        String ret = sync_cmd.send_cmd( "abort_task " + SY_FILEJOB_ID  );
        if (ret == null || ret .charAt(0) != ' ')
        {
            LogManager.msg_backup( LogManager.LVL_ERR, "Error while aborting backup server");
        }
    }
    void finish_backup(DimmCommand sync_cmd )
    {
        job_status = null;
        String ret = sync_cmd.send_cmd( "abort_task " + SY_FILEJOB_ID  );
        if (ret == null || ret .charAt(0) != ' ')
        {
            LogManager.msg_backup( LogManager.LVL_ERR, "Error while finishing backup server");
        }
    }

    private boolean wait_for_sync_ready(DimmCommand sync_cmd )
    {
        job_status = null;
        while (true)
        {
            sleep_seconds(2);
            
            if (do_finish)
            {
                abort_backup(sync_cmd);
                break;
            }

            String cmd = "list_sync_status";
            String ret = sync_cmd.send_cmd( cmd );
            if (ret == null || ret.charAt(0) != '0')
            {
                LogManager.msg_backup( LogManager.LVL_ERR, "Error while contacting backup server");
                break;
            }
            // EMPTY ?
            if (ret.length() < 10)
            {
                job_status = null;
                break;
            }
            ParseToken pt = new ParseToken(ret);
            long id = pt.GetLong("ID:");
            if (id != SY_FILEJOB_ID)
            {
                job_status = null;
                break;
            }

            job_status = ret;
            // "ID:%ld TB:%.0lf CB:%.0lf TF:%ld TD:%ld CF:%ld CD:%ld SP:%.0lf STF:%.0lf SCF:%.0lf LT:%.0lf PC:%d",
            //System.out.println(ret);
            
            long state = pt.GetLong("JS:");
            if (state == DimmCommand.JOB_READY)
            {
                finish_backup( sync_cmd );
                return true;
            }
            if (state == DimmCommand.JOB_ERROR)
            {
                abort_backup( sync_cmd );
                break;
            }
            if (do_abort)
                break;
        }

        String ret = sync_cmd.send_cmd( "list_results ID 9999 LINES 1");
        if (ret != null && ret.charAt(0) == '0')
        {
            ParseToken pt = new ParseToken(ret);
            long err_code = pt.GetLongValue("CD:");
            if (err_code != 0)
                return false;
            
            return true;
        }

        return false;
    }

    public String get_sync_status()
    {
        job_status = null;

        DimmCommand sync_cmd = new DimmCommand( (int)Main.get_long_prop(GeneralPreferences.SYNCSRV_PORT, 11170 ) );
        if (!sync_cmd.connect())
        {
            return "1: Cannot connect backup server";
        }

        String cmd = "list_sync_status" ;
        String ret = sync_cmd.send_cmd( cmd );
        if (ret == null)
        {
            return "1: error while contacting backup server";
        }
        if (ret.charAt(0) != '0')
        {
            return "2: error from backup server: " + ret;
        }
        ParseToken pt = new ParseToken(ret);
        long id = pt.GetLong("ID:");
        if (id == SY_FILEJOB_ID)
        {
            job_status = ret;
        }

        sync_cmd.disconnect();
        return ret;
    }

    public String get_job_status()
    {
        get_sync_status();

        if (job_status != null)
        {
            return job_status;
        }
        return "0: NS:" + next_ba_time_s + " LOK:" + (last_result_ok ? "1" : "0") + " LNOK:" + (last_result_nok ? "1" : "0");
    }
}
