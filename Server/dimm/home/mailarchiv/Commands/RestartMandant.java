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
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */


public class RestartMandant extends AbstractCommand
{

    
    /** Creates a new instance of HelloCommand */
    public RestartMandant()
    {
        super("restart_mandant");
        
    }
    

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        try
        {
            long m_id = pt.GetLongValue("MA:");

            // GET STRUCTS FROM ARGS
            Main.get_control().reinit_mandant((int)m_id);

            // YEEHAW, WE'RE DONE
            answer = "0: ok";
        }
        catch (Exception e)
        {
            e.printStackTrace();
            answer = "9: " + e.getMessage();
        }
                
        return true;
    }        
}
