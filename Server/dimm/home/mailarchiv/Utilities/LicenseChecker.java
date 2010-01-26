/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import home.shared.mail.RFCMailAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;






class StatMailAddress extends RFCMailAddress
{
    long rcvCnt;
    long sndCnt;
    long timestamp;

    public StatMailAddress(String adr, ADR_TYPE type, long rcv_cnt, long snd_cnt )
    {
        super(adr, type);
        this.rcvCnt = rcv_cnt;
        this.sndCnt = snd_cnt;
    }

    public long getSndCnt()
    {
        return sndCnt;
    }

    public long getRcvCnt()
    {
        return rcvCnt;
    }

    public long incSndCnt()
    {
        timestamp = System.currentTimeMillis();
        return ++sndCnt;
    }

    public long incRcvCnt()
    {
        timestamp = System.currentTimeMillis();
        return ++rcvCnt;
    }

    public long getTimestamp()
    {
        return timestamp;
    }
}
/**
 *
 * @author mw
 */
public class LicenseChecker
{
    boolean _is_licensed;
    boolean _create_licensefile;
    String license_interface;

    String lic_file_name;
    private long MAX_STAY_VALID_MS = 6*7*24*1600*1000;  // 6 WEEKS
    

    public LicenseChecker(String file_name, String intf, boolean crt_lf)
    {
        lic_file_name = file_name;
        license_interface = intf;
        _is_licensed = false;
        _create_licensefile = crt_lf;

        MAX_STAY_VALID_MS = Main.get_long_prop(GeneralPreferences.MAX_STAY_VALID_DAYS, (long)42) * 24*1600*1000;


        try
        {
            if (license_interface == null)
            {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

                while ( en.hasMoreElements() )
                {
                    NetworkInterface ni = en.nextElement();
                    license_interface = ni.getName();
                    LogManager.log(Level.FINE, "Found Interface: " + license_interface );
                    if (!license_interface.startsWith("lo") && ni.getHardwareAddress() != null &&  ni.getHardwareAddress().length > 0)
                        break;
                }
            }
        }
        catch (Exception socketException)
        {
            LogManager.err_log("Cannot detect network interface, running foot loose", socketException);
        }

        read_license_map();
    }

    public boolean is_licensed()
    {
        return _is_licensed;
    }
    public File get_license_file()
    {
        return new File(lic_file_name);
    }

    public boolean check_licensed()
    {
        _is_licensed = false;

        // IN CASE OF NETWORK FAILURE
        if (license_interface == null)
        {
            _is_licensed = true;
            return true;
        }

        NetworkInterface ni;
        {
            FileReader fr = null;
            try
            {
                ni = NetworkInterface.getByName(license_interface);
                if (ni == null || ni.getHardwareAddress() == null)
                {
                    Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

                    while ( en.hasMoreElements() )
                    {
                        System.out.println("Available interfaces:");
                        System.out.println( en.nextElement().getName() );
                    }

                    throw new Exception("invalid interface " + license_interface + " or we have no hardware address");
                }

                byte[] mac = ni.getHardwareAddress();

                MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.reset();
                md5.update(mac);
                byte[] result = md5.digest();


                /* Ausgabe */
                StringBuffer hexString = new StringBuffer();
                for (int i = 1; i <= result.length; i++)
                {
                    hexString.append(Integer.toHexString(0xFF & result[result.length - i]));
                }

                if (_create_licensefile)
                {
                    File licfile = new File(lic_file_name);
                    FileWriter fw = new FileWriter(licfile);
                    fw.write(hexString.toString());
                    fw.close();
                    LogManager.info_msg("License file was created");

                    _is_licensed = true;
                    return true;
                }
                else
                {
                    File licfile = new File(lic_file_name);
                    fr = new FileReader(licfile);

                    char[] buff = new char[40];
                    int len = fr.read(buff);


                    String lic_string = new String(buff, 0, len);


                    if (lic_string.equals(hexString.toString()))
                    {
                        _is_licensed = true;
                        return true;
                    }

                    LogManager.err_log_fatal("Unlicensed host");
                }
            }
            catch (NoSuchAlgorithmException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }
            catch (FileNotFoundException ex2)
            {
                ex2.printStackTrace();
                LogManager.err_log_fatal("Missing licensefile");
            }
            catch (SocketException ex3)
            {
                LogManager.err_log_fatal("No network interface for licensecheck");
            }
            catch (IOException ex1)
            {
                LogManager.err_log_fatal("Error while reading licensefile: " + ex1.getMessage());
            }
            catch (Exception ex4)
            {
                LogManager.err_log_fatal("Error during license check: " + ex4.getMessage());
            }
            finally
            {
                try
                {
                    if (fr != null)
                    {
                        fr.close();
                    }
                }
                catch (IOException ex)
                {
                }
            }
        }
        return false;
    }





