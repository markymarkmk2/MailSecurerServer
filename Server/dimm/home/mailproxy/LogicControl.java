/*
 * LogicControl.java
 *
 * Created on 5. Oktober 2007, 18:42
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import dimm.home.mailproxy.Commands.Ping;
import dimm.home.mailproxy.Utilities.CmdExecutor;
import java.io.FileReader;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

/**
 *
 * @author Administrator
 */
public class LogicControl
{
    Communicator comm;
    StatusDisplay sd;
    MailArchiver ma;
    MailProxyServer ps;
    
    ArrayList<WorkerParent> worker_list;
    
    boolean _is_licensed;
    
    
    /** Creates a new instance of LogicControl */
    public LogicControl()
    {
        worker_list = new ArrayList<WorkerParent>();
        try
        {
            
            comm = new Communicator();
            worker_list.add( comm );
            
            sd = new StatusDisplay();
            worker_list.add( sd );
            
            ma = new MailArchiver();
            worker_list.add( ma );
            
            ps = new MailProxyServer();
            worker_list.add( ps );
                                                
        }
        catch (Exception ex)
        {
            Main.err_log_fatal("Constructor falied: " + ex.getMessage() );
        }
    }
    
    
    public boolean is_licensed()
    {
        return _is_licensed;
    }
    
    
    boolean check_licensed()
    {
        NetworkInterface ni;
        {
            FileReader fr = null;
            try
            {
                ni = NetworkInterface.getByName("eth0");
                byte[] mac = ni.getHardwareAddress();

                MessageDigest md5 = MessageDigest.getInstance("MD5");
                md5.reset();
                md5.update(mac);
                byte[] result = md5.digest();

                /* Ausgabe */
                StringBuffer hexString = new StringBuffer();
                for (int i=1; i<=result.length; i++) 
                {
                    hexString.append(Integer.toHexString(0xFF & result[ result.length - i]));
                }
          
                if (Main.create_licensefile)
                {
                    File licfile = new File("mailproxy.license");
                    FileWriter fw = new FileWriter( licfile );
                    fw.write( hexString.toString() );
                    fw.close();
                    Main.info_msg("License file was created");
                    return true;
                }
                else
                {
                    File licfile = new File("mailproxy.license");
                    fr = new FileReader(licfile);

                    char[] buff = new char[40];
                    int len = fr.read(buff);
                    

                    String lic_string = new String( buff, 0, len );


                    if (lic_string.equals( hexString.toString() ))
                        return true;                          

                    Main.err_log_fatal("Unlicensed host");
                }
            }
            catch (NoSuchAlgorithmException ex)
            {
                Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
            }            
            catch (FileNotFoundException ex2)
            {
                Main.err_log_fatal("Missing licensefile");
            }
            catch (SocketException ex3)
            {
                Main.err_log_fatal("No network interface for licensecheck");
            }
            catch (IOException ex1)
            {
                Main.err_log_fatal("Error while reading licensefile: " + ex1.getMessage());
            }            
            finally
            {
                try
                {
                    if (fr != null)
                        fr.close();
                }
                catch (IOException ex)
                {
                    
                }
            }
        }
        return false;
    }        

    void initialize()
    {
       
        // WAIT UNTIL WE REACH INET BEFORE CONTINUING
        if (comm != null)
        {
            comm.setStatusTxt( "Checking internet..." );
            for (int i = 0; i < 5; i++)
            {
                if (do_server_ping())
                    break;

                sleep(1000);
            }

            if (!do_server_ping())
            {
                comm.setStatusTxt( "Internet not reachable" );
                comm.setGoodState( false );
                Main.err_log_fatal("Cannot connect internet at startup" );        
               
            }  
            else
            {
               
                if (!comm.isGoodState())
                {
                    comm.setStatusTxt( "Internet reachable" );
                    comm.setGoodState( true );
                }
            }   
                
        }        
        
        _is_licensed = check_licensed();
        
        
        for (int i = 0; i < worker_list.size(); i++)
        {
            try
            {
                boolean ok = worker_list.get(i).initialize();
                if (!ok)
                {
                    Main.err_log_fatal("Initialize of " + worker_list.get(i).getName() + " failed" );
                }                    
            }
            catch (Exception ex)
            {
                // SHOULD NEVER BE RECHED
                Main.err_log_fatal("Initialize of " + worker_list.get(i).getName() + " failed : " + ex.getMessage() );
            }
        }                  
    }
    
    
    
    public static void sleep( int ms)
    {
        try
        {
            Thread.sleep( ms );
        } catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }
    
    
    
