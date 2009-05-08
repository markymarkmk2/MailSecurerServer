package dimm.home.Utilities;
/*
 * Executor.java
 *
 * Created on 22. Oktober 2002, 11:20
 */

import dimm.home.mailarchiv.Main;
import java.io.*;
import java.util.ArrayList;

/**
 *
 * @author  Administrator
 * @version
 */
public class CmdExecutor
{
    
    String[] cmd;
    
    String out_text;
    
    String err_text;
    
    int ret_code = 0;
    String out_line = "";
    String err_line = "";
    ArrayList<String> out_array = new ArrayList<String>();
    ArrayList<String> err_array = new ArrayList<String>();
    
    BufferedReader stdout;
    BufferedReader stderr;
    Process p;
    SwingWorker worker;
    
    boolean no_shell;
    boolean no_debug;
    boolean no_read_line;
    
    int timeout = 0;
    long start_time = 0;

    public Object[] get_out_array()
    {
        Object[] oa = out_array.toArray();
        return oa;
    }
    public Object[] get_err_array()
    {
        Object[] oa = err_array.toArray();
        return oa;
    }
    public void set_no_read_line( boolean f )
    {
        no_read_line = f;
    }
        
    public int get_out_array_len()  {   return out_array.size(); }
    public int get_err_array_len()  {   return err_array.size(); }
    
    int read_output()
    {
        char[] ch = null;
        if (no_read_line)
            ch = new char[256];
        
        int ret = 0;
        out_line = "";
        err_line = "";

        boolean found_data = false;
        
        for (int i = 0; i < 500; i++)
        {
            found_data = false;
            
            // CHEC KFOR VALID DATA IN STREAMS
            try
            {
                if (stdout != null && stdout.ready())
                {
                    found_data = true;
                    
                    if (no_read_line)
                    {
                        stdout.read(ch, 0, 256 );
                        out_line = new String( ch );
                    }
                    else
                    {
                        out_line = stdout.readLine();
                    }
                    ret = 1;
                }
                else if (stderr != null && stderr.ready())
                {
                    found_data = true;
                    if (no_read_line)
                    {
                        stderr.read(ch, 0, 256 );
                        err_line = new String( ch );
                    }
                    else
                    {
                        err_line = stderr.readLine();
                    }
                    ret = 2;
                }
            }
            catch (Exception e )
            {
                System.out.println(e.toString());
            }

            if (found_data && out_line != null && out_line.length() > 0)
            {
                out_array.add(out_line);
               // System.out.println("StdOut: " + out_line);
            }

            if (found_data && err_line != null && err_line.length() > 0)
            {
                err_array.add(err_line);
               // System.out.println("StdErr: " + err_line);
            }
            if (!found_data)
                break;
        }
        
        return ret;
    }
    
    /** Creates new Executor */
    public CmdExecutor(String[] _cmd)
    {
        no_shell = false;
        no_debug = false;
        cmd = _cmd;
    }
    public void set_use_no_shell(boolean s)
    {
        no_shell = s;
    }
    public void set_no_debug(boolean s)
    {
        no_debug = s;
    }
    public void set_timeout(int s)
    {
        timeout = s;
    }
    
