/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.TempFileHandler;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.ParseToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 *
 * @author Administrator
 */


public class UploadCertificate extends AbstractCommand
{
    
   
    public UploadCertificate()
    {
        super("upload_certificate");
        
    }

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        long m_id = pt.GetLongValue("MA:");
        String type = pt.GetString("TY:");

        // SYNTAX: CERT:" + XStream.toXML( String cert );
        int cert_idx = data.indexOf("CERT:");
        if (cert_idx < 0)
            return false;

        String cert = data.substring(cert_idx + 5);
        XStream xs = new XStream();
        ByteBuffer cert_object = (ByteBuffer)xs.fromXML(cert);

        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
        TempFileHandler tfh = m_ctx.getTempFileHandler();


        String source_ip = "127.0.0.1";
        if (sock != null)
        {
            InetSocketAddress adr = (InetSocketAddress)sock.getRemoteSocketAddress();
            source_ip = adr.getHostName();
        }

        // CREATE UNIQUE BUT STRUCTURED NAME -> PREFIX, IP, TIME, SUFFIX
        String name = tfh.create_imp_mail_path( source_ip, "cert" );
        File cert_file = new File( name );
        int r = 10;
        while (cert_file.exists() && r > 0)
        {
            LogicControl.sleep(10);
            name = tfh.create_imp_mail_path( source_ip, cert );
            cert_file = new File( name );
            r--;
        }
        if (cert_file.exists())
        {
            answer = "3: " + Main.Txt("cannot_create_temp_file") + ": " + cert_file.getAbsolutePath();
            return true;
        }
        try
        {
            FileOutputStream fw = new FileOutputStream(cert_file);
            fw.write(cert_object.array());
            fw.close();
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            answer = "4: " + Main.Txt("cannot_write_cert_file") + ": " + cert_file.getAbsolutePath();
        }

        if (type.compareTo("cacert") == 0)
        {
            answer = import_cacert( cert_file );
        }
        else
        {
            answer = "5: unknown type of certificate";
        }
        tfh.delete(cert_file);

        
        return true;
    }

    public static String import_cacert( File cert_file )
    {
        String ret = "1: urks";

        String java_home = System.getProperty("java.home").trim();

        String key_tool_cmd = java_home + "/bin/keytool";
        String ca_cert_file = java_home + "/lib/security/cacerts";

        String[] cert_import_cmd = { key_tool_cmd, "-import", "-noprompt", "-alias", "MailSecurerCA", "-storepass", "changeit",
        "-file", cert_file.getAbsolutePath(), "-keystore", ca_cert_file};
        
        CmdExecutor exec = new CmdExecutor(cert_import_cmd);

        int code = exec.exec();
        if (code == 0)
        {
            ret = "0: ok";
        }
        else
        {
            ret = "4: " + exec.get_out_text() + " " + exec.get_err_text();
        }
        return ret;
    }
}
