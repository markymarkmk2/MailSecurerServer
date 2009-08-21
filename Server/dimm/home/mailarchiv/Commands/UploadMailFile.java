/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.StringTokenizer;

/**
 *
 * @author Administrator
 */


public class UploadMailFile extends AbstractCommand
{

    public static final String IMPMAIL_PREFIX = "mailimp";
    
    /** Creates a new instance of HelloCommand */
    public UploadMailFile()
    {
        super("upload_mail_file");
        
    }

    public static String create_imp_mail_path( String dir, String ip, String suffix )
    {

        String name = dir + "/" + IMPMAIL_PREFIX + "_" + ip + "_" + System.currentTimeMillis() + "." + suffix;
        return name;
    }
    private static String get_ip_mail_path( File f, int n )
    {
        try
        {
            StringTokenizer sto = new StringTokenizer(f.getName(), "_");

            while (n > 0)
            {
                sto.nextToken();
                n--;
            }
            return sto.nextToken();
        }
        catch (Exception e)
        {
        }
        return null;
    }
    public static String get_ip_from_mail_path( File f )
    {
        return get_ip_mail_path(f, 1);
    }
    public static long get_time_from_mail_path( File f )
    {
        try
        {
            return Long.parseLong(get_ip_mail_path(f, 2));
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return 0;
    }


    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        long m_id = pt.GetLongValue("MA:");
        String type = pt.GetString("TY:");
        long size = pt.GetLongValue("SI:");

        String suffix = CS_Constants.get_suffix_from_type(type);

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);

        File tmp_dir =  m_ctx.get_tmp_path();
        if (!tmp_dir.exists())
        {
            answer = "1: " + Main.Txt("temp_filesystem_does_not_exist:") + " " + tmp_dir.getAbsolutePath();
            return true;
        }

        long free_space = tmp_dir.getFreeSpace();
        if ( free_space - size < Main.MIN_FREE_SPACE)
        {
            answer = "2: " + Main.Txt("not enough space left on temp filesystem:") + " " + tmp_dir.getAbsolutePath();
            return true;
        }

        String source_ip = "127.0.0.1";
        if (sock != null)
        {
            InetSocketAddress adr = (InetSocketAddress)sock.getRemoteSocketAddress();
            source_ip = adr.getHostName();
        }

        // CREATE UNIQUE BUT STRUCTURED NAME -> PREFIX, IP, TIME, SUFFIX
        String name = create_imp_mail_path( tmp_dir.getAbsolutePath(), source_ip, suffix );
        File mbox_file = new File( name );
        int r = 10;
        while (mbox_file.exists() && r > 0)
        {
            LogicControl.sleep(10);
            name = create_imp_mail_path( tmp_dir.getAbsolutePath(), source_ip, suffix );
            mbox_file = new File( name );
            r--;
        }
        if (mbox_file.exists())
        {
            answer = "3: " + Main.Txt("cannot create temp file:") + " " + mbox_file.getAbsolutePath();
            return true;
        }

        String ret = Main.get_control().get_tcp_call_connect().RMX_OpenOutStream(mbox_file.getAbsolutePath(), "");

        answer = ret;

        return true;
    }        
}
