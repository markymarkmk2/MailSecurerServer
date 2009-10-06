/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.logging.Level;

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

    public LicenseChecker(String file_name, String intf, boolean crt_lf)
    {
        lic_file_name = file_name;
        license_interface = intf;
        _is_licensed = false;
        _create_licensefile = crt_lf;

        try
        {
            if (license_interface == null)
            {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

                while ( en.hasMoreElements() )
                {
                    license_interface = en.nextElement().getName();
                    if (!license_interface.startsWith("lo") )
                        break;
                }
            }
        }
        catch (Exception socketException)
        {
            LogManager.err_log("Cannot detect network interface, running foot loose", socketException);
        }
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


}
