/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class Ping extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public Ping()
    {
        super("PING");
    }

    @Override
    public boolean do_command(String data)
    {
        answer = "";
        
        String server = Main.get_prop(GeneralPreferences.SERVER, Main.DEFAULTSERVER);
        
        if (data != null && data.length() > 0)
        {
            String opt = get_opts( data );

            ParseToken pt = new ParseToken(opt);

            String ip = pt.GetString("IP:");
            if (ip != null && ip.length() > 0)
            {
                server = ip;
            }
        }

        String cmd3[]  = new String[]{"ping", "-c", "1", server };

        // TRY TO GET A PING
        if (System.getProperty("os.name").startsWith("Windows"))
            cmd3[1]  = "-n";
        
        CmdExecutor exe = new CmdExecutor( cmd3 );
        exe.set_no_debug( true );
        int retcode = exe.exec();
        answer = (retcode == 0) ? "INET_OK": "INET_NOK";
        
        return true;
    }
}