    void set_system_time()
    {
        try
        {
            // USE  --directisa WG: SHUTTLE, SHOULD NOT BOTHER OTHER BOX
            String[] ntp_server_list = { "ptbtime2.ptb.de" };
            
            for (int idx = 0; idx < ntp_server_list.length; idx++)
            {
                String rdate_cmd = Main.get_prop( Preferences.RDATE_COMMAND, "ntpdate " + ntp_server_list[idx] + " && hwclock --directisa -w" );
                String[] cmd = { rdate_cmd };
                CmdExecutor exec = new CmdExecutor( cmd );

                if (exec.exec() != 0)
                {
                    String err_txt = ""; 
                    if (exec.get_out_text() != null && exec.get_out_text().length() > 0)
                        err_txt += exec.get_out_text();
                    if (exec.get_err_text() != null && exec.get_err_text().length() > 0)
                        err_txt += exec.get_err_text();

                    Main.err_log_warn("System time cannot be retrieved: " + err_txt );     
                    
                    sleep(1000);
                }
                else
                {
                    Main.debug_msg(1, "Systemtime was synchronized" );
                    break;
                }
            }
        }
        catch (Exception exc) 
        {}
    }
        
    boolean do_server_ping()
    {
        boolean ok = false;
        try
        {
            Ping ping = new Ping();

            ping.do_command("");

            if (ping.get_answer().compareTo("INET_OK") == 0)
                ok = true;                
        }
        catch (Exception exc)
        {
        }
        if( sd != null)
            this.sd.set_router_ok(ok );
        
        if (comm != null)
        {
            
            if (!ok)
                comm.setStatusTxt( "Internet not reachable" );
            comm.setGoodState( ok );
        }
                        
        return ok;
    }
    
    
        
    
    // MAIN WORK LOOP
    void run()
    {
        long last_date_set = 0;
        long last_ping = 0;
        boolean last_start_written = false;
        long started = System.currentTimeMillis();
        
        final int MIN_VALID_RUN_TIME = 10000;  // AFTER THIS TIME WE WRITE OUR VALID TIMESTAMP
        
        try
        {  
            
            
            // SET SYSTEM TIME PRIOR STARTING PLAYLISTS
            set_system_time();
            last_date_set = System.currentTimeMillis();            
            
            
            for (int i = 0; i < worker_list.size(); i++)
            {
                if (!worker_list.get(i).start_run_loop())
                {
                    Main.err_log_fatal("Cannot start runloop for Worker " + worker_list.get(i).getName() );
                }
            }
            
            
            while (true)
            {
                sleep( 1000 );

                long now = System.currentTimeMillis();
                
                // ALLE 24h UHRZEIT SETZEN
                if ((now - last_date_set) > 24*60*60*1000)
                {
                   set_system_time();
                   last_date_set = now;
                }
                
                // ALLE 10 SEKUNDEN INET PINGENSETZEN
                if ((now - last_ping) > 10*1000)
                {
                   do_server_ping();
                   last_ping = now;
                }     
                
                if (!last_start_written)
                {
                    if ((now - started) > MIN_VALID_RUN_TIME)
                    {
                        try
                        {
                            File f = new File(Main.STARTED_OK);

                            FileWriter fw = new FileWriter(f);
                            fw.write("OK");
                            fw.close();
                        }
                        catch (Exception exc)
                        {}
                    }
                }
            }            
        } 
        catch (Exception ex)
        {
            
            ex.printStackTrace();
        }
                        
        
        Main.info_msg("Closing down " +  Main.APPNAME);
        System.exit(0);
    }
    
    public void set_shutdown( boolean b )
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            worker_list.get(i).setShutdown( b );
        }
    }
            
    
    static public Date get_actual_rel_date()
    {
        Date now = new Date(System.currentTimeMillis());        
        
        SimpleDateFormat full_date_sdf = new SimpleDateFormat("HH:mm:ss");
        String sd = full_date_sdf.format( now );
        try
        {            
            now = full_date_sdf.parse( sd );
        } 
        catch (Exception ex)
        {         
            System.out.println("Cannot retreive get_actual_rel_date " + ex.getMessage() );
        }
        
        return now;
    }

    
    public StatusDisplay get_status_display()
    {
        return sd;
    }
    public MailArchiver get_mail_archiver()
    {
        return ma;
    }
    public MailProxyServer get_proxy_server()
    {
        return ps;
    }
    
    WorkerParent get_worker(String name)
    {
        for (int i = 0; i < worker_list.size(); i++)
        {
            if (worker_list.get(i).getName().compareTo(name) == 0)
                return worker_list.get(i);
            
        }
        return null;
    }
    public ArrayList<WorkerParent> get_worker_list()
    {
         return worker_list;
    }

    public boolean check_requirements(StringBuffer sb)
    {
        boolean ok = true;
        sb.append("LogicControl check :\n");
        
        
        for (int i = 0; i < worker_list.size(); i++)
        {
            sb.append( worker_list.get(i).getName() + " check: " );
            if (!worker_list.get(i).check_requirements(sb))
            {
                ok = false;
            }
            sb.append( "\n" );
        }
                
        return ok;
    }
}
