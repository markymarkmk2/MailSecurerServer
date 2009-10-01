/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.index.SearchCall;
import dimm.home.mailarchiv.Utilities.ParseToken;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public class SearchCommand extends AbstractCommand
{
    public SearchCommand()
    {
        super("SearchMail");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String command = pt.GetString("CMD:");
        if (command.compareTo("open") == 0)
        {
            int ma_id = (int)pt.GetLongValue("MA:");
            String mail = pt.GetString("EM:");
            String field = pt.GetString("FL:");
            String val = pt.GetString("VL:");
            int n = (int)pt.GetLongValue("CNT:");


            answer = SearchCall.open_search_call( ma_id, mail, field, val, n);

            return true;
        }
        else if (command.compareTo("get") == 0)
        {
            String id = pt.GetString("ID:");
            int row = (int)pt.GetLongValue("ROW:");
            String field_list = pt.GetString("FLL:");
            String[] fields = field_list.split(",");
            ArrayList<String> field_arr = new ArrayList<String>();

            for (int i = 0; i < fields.length; i++)
            {
                String string = fields[i];
                field_arr.add(string);
            }

            answer = SearchCall.retrieve_search_call( id, field_arr, row );

            return true;
        }
        else if (command.compareTo("close") == 0)
        {
            String id = pt.GetString("ID:");

            answer = SearchCall.close_search_call(id);

            return true;
        }
        else if (command.compareTo("open_mail") == 0)
        {
            String id = pt.GetString("ID:");
            int row = (int)pt.GetLongValue("ROW:");

            answer = SearchCall.retrieve_mail(id, row);

            return true;
        }

        answer = "1: Unknown subcommand: " + data;
        return false;
    }
}
