/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;


/**
 *
 * @author mw
 */

class IRange
{
    int min;
    int max;

    public IRange()
    {
        this.min = -1;
        this.max = -1;
    }
    void parse(String bereich)
    {

        min = -1;
        max = -1;
        int i = bereich.indexOf(":");
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
    }

    public int getMin()
    {
        return min;
    }

    public int getMax()
    {
        return max;
    }
    public static ArrayList<IRange> build_range_list( String range )
    {
        ArrayList<IRange> range_list = new ArrayList<IRange>();

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
            IRange r = new IRange();
            r.parse(bereich);
            range_list.add(r);
        }
        return range_list;
    }
}

public class Uid extends ImapCmd
{
    public Uid()
    {
        super( "uid");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter ) throws IOException
    {
        return uid( is, sid, parameter );
    }

     private int uid( MWImapServer is, String sid, String par ) throws IOException
    {

         resetCounter();
        if (is.get_konto() == null)
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
        ArrayList<IRange> range_list = IRange.build_range_list( range );


        for (int i = 0; i < range_list.size(); i++)
        {
            IRange iRange = range_list.get(i);

            //debug
            //System.out.println("call "+command+" from:"+min+" to:"+max);

            try
            {
                if (command.toLowerCase().equals("search"))
                {
                    success &= search(is, iRange.getMin(), iRange.getMax(), 2, part);
                }
                else if (command.toLowerCase().equals("fetch"))
                {
                    success &= Fetch.fetch(this, is, iRange.getMin(), iRange.getMax(), 2, /*is_uid*/ true, part);
                }
                else
                {
                    //Unknown Command
                    success = false;
                    break;
                }
            }
            catch (SocketException e)
            {
                throw new IOException( e.getMessage());
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
        String result = "SEARCH";
        MailFolder folder = is.get_selected_folder();
        int messages = folder.anzMessages();

        if (part.length >= 3 && part[1].toLowerCase().equals("uid"))
        {
            ArrayList<IRange> range_list = IRange.build_range_list( part[2] );
            for ( int i = 0; i < messages; i++)
            {
                MailInfo msginfo = folder.get_mail_message(i);
                int uid = msginfo.getUID();

                boolean is_in_range = false;

                for (int j = 0; j < range_list.size(); j++)
                {
                    IRange iRange = range_list.get(j);
                    if (iRange.getMax() != -1 &&  uid <= iRange.getMax())
                    {
                        is_in_range = true;
                        break;
                    }
                    if (iRange.getMax() == -1 && uid > iRange.getMin())
                    {
                        is_in_range = true;
                        break;
                    }
                }
                if (is_in_range)
                    result += " " +  msginfo.getUID();
            }

            // NOTHING FOUND?
            if (result.indexOf(' ') == -1 && messages > 0)
            {
                // RFC WANTS AT LEAST ONE
                result += " " +  folder.get_mail_message(messages - 1).getUID();
            }

            is.response( result );
            return true;
        }

        folder.search( min, max, offset, part) ;

        
        for ( int i = 0; i < messages; i++)
        {
            MailInfo msginfo = folder.get_mail_message(i);
            result += " " +  msginfo.getUID();
        }
        is.response( result );
        is.set_has_searched( true );
        return true;
    }

}
