/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Utilities;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Notification.Notification;
import home.shared.license.DemoLicenseTicket;
import home.shared.license.HWIDLicenseTicket;
import home.shared.license.LicenseTicket;
import home.shared.license.ValidTicketContainer;
import home.shared.mail.RFCMailAddress;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

class StatMailAddress extends RFCMailAddress
{

    long rcvCnt;
    long sndCnt;
    long timestamp;

    public StatMailAddress( String adr, ADR_TYPE type, long rcv_cnt, long snd_cnt )
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

   // boolean _is_licensed;
  //  String lic_file_name;
    private long MAX_STAY_VALID_MS;
    private long DFLT_MAX_STAY_VALID_DAYS = 42;  // 6 WEEKS

    private static long DEMO_TICKET_DAYS = 30;  // 30 DAYS DEMO AT STARTUP

    ArrayList<ValidTicketContainer> ticket_list;
    public static final String USERMAP_NAME = "umap.xml";
    public static final String LICFILE_NAME = "license.xml";

    HashMap<String, StatMailAddress> license_user_map;
    final String umap_mtx = new String("umap_lock");
    final String lic_mtx = new String("lic_lock");

    public LicenseChecker()
    {       
        ticket_list = new ArrayList<ValidTicketContainer>();
        
        MAX_STAY_VALID_MS = Main.get_long_prop(GeneralPreferences.MAX_STAY_VALID_DAYS, DFLT_MAX_STAY_VALID_DAYS) * 24 * 3600 * 1000;

        check_create_demo_ticket();

        read_user_license_map();
    }

    private void check_create_demo_ticket()
    {
       // TRICK:
        // INSTALLER CREATES Demo_license.xml, WE DETECT THIS FILE, DELETE IT AND CREATE A REAL DEMO LICENSE
        //IN MailSecurer_license.xml
        // SO THIS WILL HAPPEN ONLY ONCE AFTER INSTALLATION!
        boolean create_lic = false;

        if (exists_ticket("Demo"))
        {
            File trick_demo_file = get_lic_file("Demo");

            // MAKE THIS ONE-SHOT, HEHE
            trick_demo_file.delete();

            if (!exists_ticket(LicenseTicket.PRODUCT_BASE))
                create_lic = true;
        }

        if (create_lic)
        {
            if (!exists_ticket(LicenseTicket.PRODUCT_BASE))
            {
                // CREATE
                Date exp = new Date(System.currentTimeMillis() + (long) DEMO_TICKET_DAYS * 86400 * 1000);
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTime(exp);
                DemoLicenseTicket ticket = new DemoLicenseTicket();
                try
                {
                    ticket.createTicket(LicenseTicket.PRODUCT_BASE, 100, 0xffff,
                            cal.get(GregorianCalendar.DAY_OF_MONTH), cal.get(GregorianCalendar.MONTH) + 1,
                            cal.get(GregorianCalendar.YEAR));

                    write_ticket(ticket);
                }
                catch (Exception iOException)
                {
                    LogManager.msg_license( LogManager.LVL_ERR, "Cannot create demo ticket:", iOException);
                }
            }
        }
    }
    
    public String get_first_hwid()
    {
        try
        {
            String hwid = HWIDLicenseTicket.generate_hwid();
            return hwid;
        }
        catch (IOException iOException)
        {
            LogManager.msg_license( LogManager.LVL_ERR, "Cannot create hwid:", iOException);
            return null;
        }
    }


    ValidTicketContainer get_ticket( String product )
    {
        synchronized(lic_mtx)
        {
        for (int i = 0; i < ticket_list.size(); i++)
        {
            ValidTicketContainer licenseTicket = ticket_list.get(i);
            if (licenseTicket.getTicket().getProduct().equalsIgnoreCase(product))
                return licenseTicket;
        }
        }
        return null;
    }
   
    public boolean is_licensed(String product)
    {
        ValidTicketContainer ticket = get_ticket(product);
        if (ticket != null)
        {
            return ticket.isValid();
        }
        return false;
    }
    
    public void check_licenses()
    {
        read_licenses();
        
     }
    public ArrayList<ValidTicketContainer> get_ticket_list()
    {
        return ticket_list;
    }

/*
    public boolean old_check_licensed()
    {
        boolean _create_licensefile = true;
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

                    while (en.hasMoreElements())
                    {
                        System.out.println("Available interfaces:");
                        System.out.println(en.nextElement().getName());
                    }

                    throw new Exception("invalid interface " + license_interface + " or we have no hardware address");
                }

                byte[] mac = ni.getHardwareAddress();

                MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.reset();
                md5.update(mac);
                byte[] result = md5.digest();


                
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
*/

