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
        handle_messages_searched( is );
        return noop(is, sid, parameter);
    }

    static int noop( MWImapServer is, String sid, String parameter )
    {

        is.response(sid, true, "NOOP completed");
        return 0;
    }

    static void handle_messages_searched(MWImapServer is)
    {
        if (is.mailfolder != null && is.has_searched)
        {
            is.response(Integer.toString(is.mailfolder.anzMessages()) + " EXISTS");

            for (int m = 0; m < is.mailfolder.lastanzMessages(); m++)
            {
                MailInfo msginfo = is.mailfolder.get_last_mail_message(m);
                if (msginfo.getUID() == 42) // SKIP STATUSMAIL
                    continue;
                is.response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
            }
            for (int m = 0; m < is.mailfolder.anzMessages(); m++)
            {
                MailInfo msginfo = is.mailfolder.get_mail_message(m);
                if (msginfo.getUID() == 42) // SKIP STATUSMAIL
                    continue;
                is.response(Integer.toString(msginfo.getUID()) + " RECENT");
            }
            is.has_searched = false;
        }
    }

}