    public void start()
    {
        out_line = "";
        err_line = "";
        
        worker = new SwingWorker()
        {
            public Object construct()
            {
                int ret = -1;
                try
                {
                    if (!no_shell && !System.getProperty("os.name").startsWith("Windows"))
                    {
                        String command = cmd[0];
                        
                        boolean is_local = false;
                        File f = new File(cmd[0]);
                        //System.out.print("Checking file " + f.getAbsolutePath() + " ..." );
                        if (f.exists())
                        {
                            //System.out.println("exists" );
                            if (cmd[0].charAt(0) != '/')
                                command = "./" + cmd[0];
                            else
                                command = cmd[0];
                        }
                        else
                        {
                            //System.out.println("does not exist" );
                            f = new File("/usr/local/bin/" + cmd[0]);
                            //System.out.print("Checking file " + f.getAbsolutePath() + " ..." );
                            if (f.exists())
                            {
                                //System.out.println("exists" );
                                command = "/usr/local/bin/" + cmd[0];
                            }
                            //else
                                //System.out.println("does not exist" );
                        }
                            
                        
                        //System.out.println("calling " + command );
                        
                        String[] _cmd = new String[3];
                        _cmd[0] = "sh";
                        _cmd[1] = "-c";
                        //_cmd[2] = "\"" + command;
                        _cmd[2] = command;
                        for (int i = 1; i < cmd.length; i++)
                        {
                            _cmd[2] = _cmd[2] + " " + cmd[i];
                        }
                        //_cmd[2] = _cmd[2] + "\"";
                        cmd = _cmd;
                    }
                    
                    if (!no_debug)
                    {
                        StringBuffer sb = new StringBuffer();
                        sb.append("Exec: " );
                                
                        for (int i = 0; i < cmd.length; i++)
                        {
                            sb.append(cmd[i] + " ");
                        }                    
                        
                        Main.debug_msg( 1, sb.toString() );
                    }
                    
                    start_time = System.currentTimeMillis();
                    
                    p = Runtime.getRuntime().exec(cmd);
                    
                    stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    
                    // CLOSE STDIN
                    p.getOutputStream().close();
                    
                    p.waitFor();
                    
                    ret = p.exitValue();
                }
                catch (Exception err)
                {
                    Main.err_log_fatal("Cannot execute command " + cmd[0] + ": " + err.getMessage() );
                    ret = -2;
                }
                Integer iret = new Integer(ret);
                return iret;
            }
        };
        
        worker.start();
    }
    
    public boolean check_ready()
    {
        return check_ready(300);
    }
    
    
    public boolean check_ready(int ms)
    {
        if ( p != null && timeout > 0)
        {
            long now = System.currentTimeMillis();
            if ( (now - start_time) / 1000 > timeout)
                p.destroy();
        }
        
        if ( worker.isAlive() )
        {
            while(ms > 0)
            {
                
                read_output();
            
                worker.join(10);
                if (worker.finished())
                    break;
                
                ms -= 10;
            }
           
            return false;
        }

        // THIS IS CALLED IF THE WORKER HAS FINISHED
        while(read_output() != 0)
        {
            try
            {
                Thread.sleep(10);
            } catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
        }
        
        try
        {
            stdout.close();
            stderr.close();
        }
        catch (Exception e )
        {
            System.out.println(e.toString());
        }
        stdout = null;
        stderr = null;
        
        return true;
    }
    public int get_result()
    {
        int ret = -1;
        Integer iret = (Integer)worker.get();
        if (iret != null)
        {
            ret = iret.intValue();
        }
        return ret;
    }
    
    public int exec()
    {
        int ret = -1;
        
        start();
                    
        while ( !check_ready() )
        {
            ; // DO NOTHING, JUST WAIT. CHECK READY CONSUMES 100 MS
        }
        
        ret = get_result();
        
        return ret;
    }

    public String get_out_text()
    {
        StringBuffer sb = new StringBuffer();
        
        Object[] o_array = get_out_array();
        for (int i = 0; i < o_array.length; i++)
        {
            sb.append(o_array[i].toString());
            sb.append("\n");
        }
        return sb.toString();
    }
    public String get_err_text()
    {
        StringBuffer sb = new StringBuffer();
        
        Object[] o_array = get_err_array();
        for (int i = 0; i < o_array.length; i++)
        {
            sb.append(o_array[i].toString());
            sb.append("\n");
        }
        return sb.toString();
    }
    
    
    public static void main(String argv[])
    {
//        String[] cmd = {"j:\\temp\\cdrecord.exe","-scanbus"};
        String[] cmd = {"j:\\temp\\cdda2wav.exe","-J","-L 1","-g", "-D 1,1,0","-v129" };
//        {"cmd", "/c" ,"j:\\temp\\cdda2wav -J -L 0 -g -D 1,1,0"};
//        {"cmd", "/c", "dir"};
        CmdExecutor e = new CmdExecutor(cmd);
        
        int ret = e.exec();

        
        System.out.println("Finished with error " + new Integer(ret).toString());
            
        System.out.println("StdOut: ");
        String err = e.get_err_text();
        if (err.length() > 0)
            System.out.println(err);

        System.out.println("StdErr: ");
        String out = e.get_out_text();
        if (out.length() > 0)
            System.out.println(out);        
    }
}




