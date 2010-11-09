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
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
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


// WE COMPARE TO SERIAL FIRST
// FINNALLY JUST TO REGULAR VERSION FILE
/*
mailsecurer_lnx_S123456_ver.txt";
mailsecurer_lnx_ver.txt";

*/
// THE NAMES OF THE DL FILES ARE APPROPRIATE
/*
mailsecurer_lnx_S123456.zip";
mailsecurer_lnx.zip";
*/


public class UpdateWorker extends WorkerParent
{    
    String app_name;
    String os_name;
    String server_path;
    
    BackgroundWorker update_worker;
    
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
    String get_remote_ver_path_by_serial(int  serial)
    {
        String ver_file = server_path + "/" + app_name + "_" + os_name + "_S" + serial + "_ver.txt";
        return ver_file;
    }
    String get_remote_upd_path()
    {
        String upd_file = server_path + "/" + app_name + "_" + os_name + ".zip";
        return upd_file;
    }
    String get_remote_upd_path_by_serial(long serial)
    {
        String upd_file = server_path + "/" + app_name + "_" + os_name + "_S" + serial + ".zip";
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
        
        // TRY UPDATE FILES FOR SERIAL FIRST
        int serial = Main.get_control().get_license_checker().get_serial();
        remote_ver_path = get_remote_ver_path_by_serial( serial );
        if (fman.exists_file(remote_ver_path))
        {
            remote_upd_path = get_remote_upd_path_by_serial( serial );
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
            LogManager.msg_system( LogManager.LVL_ERR, "Error while reading remote version from file " + remote_ver_path + " : " + exc.getMessage());
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
                    LogManager.msg_system( LogManager.LVL_INFO, "Detected new Version " + remote_ver);
                    return true;
                }
                return false;
            }
            // STRINGCOMPARE AS FALLBACK
            boolean ret = remote_ver.compareTo(local_ver) > 0 ? true: false;            
            if (ret)
            {
                LogManager.msg_system( LogManager.LVL_INFO, "Detected new Version " + remote_ver);
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
            LogManager.msg_system( LogManager.LVL_ERR, "Invalid version String " + ver + ": " + numberFormatException.getMessage());
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
        
        
        update_worker = new BackgroundWorker(NAME + ".CheckUpdates")
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
                        LogManager.msg_system( LogManager.LVL_ERR, exc.getMessage());
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
            LogManager.msg_system( LogManager.LVL_INFO,  "Saving actual installation...");
            
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
            LogManager.msg_system( LogManager.LVL_ERR, "Error while calling updater: " +  ex.getMessage());
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
        
        while (!Main.get_control().is_shutdown())
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
                        LogManager.msg_system( LogManager.LVL_ERR, exc.getMessage());
                    }
                    
                    LogicControl.sleep(20000);
                    
                    // IWE GET HERE, WE ARE LOST, BECAUSE UPDATE SHOULD OF KICKES US AWAY
                    LogManager.msg_system( LogManager.LVL_ERR, "Update failed, we did not restart");
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
        BackgroundWorker worker = new BackgroundWorker(NAME)
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

    @Override
    public String get_task_status( int ma_id )
    {
        return "Not supported yet.";
    }
}
