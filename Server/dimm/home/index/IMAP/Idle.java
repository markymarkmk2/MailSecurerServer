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

        if (is.mailfolder != null && is.has_searched)
        {
            for (int m = 0; m < is.mailfolder.lastanzMessages(); m++)
            {
                MailInfo msginfo = is.mailfolder.get_last_mail_message(m);
                is.response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
            }
            for (int m = 0; m < is.mailfolder.anzMessages(); m++)
            {
                MailInfo msginfo = is.mailfolder.get_mail_message(m);
                is.response(Integer.toString(msginfo.getUID()) + " RECENT");
            }
            is.has_searched = false;
        }


        //Idle schleife
        long last = 0;
        int manz = 0;
        if (is.mailfolder != null)
        {
            manz = is.mailfolder.anzMessages();
        }
        while (true)
        {
            try
            {
                if (is.in.ready())
                {
                    String rline = is.in.readLine();
                    if (rline.toLowerCase().startsWith("done") || rline.toLowerCase().endsWith("close") || rline.toLowerCase().endsWith("logout"))
                    {
                        is.response(sid, true, "IDLE completed");
                        return 0;
                    }
                    if (!rline.toLowerCase().endsWith("noop"))
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
            if ((is.mailfolder != null) && manz != is.mailfolder.anzMessages())
            {
                is.response(is.mailfolder.anzMessages() + " EXISTS");
                is.response("0 RECENT");
            }
        }
    }
}
