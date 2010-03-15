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
        MailFolder folder = is.get_selected_folder();

        if (folder != null && folder.key.equals(MailFolder.QRYTOKEN) && is.has_searched())
        {

            MailInfo msginfo = null;
            /*if (folder.lastanzMessages() > 0)
            {
                msginfo = folder.get_last_mail_message(folder.lastanzMessages() - 1);
                is.response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
            }*/
            for (int m = 0; m < folder.lastanzMessages(); m++)
            {
                msginfo = folder.get_last_mail_message(0);
                /*if (msginfo.getUID() == 42) // SKIP STATUSMAIL
                    continue;*/
                is.response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
            }
            is.response( "0 EXISTS");

            for (int m = 0; m < folder.anzMessages(); m++)
            {
                msginfo = folder.get_mail_message(m);
               /* if (msginfo.getUID() == 42) // SKIP STATUSMAIL
                    continue;*/
                is.response(Integer.toString(msginfo.getUID()) + " RECENT");
            }
            is.response(Integer.toString(folder.anzMessages()) + " EXISTS");
            is.set_has_searched( false );
        }
    }

}
