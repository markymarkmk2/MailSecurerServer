/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.Updater;

import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.FileTransferManager;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import home.shared.Utilities.ZipUtilities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;
import org.apache.commons.httpclient.methods.GetMethod;


/**
 *
 * @author media
 */


// WE COMPARE TO REAL_STATION_ID FIRST
// THEN TO VIRTUAL_STATION_ID 
// FINNALLY JUST TO REGULAR VERSION FILE
/*
sonicbox_lnx_S17_ver.txt";
sonicbox_lnx_S142_ver.txt";
sonicbox_lnx_ver.txt";

*/
// THE NAMES OF THE DL FILES ARE APPROPRIATE
/*
sonicbox_lnx_S17.zip";
sonicbox_lnx_S142.zip";
sonicbox_lnx.zip";
*/


public class UpdateWorker extends WorkerParent
{    
    String app_name;
    String os_name;
    String server_path;
    
    SwingWorker update_worker;
    
    long cycle_duration;
    boolean automatic;
    
    private static final int SHORT_DELAY = 60*1000; // STARTUP DELAY
    private static final int LONG_DELAY = 10*60*1000; // FOLLOWING DELAY
    private static final long VERYLONG_DELAY = 6*60*60*1000; // DELAY IF UPDATE FAILED 6 h LATER
            
    public static final String NAME = "UpdateWorker";
    
    String remote_ver_path = null;
    String remote_upd_path = null;    

    Main main;
    public UpdateWorker()
    {
        super(NAME);
                       
        main = Main.me;
        
        read_settings();
    }
    
    public void read_settings()
    {
        app_name = "mailsecurerserver";
        server_path = Main.SERVER_UPDATEWORKER_PATH + "MSS";

        automatic = Main.get_bool_prop(GeneralPreferences.AUTO_UPDATE, true);
        cycle_duration = SHORT_DELAY;
        
        os_name = "win";
        if (System.getProperty("os.name").startsWith("Linux"))
        {
            os_name = "lnx";
        } 
        if (System.getProperty("os.name").startsWith("Mac"))
        {
            os_name = "mac";
        }
        if (System.getProperty("os.name").startsWith("Sol"))
        {
            os_name = "sol";
        }
    }
    
    String get_remote_ver_path()
    {
        String ver_file = server_path + "/" + app_name + "_" + os_name + "_ver.txt";
        return ver_file;
    }
    String get_remote_ver_path_by_station(long station_id)
    {
        String ver_file = server_path + "/" + app_name + "_" + os_name + "_S" + station_id + "_ver.txt";
        return ver_file;
    }
    String get_remote_upd_path()
    {
        String upd_file = server_path + "/" + app_name + "_" + os_name + ".zip";
        return upd_file;
    }
    String get_remote_upd_path_by_station(long station_id)
    {
        String upd_file = server_path + "/" + app_name + "_" + os_name + "_S" + station_id + ".zip";
        return upd_file;
    }
    String get_local_upd_path()
    {
        String upd_file = Main.UPDATE_PATH + app_name + ".zip";
        return upd_file;
    }
    
        
    boolean eval_remote_filenames()
    {
        // RESET
        remote_ver_path = null;
        remote_upd_path = null;    
        
        String upd_server = Main.get_prop(GeneralPreferences.UPDATESERVER, Main.DEFAULTSERVER);
        String http_user = Main.get_prop(GeneralPreferences.HTTPUSER, Main.HTTPUSER);
        String http_pwd = Main.get_prop(GeneralPreferences.HTTPPWD, Main.HTTPPWD);
        FileTransferManager fman = new FileTransferManager( upd_server, http_user, http_pwd);
        
        // TRY UPDATE FILES FOR STATION-ID FIRST
        remote_ver_path = get_remote_ver_path_by_station(Main.get_station_id());
        if (fman.exists_file(remote_ver_path))
        {
            remote_upd_path = get_remote_upd_path_by_station( Main.get_station_id() );
        }
       
        
        // FALLBACK TO REGULAR STATION
        if (remote_upd_path == null)
        {
            remote_ver_path = get_remote_ver_path();
            if (fman.exists_file(remote_ver_path))
            {
                remote_upd_path = get_remote_upd_path();
            }            
        }
        if (remote_upd_path != null && remote_ver_path != null)
            return true;
        
        return false;
    }            
        
