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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.Utilities.LogConfigEntry;
import home.shared.Utilities.ParseToken;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;

/**
 *
 * @author Administrator
 */
public class GetLog extends AbstractCommand
{

    File dump;

    /** Creates a new instance of HelloCommand */
    public GetLog()
    {
        super("show_log");
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

        String command = pt.GetString("CMD:");

        if (command.compareTo("read") == 0)
        {
            // show_log CMD:read LG:L4 LI:" + LINES_PER_CALL + " OF:" + offset
            String log_type = pt.GetString("LG:");
            int lines = pt.GetLong("LI:").intValue();
            long offset = pt.GetLong("OF:");

            File log_file = LogManager.get_file_by_type(log_type);
            if (log_file == null || !log_file.exists())
            {
                answer = "1: not exist";
                return ok;
            }
            long len = log_file.length();
            if (offset >= len)
            {
                answer = "42: eof";
                return ok;
            }

            Vector<String> v = LogManager.tail(log_type, offset, lines);
            if (v == null)
            {
                answer = "2: tail gave null";
                return ok;
            }
            StringBuilder sb = new StringBuilder();

            Enumeration e = v.elements();
            while (e.hasMoreElements())
            {
                sb.append(e.nextElement());
                sb.append("\n");
            }

            answer = "0: " + sb.toString();
        }
        else if (command.compareTo("get_config") == 0)
        {

            ArrayList<LogConfigEntry> arr = LogManager.get_log_config_arry();
            String s = ParseToken.BuildCompressedObjectString(arr);
            answer = "0: CFG:" + s;
            return true;

        }
        else if ( command.compareTo("set_config") == 0)
        {
            String cfg = pt.GetString("CFG:");
            boolean write_cfg = pt.GetBoolean("WC:");

            Object o = ParseToken.DeCompressObject(cfg);
            if (o instanceof ArrayList)
            {
                ArrayList<LogConfigEntry> arr = (ArrayList<LogConfigEntry>)o;
                LogManager.set_log_config_arry(arr, write_cfg);
                answer = "0: ";
            }
            else
            {
                answer = "1: invalid param";
            }

            return true;

         }

        else
            answer = "1: invalid command";


        return ok;
    }
       @Override
    public InputStream get_stream()
    {
        FileInputStream fis;
        try
        {
            fis = new FileInputStream(dump);
        }
        catch (FileNotFoundException ex)
        {
            return null;
        }
        return fis;
    }

    @Override
    public long get_data_len()
    {
        return dump.length();
    }

    @Override
    public boolean has_stream()
    {
        return (dump != null);
    }

}
