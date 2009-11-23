/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */


public class Uid extends ImapCmd
{
    public Uid()
    {
        super( "uid");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return uid( is, sid, parameter );
    }

     private int uid( MWImapServer is, String sid, String par )
    {
        if (is.konto == null || is.mailfolder == null)
        {
            is.response(sid, false, "UID failed");
            return 1;
        }
        String part[] = imapsplit(par);
        if (part == null || part.length == 0)
        {
            is.response(sid, false, "UID failed");
            return 1;
        }
        String command = part[0];
        String range = part[1];
        boolean success = true;
        while (!range.equals(""))
        {
            /* uid range could be 34:38,42:43,45 */
            String bereich = "";
            int i = range.indexOf(",");
            if (i < 0)
            {
                bereich = range;
                range = "";
            }
            else
            {
                bereich = range.substring(0, i);
                range = range.substring(i + 1);
            }
            int min = -1;
            int max = -1;
            i = bereich.indexOf(":");
            if (i < 0)
            {
                try
                {
                    min = max = Integer.parseInt(bereich);
                }
                catch (Exception e)
                {
                }
            }
            else
            {
                try
                {
                    min = Integer.parseInt(bereich.substring(0, i));
                }
                catch (Exception e)
                {
                }

                try
                {
                    max = Integer.parseInt(bereich.substring(i + 1));
                }
                catch (Exception e)
                {
                }

                if (min > 100000)
                {
                min = 0; //Mop: ob das wohl richtig ist
                }
                if (max == 0)
                {
                    max = -1;
                }
            }
            //debug
            //System.out.println("call "+command+" from:"+min+" to:"+max);

            try
            {
                if (command.toLowerCase().equals("search"))
                {
                    success &= search(is, min, max, 2, part);
                }
                else if (command.toLowerCase().equals("fetch"))
                {
                    success &= Fetch.fetch(is, min, max, 2, /*is_uid*/ true, part);
                }
                else
                {
                    //Unknown Command
                    success = false;
                    break;
                }
            }
            catch (Exception iOException)
            {
                iOException.printStackTrace();
                success = false;
                break;
            }
        }
        is.response(sid, success, "UID " + command.toUpperCase() + " " + (success ? "completed" : "failed"));
        return success ? 0 : 1;
    }

    boolean search( MWImapServer is, int min, int max, int offset, String part[] )
    {
        is.mailfolder.search( min, max, offset, part) ;

        String result = "SEARCH";
        for ( int i = 0; i < is.mailfolder.anzMessages(); i++)
        {
            MailInfo msginfo = is.mailfolder.get_mail_message(i);
            result += " " +  msginfo.getUID();
        }
        is.response( result );
        is.has_searched = true;
        return true;
    }

}