    String get_remote_ver()
    {
        String remote_ver = null; 
        
        String upd_server = Main.get_prop(GeneralPreferences.UPDATESERVER, Main.DEFAULTSERVER);
        String http_user = Main.get_prop(GeneralPreferences.HTTPUSER, Main.HTTPUSER);
        String http_pwd = Main.get_prop(GeneralPreferences.HTTPPWD, Main.HTTPPWD);
        FileTransferManager fman = new FileTransferManager( upd_server, http_user, http_pwd);
        
        try
        {
            GetMethod gm = fman.open_dl_stream( remote_ver_path );
            if (gm == null)
            {
                throw new Exception( "Remote version file is missing, no updates" );
            }
            
            InputStream is = gm.getResponseBodyAsStream();
            byte[] b = new byte[256];
            int len = is.read(b);
            is.close();
            remote_ver = new String(b, 0, len);

        }
        catch ( Exception exc )
        {
            Main.err_log("Error while reading SB remote version from file " + remote_ver_path + " : " + exc.getMessage());
        }
        return remote_ver;
    }
    
    boolean check_changed()
    {
        String local_ver = Main.get_version_str();
        
        String remote_ver = null;
        
        if (eval_remote_filenames())
        {
            remote_ver = get_remote_ver();
        }
        
        if (remote_ver != null)
        {
            int local_ver_code = get_ver_code( local_ver );
            int remote_ver_code = get_ver_code( remote_ver );
            
            // TRY DECODED VERSION CODE
            if (local_ver_code != 0 && remote_ver_code != 0)
            {                
                if (remote_ver_code > local_ver_code)
                {
                    Main.info_msg("Detected new Version " + remote_ver);
                    return true;
                }
                return false;
            }
            // STRINGCOMPARE AS FALLBACK
            boolean ret = remote_ver.compareTo(local_ver) > 0 ? true: false;            
            if (ret)
            {
                Main.info_msg("Detected new Version " + remote_ver);
                return true;
            }
        }

        // ERROR WHILE CHECKING REMOTE VERSION -> DO NOTHING!
        return false;        
    }
    public static int get_ver_code(String ver)
    {
        try
        {
            StringTokenizer st = new StringTokenizer(ver, ".\n\r \t");
            int v_high = Integer.parseInt(st.nextToken());
            int v_mid = Integer.parseInt(st.nextToken());
            int v_low = Integer.parseInt(st.nextToken());
            return v_high * 1000 * 1000 + v_mid * 1000 + v_low;

        }
        catch (NumberFormatException numberFormatException)
        {
            Main.err_log_fatal("Invalid version String " + ver + ": " + numberFormatException.getMessage());
        }
        return 0;
    }
        

