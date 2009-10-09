/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import dimm.home.mailarchiv.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 *
 * @author mw
 */
public class Preferences
{
    String prefs_path;
    protected ArrayList<String> prop_names;
    Properties props;

    public Preferences( String _path )
    {
        prefs_path = _path;
        if (prefs_path.charAt(prefs_path.length() - 1 ) != '/')
            prefs_path += "/";

        prop_names = new ArrayList<String>();

    }

    public Preferences()
    {
        this(Main.PREFS_PATH);
    }

    String base_prop_name( String s )
    {
        int idx = s.lastIndexOf("_");
        if (idx >= 0)
        {
            try
            {
                int n = Integer.parseInt(s.substring(idx + 1));
                return s.substring(0, idx);
            }
            catch (Exception exc)
            {
            }
        }
        return s;
    }

    boolean check_prop( String s )
    {
        for (int i = 0; i < prop_names.size(); i++)
        {
            String base_prop = base_prop_name(s);
            if (prop_names.get(i).compareTo(base_prop) == 0)
            {
                return true;
            }
        }
        return false;
    }
    private void warn_unknown( String p)
    {
        Main.err_log_warn(Main.Txt("Unknown_property") + " <" + p + ">");
    }

    public String get_prop( String p )
    {
        if (!check_prop(p))
        {
            warn_unknown(p);
            return null;
        }
        String ret = props.getProperty(p);
        return ret;
    }
    public String get_prop( String p, String def )
    {
        if (!check_prop(p))
        {
            warn_unknown(p);
            return null;
        }
        String ret = props.getProperty(p);
        if (ret == null)
            ret = def;
        
        return ret;
    }

    public boolean get_boolean_prop( String p, boolean  def )
    {
        if (!check_prop(p))
        {
            warn_unknown(p);
            return false;
        }
        String ret = props.getProperty(p);

        if (ret == null || ret.length() == 0)
            return def;

        try
        {
            if (ret.charAt(0) == '1' || ret.toLowerCase().charAt(0) == 'j' ||ret.toLowerCase().charAt(0) == 'y' )
                return true;
        }
        catch (Exception exc)
        {
            LogManager.err_log( Main.Txt("Cannot_read_boolean_property") + ": ", exc);
        }

        return false;
    }
    public boolean get_boolean_prop( String p)
    {
        return get_boolean_prop(p, false);
    }


    public ArrayList<String> get_prop_list()
    {
        return prop_names;
    }

    public void read_props()
    {
        File prop_file = new File(prefs_path + "preferences.dat");
        props = new Properties();
        try
        {
            if (prop_file.exists())
            {
                FileInputStream istr = new FileInputStream(prop_file);
                props.load(istr);
                istr.close();
            }
        }
        catch (Exception exc)
        {
            LogManager.err_log( Main.Txt("Cannot_read_properties") + " " + prop_file.getAbsolutePath() + ": ", exc);
        }
    }

    public void set_prop( String p, String v )
    {
        if (!check_prop(p))
        {
            warn_unknown(p);
        }
        props.setProperty(p, v);
    }

    public boolean store_props()
    {
        File prop_file = new File(prefs_path + "preferences.dat");
        try
        {
            FileOutputStream ostr = new FileOutputStream(prop_file);
            props.store(ostr, Main.APPNAME + " Properties, please do not edit");
            ostr.close();
            return true;
        }
        catch (Exception exc)
        {
            LogManager.err_log( Main.Txt("Cannot_write_properties") + " " + prop_file.getAbsolutePath() + ": ", exc);
        }
        return false;
    }

}
