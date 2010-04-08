/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class StartVPN extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public StartVPN()
    {
        super("STARTVPN");
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        ParseToken pt = new ParseToken(opt);
        
        String cmd = pt.GetString("CMD:");

        if (cmd.equals("GET"))
        {
            String cmd_str[];
            cmd_str = new String[]{"ps", "ax | grep openvpn | grep -v grep" };
            
            CmdExecutor exe = new CmdExecutor( cmd_str );
            if (exe.exec() == 0)
                answer = "VPN:1";
            else
                answer = "VPN:0";
        }
        
        if (cmd.equals("STOP"))
        {
            String cmd_str[];
            cmd_str = new String[]{Main.SCRIPT_PATH + "stop_vpn" };
            
            CmdExecutor exe = new CmdExecutor( cmd_str );
            
            int ret = exe.exec();
            if (ret != 0)
            {
                Main.err_log("Start VPN gave " + exe.get_out_text() + " " + exe.get_err_text() );
            }
            answer = "VPN:" + ret;
        }
        
        if (cmd.equals("START"))
        {
            String cmd_str[];
            cmd_str = new String[]{Main.SCRIPT_PATH + "start_vpn" };        
            
            CmdExecutor exe = new CmdExecutor( cmd_str );
            
            int ret = exe.exec();
            if (ret != 0)
            {
                Main.err_log("Start VPN gave " + exe.get_out_text() + " " + exe.get_err_text() );
            }
            
            answer = "VPN:" + ret;
        }
            
                
        
        return true;
    }
    
}