    public void check_updates_wait()
    {
        check_updates();
        
        while (update_worker != null && !update_worker.finished())
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex)
            {
            }
        }

    }

    public void check_updates()
    {
        if (update_worker != null && !update_worker.finished())
            return;
        
        
        update_worker = new SwingWorker(NAME + ".CheckUpdates")
        {


            @Override
            public Object construct()
            {
                if (automatic && check_changed())
                {
                    
                    try
                    {
                        download_update();
                    
                        handle_update();
                    }
                    catch (Exception exc)
                    {
                        Main.err_log(exc.getMessage());
                    }
                }
                
                return null;
            }
        };

        
        update_worker.start();                 
    }
 
    private void download_update() throws IOException
    {
        String upd_server = Main.get_prop(GeneralPreferences.UPDATESERVER, Main.DEFAULTSERVER);
        String http_user = Main.get_prop(GeneralPreferences.HTTPUSER, Main.HTTPUSER);
        String http_pwd = Main.get_prop(GeneralPreferences.HTTPPWD, Main.HTTPPWD);
        FileTransferManager ftm = new FileTransferManager( upd_server, http_user, http_pwd);
        ftm.enable_busy(false, null);
        
        
        String src = remote_upd_path;
        String trg = get_local_upd_path();
        File trg_file = new File( trg );
        File parent = trg_file.getParentFile();
        if (!parent.exists())
            parent.mkdirs();
        
        if (!ftm.download_file( src, trg, false))
            throw new IOException( "The download of the update failed");
            
    }

    private void handle_update()
    {
        FileWriter fw = null;
        try
        {
            File here = new File(".");
            String[] exclude_list = {"update", "logs", "database", "db", "temp" };
            ZipUtilities zu = new ZipUtilities();
            Main.debug_msg(1, "Saving actual installation...");
            
            zu.zip(here.getAbsolutePath(), Main.UPDATE_PATH + "last_valid_version.zip", exclude_list);
            
            CmdExecutor exec;
            
            if (System.getProperty("os.name").startsWith("Win"))
            {
                File bootstrap = new File("bootstrap.bat");
                fw = new FileWriter(bootstrap);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write("start javaw -cp dist\\MailArchiv.jar dimm.home.Updater.Updater --path " + here.getAbsolutePath() + "\\MSS " + get_local_upd_path() );
                bw.close();
                
                String[] update_cmd = {"bootstrap.bat"};
                exec = new CmdExecutor(update_cmd);                
            }
            else if (System.getProperty("os.name").startsWith("Mac"))
            {
                File bootstrap = new File("bootstrap.sh");
                fw = new FileWriter(bootstrap);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write("#!/bin/sh");
                bw.newLine();
                bw.write("cp MailArchiv.jar update");  // COPY IF UPDATE FAILS
                bw.newLine();

                bw.write("/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Commands/java -cp MailArchiv.jar dimm.home.Updater.Updater --path " + here.getAbsolutePath() + "/MSS " + get_local_upd_path() + " &" );
                bw.close();


                String[] update_cmd = {"sh", "./bootstrap.sh"};
                exec = new CmdExecutor(update_cmd);
                exec.set_use_no_shell(true);

            }
            else
            {
                File bootstrap = new File("bootstrap.sh");
                fw = new FileWriter(bootstrap);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write("#!/bin/sh");
                bw.newLine();
                bw.write("cp MailArchiv.jar update");  // COPY IF UPDATE FAILS
                bw.newLine();
                
                bw.write("java -cp MailArchiv.jar dimm.home.Updater.Updater --path " + here.getAbsolutePath() + "/MSS " + get_local_upd_path() + " &" );
                bw.close();
                
                
                String[] update_cmd = {"sh", "./bootstrap.sh"};
                exec = new CmdExecutor(update_cmd); 
                exec.set_use_no_shell(true);
            }
            
            exec.start();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            Main.err_log_fatal("Error while calling updater: " +  ex.getMessage());
        }
        finally
        {
            try
            {
                fw.close();
            }
            catch (IOException ex)
            {
            }
        }        
    }
    
    void run_loop()
    {
        long last_time_checked = System.currentTimeMillis();
        
        while (true)
        {
            long now = System.currentTimeMillis();
            if (now > last_time_checked + cycle_duration)
            {
                last_time_checked = now;
                
                cycle_duration = LONG_DELAY;
                
                if (automatic && check_changed())
                {                    
                    try
                    {
                        download_update();                    
                        handle_update();
                    }
                    catch (Exception exc)
                    {
                        Main.err_log(exc.getMessage());
                    }
                    
                    LogicControl.sleep(20000);
                    
                    // IWE GET HERE, WE ARE LOST, BECAUSE UPDATE SHOULD OF KICKES US AWAY
                    Main.err_log("Update failed, we did not restart");
                    cycle_duration = VERYLONG_DELAY;
                }                                
            }                        
            LogicControl.sleep(1000);
        }        
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    @Override
    public boolean start_run_loop()
    {
        SwingWorker worker = new SwingWorker(NAME)
        {
            @Override
            public Object construct()
            {
                run_loop();
                return null;
            }
        };

        worker.start();
        
        return true;        
    }

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    @Override
    public String get_task_status()
    {
        return "Not supported yet.";
    }
}
