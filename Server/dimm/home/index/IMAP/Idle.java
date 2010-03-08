/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.index.IMAP.MWImapServer;

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
        
        
        while (true)
        {
            try
            {
                Noop.handle_messages_searched( is );
                 
                if (is.in.ready())
                {
                    String rline = is.in.readLine();
                    if (is.trace)
                        System.out.println( "In: " + rline );

                    if (rline.toLowerCase().startsWith("done") || rline.toLowerCase().endsWith("close") || rline.toLowerCase().endsWith("logout"))
                    {
                        is.response(sid, true, "IDLE completed");
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
            }
            catch (Exception e)
            {
                is.konto.log(e);
                is.response(sid, false, "IDLE failed");
                return 1;
            }

            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                is.konto.log(e);
            }
        }
    }
}
