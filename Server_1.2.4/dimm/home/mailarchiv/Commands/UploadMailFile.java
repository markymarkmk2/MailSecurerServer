/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.TempFileHandler;
import home.shared.CS_Constants;
import home.shared.Utilities.ParseToken;
import java.io.File;
import java.net.InetSocketAddress;

/**
 *
 * @author Administrator
 */


public class UploadMailFile extends AbstractCommand
{
    
   
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

        String suffix = CS_Constants.get_suffix_from_em_type(type);

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);

        TempFileHandler tfh = m_ctx.getTempFileHandler();

        try
        {
            tfh.check_space(size);
        }
        catch (VaultException vaultException)
        {
            answer = "2: " + vaultException.getMessage();
            return true;
        }

        String source_ip = "127.0.0.1";
        if (sock != null)
        {
            InetSocketAddress adr = (InetSocketAddress)sock.getRemoteSocketAddress();
            source_ip = adr.getHostName();
        }

        // CREATE UNIQUE BUT STRUCTURED NAME -> PREFIX, IP, TIME, SUFFIX
        String name = tfh.create_imp_mail_path( source_ip, suffix );
        File mbox_file = new File( name );
        int r = 10;
        while (mbox_file.exists() && r > 0)
        {
            LogicControl.sleep(10);
            name = tfh.create_imp_mail_path( source_ip, suffix );
            mbox_file = new File( name );
            r--;
        }
        if (mbox_file.exists())
        {
            answer = "3: " + Main.Txt("cannot create temp file:") + " " + mbox_file.getAbsolutePath();
            return true;
        }

        String ret = m_ctx.get_tcp_call_connect().RMX_OpenOutStream( getSsoEntry(), mbox_file.getAbsolutePath(), "");

        answer = ret;

        return true;
    }        
}