    HashMap<String,StatMailAddress> license_map;
    public static final String LICFILE_NAME = "umap.xml";

    private void read_license_map()
    {
        String path = Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH);

        File tmp_path = new File( path, LICFILE_NAME);
        if (tmp_path.exists())
        {
            FileInputStream fis = null;
            XStream xs = new XStream();
            try
            {
                fis = new FileInputStream(tmp_path);
                Object o = xs.fromXML(fis);
                if (o instanceof HashMap)
                {
                    license_map = (HashMap<String, StatMailAddress>)o;
                }
            }
            catch (FileNotFoundException fileNotFoundException)
            {
            }
            finally
            {
                if (fis != null)
                {
                    try
                    {
                        fis.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
        else
        {
            LogManager.err_log_warn(Main.Txt("Beginning_a_new_usermap,_file_was_not_found"));
            license_map = new HashMap<String,StatMailAddress>();
        }

    }
    public void update_license_map()
    {
        String path = Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH);
        File tmp_path = new File( path, LICFILE_NAME);
        FileOutputStream fos = null;
        XStream xs = new XStream();
        try
        {
            fos = new FileOutputStream(tmp_path);
            xs.toXML(license_map, fos);
        }
        catch (Exception exception)
        {
            LogManager.err_log_warn(Main.Txt("Cannot_write_usermap") + ": " + exception.getLocalizedMessage());
        }
        finally
        {
            if (fos != null)
            {
                try
                {
                    fos.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
    }

    RFCMailAddress get_matching_license_email(ArrayList<String> domain_list, ArrayList<RFCMailAddress> email_list )
    {
        for (int i = 0; i < email_list.size(); i++)
        {
            RFCMailAddress adr = email_list.get(i);
            
            for (int j = 0; j < domain_list.size(); j++)
            {
                String domain = domain_list.get(j);
                if (adr.get_domain().compareToIgnoreCase(domain) == 0)
                {
                    return adr;
                }
            }
        }
        return null;
    }

    public boolean is_license_exceeded( ArrayList<String> domain_list, ArrayList<RFCMailAddress> email_list )
    {

        RFCMailAddress email = get_matching_license_email( domain_list, email_list);

        // THIS ONE IS TRICKY: WE HAVE NOT FOUND AN EMAIL TO LICENSE, THIS SHOULD NOT HAPPEN, BECAUSE THIS MAILD SHOULD HAVE BEEN REJECTED BEFORE
        if (email == null)
        {
            LogManager.err_log_warn(Main.Txt("No_email_to_license_found") );
            return false;
        }

        StatMailAddress adr = license_map.get(email.get_mail());
        if (adr == null)
        {
            if (license_map.size() >= get_max_units())
            {
                return false;
            }
            adr = new StatMailAddress( email.get_mail(), email.getAdr_type(), 0, 0);
            license_map.put(email.get_mail(), adr);
        }
        if (email.is_from())
        {
            adr.incSndCnt();
        }
        else
        {
            adr.incRcvCnt();
        }

        return false;

    }

    private int get_max_units()
    {
        // TODO GET UNITS FROM LICENSE FILE
        return 100;
    }

    // THIS CAN BE TIME CONSUMING, NOT TOO OFTEN; IS NOT CRITICAL
    void do_idle()
    {
        long now = System.currentTimeMillis();

        Set<Entry<String,StatMailAddress>> set = license_map.entrySet();
        for (Entry<String, StatMailAddress> entry : set)
        {
            if ((now - entry.getValue().getTimestamp()) > MAX_STAY_VALID_MS)
            {
                LogManager.info_msg(Main.Txt("Removing_inactive_user_from_usermap") + ": " + entry.getValue().get_mail() );
                set.remove(entry);
            }
        }


    }

}
