/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.httpd.TCPCallConnect;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Administrator
 */


public class UploadMailFile extends AbstractCommand
{

    
    /** Creates a new instance of HelloCommand */
    public UploadMailFile()
    {
        super("upload_mail_file");
        
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
            answer = "2: temp directory does not exist: " + tmp_dir.getAbsolutePath();
            return true;
        }

        long free_space = tmp_dir.getFreeSpace();
        if ( free_space - size < Main.MIN_FREE_SPACE)
        {
            answer = "2: not enough space left on disk: " + tmp_dir.getAbsolutePath();
            return true;
        }

        try
        {
            File mbox_file = File.createTempFile("mailimp", suffix, tmp_dir);

            String ret = Main.get_control().get_tcp_call_connect().RMX_OpenOutStream(mbox_file.getAbsolutePath(), "");

            answer = ret;

            return true;
        }
        catch (IOException iOException)
        {
            answer = "3: cannot create temp file: " + iOException.getMessage();
        }
        
        return true;

    }        
}
