/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.util.ArrayList;
import java.util.StringTokenizer;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */


public class ShellCmd extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public ShellCmd()
    {
        super("SHELLCMD");
    }

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );
        ParseToken pt = new ParseToken(opt);
        
        ArrayList<String> slist = new  ArrayList<String>();
        String command = pt.GetString("CMD:");
        StringTokenizer sto = new StringTokenizer( command, " \n\r\t" );
        while (sto.hasMoreTokens())
            slist.add( sto.nextToken() );
        
        
        try
        {
            String[] cmd = (String[])slist.toArray(new String[0]);

            CmdExecutor exe = new CmdExecutor( cmd );
            exe.set_timeout(15); // MAX 15 Secs
            exe.exec();

            answer = exe.get_err_text() + exe.get_out_text();

            return (exe.get_result() == 0);
        }
        catch (Exception exc)
        {
            answer = "command failed: " + exc.getMessage() ;
            return false;
        }            
    }    
}
