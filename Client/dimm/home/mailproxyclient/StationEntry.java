package dimm.home.mailproxyclient;

/*
 * StationEntry.java
 *
 * Created on 15. Oktober 2007, 11:03
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */



/**
 *
 * @author Administrator
 */
public class StationEntry
{
    long station;
    String ip;
    String version;
    boolean dbl;
    Boolean _is_active;
    String name;
    int ping;
    boolean is_udp;
    
    public StationEntry( int s, boolean act, boolean _is_udp )
    {
        this( s, "", "", act, _is_udp );
    }
    
    public StationEntry( long s, String v, String _ip, boolean act, boolean _is_udp )
    {
        station = s;
        version = v;
        ip = _ip;
        dbl = false;
        _is_active  = new Boolean( act );
        name = "";
        is_udp = _is_udp;
    }
    public boolean get_is_udp()
    {
        return is_udp;
    }

    public int get_high_version()
    {
        if (version == null)
            return 0;
        
        try
        {
            int idx = version.indexOf(".");
            return Integer.parseInt(version.substring(0,idx));
        }
        catch (Exception exc)
        {
        }
        return 0;
        
    }
    public int get_low_version()
    {
        if (version == null)
            return 0;
        
        try
        {
            int idx = version.lastIndexOf(".");
            return Integer.parseInt(version.substring(idx + 1));
        }
        catch (Exception exc)
        {
        }
        return 0;
    }
    public int get_mid_version()
    {
        if (version == null)
            return 0;
        
        try
        {
            int idxa = version.lastIndexOf(".");
            int idxe = version.lastIndexOf(".");
            return Integer.parseInt(version.substring(idxa + 1, idxe));
        }
        catch (Exception exc)
        {
        }
        return 0;
    }
    
    @Override
    public String toString()
    {
        if (station > 0)
            return Main.SERVERAPP + " ID <" + station + ">  '" + name + "'  Version " + version + " IP: " + ip + ((dbl) ? " Doublette!" : "" + 
                    (_is_active.booleanValue()? "activ" : "inactiv") );
        
        return Main.SERVERAPP + " ohne StationsID Version " + version + " IP: " + ip;
        
    }

    public long get_id()
    {
        return station;
    }

    public String get_ip()
    {
        return ip;
    }
    public int get_ping()
    {
        return ping;
    }

    public void set_ping(int p)
    {
        ping = p;
    }
    public String get_version()
    {
        return version;
    }

     void set_dbl(boolean b)
    {
        dbl = b;
    }

    Boolean is_active()
    {        
        return _is_active;
    }

    String get_name()
    {
        return name;
    }
    void set_name(String s)
    {
        name = s;
    }
    void set_version(String s)
    {
        version = s;
    }
    void set_ip(String s)
    {
        ip = s;
    }
}