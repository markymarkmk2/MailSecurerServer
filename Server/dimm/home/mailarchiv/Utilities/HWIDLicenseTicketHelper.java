/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import home.shared.license.HWIDLicenseTicket;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author mw
 */
public class HWIDLicenseTicketHelper
{       

    public static String generate_hwid() throws IOException
    {
        try
        {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

            while (en.hasMoreElements())
            {
                NetworkInterface ni = en.nextElement();
                if (ni.getName().startsWith("lo") || ni.getHardwareAddress() == null || ni.getHardwareAddress().length == 0)
                    continue;

                byte[] mac = ni.getHardwareAddress();
                String str_mac = new String(Base64.encodeBase64(mac), "UTF-8");
                return str_mac;
            }
        }
        catch (Exception exc)
        {
            throw new IOException(exc.getLocalizedMessage());
        }

        return null;
    }

    
    public static boolean isValid( HWIDLicenseTicket ticket )
    {
        // CHECK PARENT DATA FIRST
        if (!ticket.isValid())
        {
            return false;
        }
        try
        {
            // LOOK FOR A VALID INTERFACE
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

            while (en.hasMoreElements())
            {
                byte[] mac = en.nextElement().getHardwareAddress();
                if (mac != null)
                {
                    String str_mac = new String(Base64.encodeBase64(mac), "UTF-8");
                    if (str_mac.compareToIgnoreCase(ticket.getHwid()) == 0)
                    {
                        return true;
                    }
                }
            }
            ticket.setLastErrMessage( "HWID_does_not_match" );
        }
        catch (Exception exc)
        {
            ticket.setLastErrMessage( "Cannot_check_HWID: " + exc.getLocalizedMessage() );
            if (ticket.getLogListener() != null)
                ticket.getLogListener().error_log( ticket.getLastErrMessage() );
        }
        return false;
    }

   
  
}