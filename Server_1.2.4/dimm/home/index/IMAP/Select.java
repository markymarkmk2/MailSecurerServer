/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Select extends ImapCmd
{
    public Select()
    {
        super( "select");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        return select( is, sid, parameter );
    }

    static int select(ImapsInstance is,  String sid, String par )
    {
        if (is.get_konto() != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                MailFolder folder = is.get_konto().select(part[0]);
                
                is.set_selected_folder( folder );
                if (folder != null)
                {
                    folder.reset();
                    is.response("" + folder.anzMessages() + " EXISTS" );
                    is.response("" + folder.anzMessages() + " RECENT" );

                    is.response("FLAGS (\\Answered \\Flagged \\Deleted \\Draft \\Seen)");
                    is.response("OK [UIDVALIDITY " + folder.get_uid_validity() + "] UIDVALIDITY value" );
                    is.response("OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Draft \\Seen)] Permanent flags)");
                }
                is.response(sid, true, "[READ-ONLY] SELECT completed");
                return 0;
            }
        }
        is.response(sid, false, "SELECT failed");
        return 1;
    }

}
