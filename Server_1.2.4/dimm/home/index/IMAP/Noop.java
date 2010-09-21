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
    public int action( ImapsInstance is, String sid, String parameter )
    {
        handle_messages_searched( is );
        return noop(is, sid, parameter);
    }

    static int noop( ImapsInstance is, String sid, String parameter )
    {

        is.response(sid, true, "NOOP completed");
        return 0;
    }

    static void handle_messages_searched(ImapsInstance is)
    {
        MailFolder folder = is.get_selected_folder();

        if (folder != null && folder.key.equals(MailFolder.QRYTOKEN) && is.has_searched())
        {

            // WE EXPUNGE MESSAGE SEQUENCE NUMBERS (1... COUNT_OF_MSG_IN_FOLDER)
            for (int m = 0; m < folder.lastanzMessages(); m++)
            {
                is.response("1 EXPUNGE");
            }

            for (int m = 0; m < folder.anzMessages(); m++)
            {
                is.response(Integer.toString(m+1) + " RECENT");
            }
            is.response(Integer.toString(folder.anzMessages()) + " EXISTS");
            is.set_has_searched( false );
        }
    }
}