    private void read_user_license_map()
    {
        license_user_map = null;

        synchronized (umap_mtx)
        {
            String path = Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH);

            File tmp_path = new File(path, USERMAP_NAME);
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
                        license_user_map = (HashMap<String, StatMailAddress>) o;
                    }
                }
                catch (Exception exc)
                {
                    LogManager.msg_license( LogManager.LVL_ERR, Main.Txt("Error_while_reading_usermap"), exc);
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
            if (license_user_map == null)
            {
                LogManager.msg_license( LogManager.LVL_WARN, Main.Txt("Beginning_a_new_usermap,_file_was_not_found_or_broken"));
                license_user_map = new HashMap<String, StatMailAddress>();
            }
        }
    }

    public void update_license_map()
    {
        synchronized (umap_mtx)
        {
            String path = Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH);
            File tmp_path = new File(path, USERMAP_NAME);
            FileOutputStream fos = null;
            XStream xs = new XStream();
            try
            {
                fos = new FileOutputStream(tmp_path);
                xs.toXML(license_user_map, fos);
            }
            catch (Exception exception)
            {
                LogManager.msg_license( LogManager.LVL_ERR, Main.Txt("Cannot_write_usermap") + ": " + exception.getLocalizedMessage());
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
    }

    ArrayList<RFCMailAddress> get_matching_license_emails( ArrayList<String> domain_list, ArrayList<RFCMailAddress> email_list )
    {
        ArrayList<RFCMailAddress> list = new ArrayList<RFCMailAddress>();
        for (int i = 0; i < email_list.size(); i++)
        {
            RFCMailAddress adr = email_list.get(i);

            for (int j = 0; j < domain_list.size(); j++)
            {
                String domain = domain_list.get(j);
                if (adr.get_domain().compareToIgnoreCase(domain) == 0)
                {
                    list.add(adr);
                }
            }
        }
        return list;
    }

    public boolean is_license_exceeded( MandantContext m_ctx, ArrayList<String> domain_list, ArrayList<RFCMailAddress> email_list )
    {
        synchronized (umap_mtx)
        {

            RFCMailAddress lic_email = null;
            
            ArrayList<RFCMailAddress> matching_email_list = get_matching_license_emails(domain_list, email_list);

            // THIS ONE IS TRICKY: WE HAVE NOT FOUND AN EMAIL TO LICENSE, THIS SHOULD NOT HAPPEN, BECAUSE THIS MAILD SHOULD HAVE BEEN REJECTED BEFORE
            if (matching_email_list.size() == 0)
            {
                LogManager.msg_license( LogManager.LVL_WARN, Main.Txt("No_email_to_license_found"));
                return false;
            }
            if (Main.get_bool_prop(GeneralPreferences.ONLY_FROM_ADRESS_LIC, true))
            {
                for (int i = 0; i < matching_email_list.size(); i++)
                {
                    RFCMailAddress rFCMailAddress = matching_email_list.get(i);
                    if (rFCMailAddress.getAdr_type() == RFCMailAddress.ADR_TYPE.FROM)
                    {
                        lic_email = rFCMailAddress;
                        break;
                    }
                }
            }

            if (lic_email == null)
            {
                // WE FOUND A VALID MAILADDRESS BUT THE FROM ADDRESS DOES NOT BELONG TO OUR DOMAINS
                return false;
            }

            // GET ENTRY FROM LICENSE MAP
            StatMailAddress adr = license_user_map.get(lic_email.get_mail());
            if (adr == null)
            {
                int max_units = get_max_units(LicenseTicket.PRODUCT_BASE);
                int used_units = get_used_units(LicenseTicket.PRODUCT_BASE);


                // NOT FOUND; WE HAVE THE FIRST MAIL FROM A NEW USER
                // -> CHECK IF UNITS IS SUFFICIENT
                if (used_units >= max_units)
                {
                    // AHH, MONEY...
                    String l_str = " (" + used_units + "/" + max_units + " Users reached)";
                    LogManager.msg_license( LogManager.LVL_ERR, Main.Txt("License_is_exceeded") + l_str);
                    Notification.throw_notification(m_ctx.getMandant(), Notification.NF_ERROR, Main.Txt("License_is_exceeded") + l_str);

                    return true;  // LEAVE WITH ERROR
                }

                // WARN EVERY 5% USERS IF MORE THAN 2/3
                if (used_units >= (max_units * 2) / 3)
                {
                    int modulo_cnt = max_units / 20;
                    int warn_type = LogManager.LVL_INFO;
                    if ((used_units * 100)/max_units > 80)
                        warn_type = LogManager.LVL_WARN;

                    if (used_units % modulo_cnt == 0)
                    {
                        String l_str = " (" + used_units + "/" + max_units + " Users reached)";
                        LogManager.msg_license( warn_type, Main.Txt("Licenseinformation") + l_str);
                        Notification.throw_notification(m_ctx.getMandant(), Notification.NF_INFORMATIVE, Main.Txt("Licenseinformation") + l_str);
                    }
                }

                // ADD THIS ENTRY TO USERMAP
                adr = new StatMailAddress(lic_email.get_mail(), lic_email.getAdr_type(), 0, 0);
                license_user_map.put(lic_email.get_mail(), adr);

                update_license_map();
            }
            if (lic_email.is_from())
            {
                adr.incSndCnt();
            }
            else
            {
                adr.incRcvCnt();
            }
        }
        return false;
    }

    public int get_max_units(String product)
    {
        ValidTicketContainer ticket = get_ticket( product );
        if (ticket != null)
        {
            if (ticket.isValid())
            {
                return ticket.getTicket().getUnits();
            }
        }
        return 0;
    }

    public int get_used_units(String product)
    {
        if (product.compareTo(LicenseTicket.PRODUCT_BASE) == 0)
        {
            synchronized (umap_mtx)
            {
                if (license_user_map != null)
                    return license_user_map.size();
            }
        }
        return 0;
    }

    // THIS CAN BE TIME CONSUMING, NOT TOO OFTEN; IS NOT CRITICAL
    public void do_idle()
    {
        long now = System.currentTimeMillis();

        synchronized (umap_mtx)
        {
            boolean done = false;
            while (!done)
            {
                done = true;
                Set<Entry<String, StatMailAddress>> set = license_user_map.entrySet();
                for (Entry<String, StatMailAddress> entry : set)
                {
                    if ((now - entry.getValue().getTimestamp()) > MAX_STAY_VALID_MS)
                    {
                        LogManager.msg_license( LogManager.LVL_INFO, Main.Txt("Removing_inactive_user_from_usermap") + ": " + entry.getValue().get_mail());
                        set.remove(entry);
                        done = false;
                        break;
                    }
                }
            }
        }
        update_license_map();
    }

    public boolean exists_ticket(String product)
    {
        // DO NOT RECREATE EVERY TIME, LICENSE FILE HAS TO BE MISSING
        File lic_path = get_lic_file( product );
        return lic_path.exists();
    }
    public int get_serial()
    {
        ValidTicketContainer ticket = get_ticket( LicenseTicket.PRODUCT_BASE );
        if (ticket != null)
        {
            if (ticket.isValid())
            {
                return ticket.getTicket().getSerial();
            }
        }
        return 0;
    }

    File get_lic_file( String product )
    {
        File lic_path = new File(Main.LICENSE_PATH + product + "_" + LICFILE_NAME);
        return lic_path;
    }
    File get_lic_file( LicenseTicket ticket )
    {
        return get_lic_file(ticket.getProduct());
    }

    public boolean delete_license(String product)
    {
        try
        {
            synchronized (lic_mtx)
            {
                File lic_path = get_lic_file(product);
                lic_path.delete();
                ValidTicketContainer vtck = get_ticket(product);
                if (vtck != null)
                {
                    ticket_list.remove(vtck);
                    return true;
                }
            }
        }
        catch (Exception e)
        {
        }
        return false;
    }

    private ValidTicketContainer read_license(String product)
    {
        File lic_path = get_lic_file(product);
        return read_license(lic_path);
    }
    private ValidTicketContainer read_license(File lic_path)
    {
        if (lic_path.exists())
        {
            FileInputStream fis = null;
            XStream xs = new XStream();
            try
            {
                fis = new FileInputStream(lic_path);
                Object o = xs.fromXML(fis);
                if (o instanceof LicenseTicket)
                {
                    LicenseTicket t = (LicenseTicket)o;
                    boolean valid = t.isValid();
                    ValidTicketContainer vtck = new ValidTicketContainer(t, valid);
                    if (valid)
                        LogManager.msg_license( LogManager.LVL_INFO, Main.Txt("Found_valid_license") + ": " + vtck.getTicket().toString());
                    else
                        LogManager.msg_license( LogManager.LVL_ERR,  vtck.getTicket().getLastErrMessage());
                    
                    return vtck;
                }
            }
            catch (Exception exc)
            {
                LogManager.msg_license( LogManager.LVL_WARN,  "Found invalid license ticket " + lic_path + ": " + exc);

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
            LogManager.msg_license( LogManager.LVL_WARN, Main.Txt("No License was found"));
        }
        return null;
    }


    public void write_ticket( LicenseTicket ticket )
    {
        synchronized(lic_mtx)
        {
        File lic_path = get_lic_file( ticket );
        
        FileOutputStream fos = null;
        XStream xs = new XStream();
        try
        {
            fos = new FileOutputStream(lic_path);
            xs.toXML(ticket, fos);
        }
        catch (Exception exception)
        {
            LogManager.msg_license( LogManager.LVL_ERR, Main.Txt("Cannot_write_license_ticket") + ": " + exception.getLocalizedMessage());
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
    }

    public void read_licenses()
    {
        synchronized(lic_mtx)
        {
            ticket_list.clear();

            File lic_dir = get_lic_file("1234").getParentFile();
            File[] lic_list = lic_dir.listFiles();
            for (int i = 0; i < lic_list.length; i++)
            {
                File file = lic_list[i];
                if (!file.getName().endsWith("_license.xml"))
                    continue;

                ValidTicketContainer vtck = read_license(file);
                if (vtck != null)
                    ticket_list.add(vtck);
            }
        }
    }
}
