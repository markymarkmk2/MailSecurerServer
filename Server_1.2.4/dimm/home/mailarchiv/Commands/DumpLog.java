/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Commands;

import java.io.File;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class DumpLog extends AbstractCommand
{

    File dump;

    /** Creates a new instance of HelloCommand */
    public DumpLog()
    {
        super("dump_log");
        dump = null;
    }

    @Override
    public boolean do_command( String data )
    {
        boolean ok = true;
        answer = "";

        String opt = get_opts(data);

        ParseToken pt = new ParseToken(opt);

        dump = null;

        int ma_id = (int) pt.GetLongValue("MA:");
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(ma_id);
        if (ma_id <= 0)
        {
            answer = "1: Invalid mandant";
            return ok;
        }
        boolean del_after_dump = pt.GetBoolean("DEL:");

        dump = Main.build_log_dump(del_after_dump);

        if (dump == null)
        {
            ok = false;
        }
        else if (!dump.exists())
        {
            ok = false;
        }
        else
        {
            String ret = m_ctx.get_tcp_call_connect().RMX_OpenInStream( getSsoEntry(), dump.getAbsolutePath(), null);
            answer = ret;
            return ok;
        }

        return ok;
    }

}
