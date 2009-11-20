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
    public int action( MWImapServer is, String sid, String parameter )
    {
        return select( is, sid, parameter );
    }

    static int select(MWImapServer is,  String sid, String par )
    {
        if (is.konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                is.mailfolder = is.konto.select(part[0]);
                if (is.mailfolder != null)
                {
                    is.response("" + is.mailfolder.anzMessages() + " EXISTS" );
                    is.response("" + is.mailfolder.anzMessages() + " RECENT" );

                    is.response("FLAGS (\\Answered \\Flagged \\Deleted \\Draft \\Seen)");
                    is.response("OK [UIDVALIDITY " + is.mailfolder.get_uid_validity() + "] UIDVALIDITY value" );
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
