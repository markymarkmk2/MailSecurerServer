/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.DirectoryEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import dimm.home.serverconnect.DimmCommand;
import home.shared.CS_Constants;
import home.shared.hibernate.Backup;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 *
 * @author mw
 */
public class BackupScript extends WorkerParentChild
{
    private static final int BA_TIME_WINDOW_S = 60;

    Backup backup;
    long next_ba_time_s;
    boolean backup_was_started;
    Date base_date;

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
            LogManager.err_log("Startdatum_ist_nicht_okay", parseException);
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
        ArrayList<DirectoryEntry> last_entry_list = new ArrayList<DirectoryEntry>();
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(backup.getMandant().getId());
        long da_id = backup.getDiskArchive().getId();
        Vault vault = m_ctx.get_vault_by_da_id(da_id);

        next_ba_time_s = calc_next_ba_time();

        while (!do_finish)
        {
            sleep_seconds(1);

            if (test_flag(CS_Constants.BACK_DISABLED))
                continue;
            

            long now_s = System.currentTimeMillis() / 1000;
            // NOT IN TIMEWINDOW ?
            if (now_s < next_ba_time_s || now_s > (next_ba_time_s + BA_TIME_WINDOW_S * 1000))
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
        synchronized (backup)
        {
        }
    }

    


    public Backup get_hf()
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
        if (rel_s <= base_start_time_s)
        {
            // THIS IS SIMPLE THE
            return base_start_time_s;
        }
        
        // OK NEXT START IS BASE PLUS ACT TIME MODULO CyCLE
        long n_cycles = rel_s / cycle_secs;
        return base_start_time_s + n_cycles * cycle_secs;
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
                continue;

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

    private void start_backup()
    {
        DimmCommand sync_cmd = new DimmCommand( (int)Main.get_long_prop(GeneralPreferences.SYNCSRV_PORT, 11170 ) );
        sync_cmd.send_cmd( "copy_files" );
    }

}
