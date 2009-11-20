/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Noop extends ImapCmd
{


    public Noop()
    {
        super("noop");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return noop(is, sid, parameter);
    }

    static int noop( MWImapServer is, String sid, String parameter )
    {
        if (is.has_searched)
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

        is.response(sid, true, "NOOP completed");
        return 0;
    }
}
