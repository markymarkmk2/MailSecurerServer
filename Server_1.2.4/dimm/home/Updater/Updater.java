/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.Updater;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import home.shared.Utilities.ZipListener;
import home.shared.Utilities.ZipStatusDlg;
import home.shared.Utilities.ZipUtilities;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 *
 * @author Administrator
 */
public class Updater 
{
    
    public static final String POST_PROC_FILE = "post_process_list.txt";
    public static final String PRE_PROC_FILE = "pre_process_list.txt";
    boolean _with_gui;
    String zip_file;
    ZipStatusDlg dlg;
    String targ_path;
    
    private boolean failed;
    private boolean finished;
    
    ZipUtilities zu;
    CmdExecutor script_exec;
    
    String[] script_final_ok = null;
    String[] script_final_nok = null;
    
    public Updater( String args[])
    {
        failed = false;
        finished = false;
        zu = new ZipUtilities();
        
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].compareTo("--gui") == 0)
                _with_gui = true;       
            if (args[i].compareTo("--path") == 0 && i + 1 < args.length)
            {
                i++;
                targ_path = args[i];
            }
        }
        // LAST ARG IS ZIP FILE
        if (args.length > 0)
        {
            zip_file = args[args.length - 1 ];
        }
        
        // LOCAL DIR IS FALLBACK
        if (targ_path == null)
        {
            targ_path = new File(".").getAbsolutePath();
            if (targ_path.endsWith("/.") || targ_path.endsWith("\\"))
                targ_path = targ_path.substring(0, targ_path.length()-2);
        }

     }
    
  

    public boolean do_update()
    {
        if (zip_file == null)
            return false;
                
        final Updater me = this;

        if (with_gui())
        {
            JFrame frm = new JFrame();
            
            dlg = new ZipStatusDlg( frm, zu );
            dlg.setModal(false);
            try
            {
                Runnable r = new Runnable()
                {

                    @Override
                    public void run()
                    {
                        dlg.setLocationRelativeTo(null);
                        dlg.setVisible(true);
                    }
                };

                SwingUtilities.invokeAndWait(r);
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (InvocationTargetException ex)
            {
                Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
        }               
        SwingWorker sw = new SwingWorker("Updater")
        {
            @Override
            public Object construct()
            {
                // GIVE GUI SOME TIME
                try
                {
                    if (with_gui())
                    {
                        while (dlg == null || !dlg.isVisible())
                            Thread.sleep(500);
                    }
                }
                catch (InterruptedException interruptedException)
                {
                }
                run_update( zu );
                finished = true;                
                return null;
            }
        };
        
        sw.start();
                
      
        return true;
    }

    boolean is_finished()
    {
        if (with_gui() && dlg != null)
        {                     
            return dlg.is_finished();
        }
        return finished;        
    }

   
    private void set_gui_new_state( int st, String sts )
    {
        if (with_gui() && dlg != null)
        {
            dlg.new_status(st, sts );
        }    
        else
            System.out.println(sts);
    }
    void convert_script_to_slashes( String script )
    {
        FileReader fr = null;
        FileWriter fw = null;
        try
        {
            fr = new FileReader(script);
            BufferedReader rd = new BufferedReader(fr);
            ArrayList<String> arr = new ArrayList<String>();
            while (true)
            {
                String line = rd.readLine();
                if (line == null)
                    break;

                arr.add(line);                    
            }
            
            rd.close();
            fr = null;
            
            new File( script).delete();
            fw = new FileWriter( script );
            BufferedWriter wr = new BufferedWriter( fw );
            for (int i = 0; i < arr.size(); i++)
            {
                String string = arr.get(i);
                wr.write(string);
                wr.newLine();
            }
            wr.flush();
            fw.close();
            fw = null;
        }
        catch (Exception ex)
        {
            System.out.println("convert_script_to_slashes failed: " + ex.getMessage() );
            ex.printStackTrace();
        }
        finally
        {
            try
            {
                if (fr != null)
                    fr.close();
                if (fw != null)
                    fw.close();
            }
            catch (IOException ex)
            {
            }
        }
    }    
    int call_script(String[] script_args)
    {
        String args = "";
        for (int i = 1; i < script_args.length; i++)
        {
            if (args.length() > 0)
                args += " ";
            args += script_args[i];
        }
        return call_script( script_args[0], args );        
    }
    
    int call_script(String script, String arg)
    {
        int ret = -1;
        if (System.getProperty("os.name").startsWith("Win"))
        {
            File sc = new File(script + ".bat");
            if (sc.exists())
                script = script + ".bat";
            String[] exec_cmd = { script, arg };
            script_exec = new CmdExecutor( exec_cmd );
            ret = script_exec.exec();
        }     
        else
        {
            File f = new File(script);
            if (!f.exists())
            {
                File f2 = new File(script + ".sh");
                if (f2.exists())
                {
                    script += ".sh";                            
                }
            }
                
            convert_script_to_slashes( script );
            String[] exec_cmd = { "sh", script, arg };
            script_exec = new CmdExecutor( exec_cmd );
            script_exec.set_use_no_shell(true);
            System.out.println("Calling script <sh " + script + " " + arg);
            
            ret = script_exec.exec();
            
        }
        
        return ret;
        
    }
    
    private boolean handle_processing_list_line(String line) throws Exception
    {
        StringTokenizer st = new StringTokenizer( line, "\t\r\n" );
        String cmd = st.nextToken();
        String arg1 = null;
        File src = null;
        
        if (st.hasMoreTokens())
        {
            arg1 = st.nextToken();
            src = new File(arg1);
            if (!src.isAbsolute())
            {
                src = new File(targ_path + "/"+ arg1);
            }
        }            
        
        String arg2 = null;
        File trg = null;
        if (st.hasMoreTokens())
        {
            arg2 = st.nextToken();
            trg = new File(arg2);
            if (!trg.isAbsolute())
            {
                trg = new File(targ_path + "/"+ arg2);
            }
        }
        
        if (cmd.compareTo("DESC") == 0)
        {   
            if (with_gui() && dlg != null)
            {
                if (arg1 != null)                
                    dlg.set_description(arg1);
            }
            else
            {
                if (arg1 != null)
                    Main.info_msg(line);
            }
          
            return true;
        }
        if (cmd.compareTo("DESCADD") == 0)
        {   
            if (with_gui() && dlg != null)
            {
                if (arg1 != null)                
                    dlg.add_description(arg1);
                else
                    dlg.add_description(null);
            }
            else
            {
                if (arg1 != null)
                    Main.info_msg(line);
            }
            return true;
        }
        
        
        if (cmd.compareTo("COPY") == 0)
        {            
            if (arg1 == null || arg2 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (!trg.getParentFile().exists())
                trg.getParentFile().mkdirs();
            
            set_gui_new_state(ZipListener.ST_BUSY, "Copying " + arg1 + " to " + arg2 + "..." );
                        
            
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src.getAbsolutePath()) );
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(trg.getAbsolutePath()) );
            zu.copyInputStream(bis, bos, src.length());
            trg.setLastModified(src.lastModified());
            bis.close();
            bos.close();
            return true;
            
        }
        else if (cmd.compareTo("DEL") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            set_gui_new_state(ZipListener.ST_BUSY, "Deleting " + src.getName() + "..." );
            
            
            src.delete();
            return true;
            
        }
        else if (cmd.compareTo("MOVE") == 0)
        {
            if (arg1 == null || arg2 == null)
                throw new Exception("Missing args for " + cmd );
            
            set_gui_new_state(ZipListener.ST_BUSY, "Moving " + src.getName() + " to " + trg.getName() + "..." );
            
            src.renameTo(trg);
            
            return true;
        
        }
        else if (cmd.compareTo("MKDIR") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (!src.exists())
                src.mkdirs();
            
            return true;
        }
        else if (cmd.compareTo("RMRECURSE") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (arg1.length() <= 1)
            {
                throw new Exception("Invalid arg " + arg1 + " for RMRECURSE" );
            }
                
            if (src.isFile())
                src.delete();
            else
                rm_recurse( src );
            
            return true;
        }   
        else if (cmd.compareTo("SCRIPT") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );

            // DEFAULT PARAM IS PATH TO SCRIPT 
            if (arg2 == null && src.getParentFile() != null)
                arg2 = src.getParentFile().getAbsolutePath();
            
            call_script(  src.getAbsolutePath(), arg2 );
                        
            return true;
        }   
        else if (cmd.compareTo("SCRIPTNOFAIL") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (arg2 == null)
                arg2 = "";
            
            int ret = call_script(  src.getAbsolutePath(), arg2 );
            if (ret != 0)
            {
                throw new Exception( "Script " + arg1 + " failed, aborting: " + 
                        script_exec.get_out_text() + " / " + script_exec.get_err_text() );
            }
                        
            return true;
        }   
        else if (cmd.compareTo("SCRIPTFINALOK") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (arg2 != null)
            {
                script_final_ok = new String[2];
                script_final_ok[0] = src.getAbsolutePath();
                script_final_ok[1] = arg2;
            }
            else
            {
                script_final_ok = new String[1];
                script_final_ok[0] = src.getAbsolutePath();
            }
                
                        
            return true;
        }   
        else if (cmd.compareTo("SCRIPTFINALNOK") == 0)
        {
            if (arg1 == null)
                throw new Exception("Missing args for " + cmd );
            
            if (arg2 != null)
            {
                script_final_nok = new String[2];
                script_final_nok[0] = src.getAbsolutePath();
                script_final_nok[1] = arg2;
            }
            else
            {
                script_final_nok = new String[1];
                script_final_nok[0] = src.getAbsolutePath();
            }
                        
            return true;
        }   
        
        return false;
    }

    private void rm_recurse(File dir)
    {
        File[] lst = dir.listFiles();
        for (int i = 0; i < lst.length; i++)
        {
            File file = lst[i];
            if (file.getName().compareTo(".") == 0)
                continue;
            if (file.getName().compareTo("..") == 0)
                continue;
            
            if (file.isFile())
            {
                set_gui_new_state(ZipListener.ST_BUSY, "Deleting " + file.getName() + "..." );

                file.delete();
            }
            else
            {
                rm_recurse( file );
            }
        }     
        dir.delete();
    }
    
    void call_failed_script()
    {
        CmdExecutor exec = new CmdExecutor( script_final_nok );
        int ret = exec.exec();
        if (ret != 0)
        {
            set_gui_new_state(ZipListener.ST_ERROR, "Final nok script failed: " +  
                    exec.get_out_text() + " / " + exec.get_err_text() );
        }
    }
    
    private void run_update(ZipUtilities zu)
    {
        failed = !run_pre_processing(targ_path);
        if (failed)
        {
            set_gui_new_state(ZipListener.ST_BUSY, "Preprocessing failed" );
            call_failed_script();
            return;
        }            
        
        zu.read_stat(  zip_file );
        failed = !zu.unzip(targ_path, zip_file);
        if (failed)
        {
            set_gui_new_state(ZipListener.ST_BUSY, "Unpacking failed" );
            call_failed_script();
            return;
        }            
            
        
        failed = !run_post_processing(targ_path);
        if (failed)
        {
            set_gui_new_state(ZipListener.ST_BUSY, "Postprocessing failed" );
            call_failed_script();
            return;
        }  
        if (!failed)
        {
            int ret = call_script(  script_final_ok);
            if (ret != 0)
            {
                set_gui_new_state(ZipListener.ST_ERROR, "Final ok script failed: " +  
                        script_exec.get_out_text() + " / " + script_exec.get_err_text() );
            }
            if (with_gui() && dlg != null)
            {
                dlg.call_auto_close();
            }
 
        }
        set_gui_new_state(ZipListener.ST_READY, "Finished" );
    }
    
    boolean run_pre_processing( String path )
    {
        // READ PREPROCESS FILE FIRST
        zu.unzip(targ_path, zip_file, PRE_PROC_FILE);
        
        File pre_proc_file = new File(path + "/" + PRE_PROC_FILE );
        
        if (pre_proc_file.exists())
        {
            set_gui_new_state( ZipListener.ST_BUSY, "Starting preprocessor..." );            
            return handle_processing_list(pre_proc_file);
        }
        return true;
    }
    boolean run_post_processing(String path )
    {
        File post_processing_list = new File(path + "/" + POST_PROC_FILE );
        
        if (post_processing_list.exists())
        {
            set_gui_new_state( ZipListener.ST_BUSY, "Starting postprocessor..." );            
            
            return handle_processing_list(post_processing_list);
        }

        return true;
    }
    
    // FORMAT ACTION\tSRC_FILE\tTRG_FILE
    boolean handle_processing_list( File post_processing_list )
    {
        String line = "";
        try
        {
            FileReader fr = new FileReader(post_processing_list);
            BufferedReader br = new BufferedReader(fr);

            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                if (line.charAt(0) == '#')
                {
                    continue;
                }
                if (line.startsWith("//"))
                {
                    continue;
                }

                boolean handled = handle_processing_list_line(line);
                if (!handled)
                {
                    throw new Exception("Unknown command" );
                }                    
            }
            br.close();
        }
        
        catch (Exception e)
        {
            set_gui_new_state( ZipListener.ST_ERROR, "Error while processing line " + line + ": " + e.getMessage() );            
                
            return false;
        }                
        return true;        
    }
    
    public static void build_sr_installer(String os_name, String app_name, String install_dir)
    {
        System.out.println("Invalid Installer Call");
       
    }
    public static void build_sb_installer(String os_name, String app_name, String install_dir)
    {
        FileWriter fw = null;
        try
        {
                     
            System.out.println("Building Installer for " + app_name + " " + os_name + " " + Main.get_version_str());
            
            File t_dir = new File(install_dir);
            if (!t_dir.exists())
            {
                t_dir.mkdirs();
            }
            String[] sc_args = {"copy_to_installer.bat"};
            CmdExecutor exec = new CmdExecutor(sc_args);
            exec.exec();



            fw = new FileWriter(install_dir + "/" + Updater.PRE_PROC_FILE);
            BufferedWriter bw = new BufferedWriter( fw );
            bw.write( "DESC\tThis is the installer for " + app_name + "\n");
            bw.write( "DESCADD\t\n");
            bw.write( "DESCADD\tPlease wait until all files are installed\n");
            bw.close();

            fw = new FileWriter( install_dir + "/" + Updater.POST_PROC_FILE);
            bw = new BufferedWriter( fw );
            bw.write( "SCRIPT\tcopy_to_final\n");
            bw.write( "DESC\tInstallation was completed\n");
            bw.write( "DESCADD\t\n");
            bw.write( "SCRIPTFINALOK\trestart_box\n");
            bw.close();

            File here = new File(".");

            String[] exclude_list = {install_dir};

            ZipUtilities zu = new ZipUtilities();
            zu.zip(install_dir, app_name + "_" + os_name + ".zip", exclude_list);

            fw = new FileWriter(app_name + "_" + os_name + "_ver.txt");
            bw = new BufferedWriter( fw );
            bw.write(Main.get_version_str());
            bw.close();

            return;
        }
        catch (IOException ex)
        {
            Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            try
            {
                fw.close();
            }
            catch (IOException ex)
            {
                Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
            }
        }        
    }
    
    /**
     @param args the command line arguments
     */
    public static void main(final String args[])
    {
        if (args.length == 0)
        {
            System.out.println("Missing args for Updater");
        }
        if (args.length > 0 && args[0].compareTo("--do_test") == 0)
        {
            String[] test_args = {"--gui", "--path", "./test2", "test.zip" };
            Updater upd = new Updater(test_args);
                
            //upd.setVisible(true);
            upd.do_update();
            
            while (!upd.is_finished())
            {
                try
                {
                    Thread.sleep(100);

                }
                catch (InterruptedException interruptedException)
                {
                }
            }
            System.exit( 0);
            return;
        }    
        if (args.length > 0 && args[0].compareTo("--create_test") == 0)
        {
            FileWriter fw = null;
            try
            {
                String test = "test";
                File t_dir = new File(test);
                if (!t_dir.exists())
                {
                    t_dir.mkdirs();
                }
                
                File t_file = new File("test/testfile.dat");
                if (!t_file.exists())
                {
                    t_file.createNewFile();
                    fw = new FileWriter(t_file);
                    BufferedWriter bw = new BufferedWriter( fw );
                    bw.write("Alloohaaaa\n");
                    bw.close();                    
                }

                fw = new FileWriter("test/" + Updater.PRE_PROC_FILE);
                BufferedWriter bw = new BufferedWriter( fw );
                bw.write( "DESC\tThis is the installer for Test\n");
                bw.write( "DESCADD\t\n");
                bw.write( "DESCADD\tPlease wait until all files are installed\n");
                bw.write( "DESCADD\t\n");
                bw.write( "DESCADD\tThank you, asshole\n");
                bw.close();
                
                fw = new FileWriter("test/" + Updater.POST_PROC_FILE);
                bw = new BufferedWriter( fw );
                bw.write( "COPY\ttestfile.dat\tj:\\tmp\\testfile.dat\n");
                bw.write( "COPY\ttestfile.dat\tM:\\tmp\\testfile.dat\n");
                bw.write("MOVE\tM:\\tmp\\testfile.dat\tM:\\tmp\\moved_testfile.dat\n");
                bw.write("DEL\ttestfile.dat\n");
                bw.write("MKDIR\tM:\\tmp\\upd_test\n");
                bw.write("COPY\tM:\\tmp\\moved_testfile.dat\tM:\\tmp\\upd_test\\testfile.dat\n");
                bw.write("MKDIR\tM:\\tmp\\upd_test2\n");
                bw.write("COPY\tM:\\tmp\\upd_test\\testfile.dat\tM:\\tmp\\upd_test2\\testfile.dat\n");
                bw.write("RMRECURSE\tM:\\tmp\\upd_test\n");
                bw.write( "DESC\tBye bye asshole, this is it\n");
                bw.write( "DESCADD\t\n");
                bw.write( "DESCADD\tThank you, dumbass\n");
                bw.write( "SCRIPTFINALOK\trestart_remote.bat\n");
                bw.close();

                File here = new File(".");

                String[] exclude_list = {test};

                ZipUtilities zu = new ZipUtilities();
                zu.zip(test, "test.zip", exclude_list);

                return;
            }
            catch (IOException ex)
            {
                Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
            }
            finally
            {
                try
                {
                    fw.close();
                }
                catch (IOException ex)
                {
                    Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return;

        }
        if (args.length > 1 && args[0].compareTo("--sr") == 0)
        {
            Updater.build_sr_installer(args[1], "mailsecurerclient", "MSCI");
            return;
        }
        if (args.length > 1 && args[0].compareTo("--sb") == 0)
        {
            Updater.build_sb_installer(args[1], "mailsecurerserver", "MSSI");
            return;
        }
        
        final Updater upd = new Updater(args);
        SwingWorker sw = new SwingWorker("Updater")
        {

            @Override
            public Object construct()
            {
                upd.do_update();
                return null;
            }
        };
        sw.start();
        
        while (!upd.is_finished())
        {
            try
            {
                Thread.sleep(100);

            }
            catch (InterruptedException interruptedException)
            {
            }
        }
        System.exit( 0);
        return;
                
    }
    
    
    // Variables declaration - do not modify                     
    // End of variables declaration                   

    private boolean with_gui()
    {
        return _with_gui;
    }
        

}
