/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 *
 * @author mw
 */
public class Idle extends ImapCmd
{

 
    public Idle()
    {
        super("idle");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return idle(is, sid, parameter);
    }

    int idle( MWImapServer is, String sid, String parameter )
    {
        if (is.konto == null)
        {
            is.response(sid, false, "IDLE failed");
            return 1;
        }

        is.write("+ Waiting for done");

        // HANDLE BACKGROUND SEARCH
        Noop.handle_messages_searched( is );
        

        //Idle schleife

        int to = 0;
        try
        {
            to = is.s.getSoTimeout();
            is.s.setSoTimeout(1000);
        }
        catch (SocketException socketException)
        {
        }
        
        while (true)
        {
            try
            {
                Noop.handle_messages_searched( is );

                if (is.s.isInputShutdown() || is.s.isOutputShutdown() || !is.s.isConnected())
                {
                    return 1;
                }
                

               

                try
                {
                    String rline = is.in.readLine();
                    if (rline == null)
                    {
                        throw new Exception("read_line empty");
                    }
                    if (is.trace)
                        System.out.println( "In: " + rline );

                    if (rline.toLowerCase().startsWith("done") || rline.toLowerCase().endsWith("close") || rline.toLowerCase().endsWith("logout"))
                    {
                        is.response(sid, true, "IDLE completed");
                        is.s.setSoTimeout( to );
                        return 0;
                    }

                    if (rline.toLowerCase().endsWith("noop") )
                    {
                        // HANDLE BACKGROUND SEARCH
                        Noop.handle_messages_searched( is );
                    }
                    else if (rline.toLowerCase().endsWith("idle"))
                    {
                        is.write("+ Waiting for done");
                    }
                    else
                    {
                        throw new Exception(rline);
                    }
                }
                catch ( SocketTimeoutException texc)
                {
                }
                LogicControl.sleep(100);
            }
            catch (Exception e)
            {
                is.konto.log(e);
                is.response(sid, false, "IDLE failed");
                try
                {
                    is.s.setSoTimeout(to);
                }
                catch (SocketException socketException)
                {
                }
                return 1;
            }
        }
    }
}
