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

    public String get_prop( String p )
    {
        if (!check_prop(p))
        {
            Main.err_log_warn("Unbekannte property <" + p + ">");
            return null;
        }
        String ret = props.getProperty(p);
        return ret;
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
            FileInputStream istr = new FileInputStream(prop_file);
            props.load(istr);
            istr.close();
        }
        catch (Exception exc)
        {
            System.out.println("Kann Properties nicht lesen: " + exc.getMessage());
        }
    }

    public void set_prop( String p, String v )
    {
        if (!check_prop(p))
        {
            Main.err_log_warn("Unbekannte property <" + p + ">");
        }
        props.setProperty(p, v);
    }

    public boolean store_props()
    {
        File prop_file = new File(prefs_path + "preferences.dat");
        try
        {
            FileOutputStream ostr = new FileOutputStream(prop_file);
            props.store(ostr, "JMailProxy Properties, please do not edit");
            ostr.close();
            return true;
        }
        catch (Exception exc)
        {
            Main.err_log("Kann Properties nicht schreiben: " + exc.getMessage());
        }
        return false;
    }

}
