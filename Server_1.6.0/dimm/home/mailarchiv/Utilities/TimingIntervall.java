/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 *
 * @author Administrator
 */
public class TimingIntervall
{
    private int imap_idle_time_s = 0;
    private int from_hour = 0;
    private int till_hour = 0;

    static GregorianCalendar cal = new GregorianCalendar();

    @Override
    public String toString()
    {
        if (from_hour != till_hour)
            return imap_idle_time_s + "s " + from_hour  + ":00 - " + till_hour + ":00";

        return imap_idle_time_s + "s";
    }

    public int getFrom_hour()
    {
        return from_hour;
    }

    public int getImap_idle_time_s()
    {
        return imap_idle_time_s;
    }

    public int getTill_hour()
    {
        return till_hour;
    }

    
    public static TimingIntervall parse( String entry )
    {
        TimingIntervall tm = null;
        String[] vals = entry.split("[()-]");
        ArrayList<Integer> ivals = new ArrayList<Integer>();

        for (int j = 0; j < vals.length; j++)
        {
            if (vals[j] != null && vals[j].length() > 0)
            {
                try
                {
                    ivals.add(Integer.parseInt(vals[j]));
                }
                catch (NumberFormatException numberFormatException)
                {
                    throw new IllegalArgumentException("Wrong timing format: " + entry);
                }
            }
        }
        if (ivals.size() == 1)
        {
            tm = new TimingIntervall();

            tm.imap_idle_time_s = ivals.get(0).intValue();
            if (tm.imap_idle_time_s <= 0)
                throw new IllegalArgumentException("Wrong timing cycle: " + entry);
        }
        else if (ivals.size() == 3)
        {
            tm = new TimingIntervall();

            tm.imap_idle_time_s = ivals.get(0).intValue();
            if (tm.imap_idle_time_s <= 0)
                throw new IllegalArgumentException("Wrong timing cycle: " + entry);

            tm.from_hour = ivals.get(1).intValue();
            if (tm.from_hour < 0 || tm.from_hour > 24 )
                throw new IllegalArgumentException("Wrong timing start: " + entry);

            tm.till_hour = ivals.get(2).intValue();
            if (tm.till_hour < 0 || tm.till_hour > 24 )
                throw new IllegalArgumentException("Wrong timing end: " + entry);
        }
        else
        {
            throw new IllegalArgumentException("Wrong timing format: " + entry);
        }
        return tm;
    }
    public static ArrayList<TimingIntervall> eval_timing(String timing_string,  String log_typ)
    {
        ArrayList<TimingIntervall> timing_list  = new ArrayList<TimingIntervall>();
        if (timing_string != null && timing_string.length() > 0)
        {

            String[] entries = timing_string.split(" ");
            for (int i = 0; i < entries.length; i++)
            {
                String entry = entries[i];
                String[] vals = entry.split("[()-]");
                ArrayList<Integer> ivals = new ArrayList<Integer>();

                try
                {
                    TimingIntervall tm = TimingIntervall.parse(entry);
                    timing_list.add(tm);
                }
                catch (IllegalArgumentException illegalArgumentException)
                {
                    LogManager.msg(LogManager.LVL_DEBUG, log_typ, "Reading IMAP timing failed: " + illegalArgumentException.getMessage() );
                }
            }
        }
        return timing_list;
    }

    public static int get_idle_time(  ArrayList<TimingIntervall> timing_list, int dflt_idle)
    {
        int found_idle_time = dflt_idle;
        int h = get_act_hour();


        // LOOK FOR MATCHING ENTRY
        for (int i = 0; i < timing_list.size(); i++)
        {
            TimingIntervall imapTiming = timing_list.get(i);

            // W/O TIME MEANS ALL THE TIME
            if (imapTiming.getFrom_hour() == imapTiming.getTill_hour())
            {
                found_idle_time = imapTiming.getImap_idle_time_s();
            }
            else if (imapTiming.getFrom_hour() <= h && h < imapTiming.getTill_hour())
            {
                found_idle_time = imapTiming.getImap_idle_time_s();
                break;
            }
        }
        return found_idle_time;

    }
    public static boolean is_allowed_now(  ArrayList<TimingIntervall> timing_list)
    {
        if (timing_list == null)
            return true;

        boolean allowed = true;
        int h = get_act_hour();

        // LOOK FOR MATCHING ENTRY
        for (int i = 0; i < timing_list.size(); i++)
        {
            TimingIntervall imapTiming = timing_list.get(i);

            // ANYTIME?
            if (imapTiming.getFrom_hour() == imapTiming.getTill_hour())
            {
                allowed = true;
                continue;
            }

            // INSIDE SPECIFIC TIME
            if (imapTiming.getFrom_hour() <= h && h < imapTiming.getTill_hour())
            {
                allowed = true;
                break; // WE MAY
            }
            else
            {
                allowed = false;
            }
        }
        return allowed;
    }

    public static int get_act_hour()
    {
        Date d = new Date();        
        cal.setTime(d);
        int h = cal.get(GregorianCalendar.HOUR_OF_DAY);

        return h;
    }

}
