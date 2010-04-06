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
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.TempFileHandler;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.Utilities.ZipUtilities;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */


public class HandleCertificate extends AbstractCommand
{
       
    public HandleCertificate()
    {
        super("certificate");
        
    }

    public static void check_keystore( boolean system_keystore)
    {
        try
        {
            if (!KeyToolHelper.is_keystore_valid(system_keystore))
            {
                LogManager.err_log_warn("Rebuilding keystore");
                KeyToolHelper.init_keystore(Main.get_fqdn());
            }
        }
        catch (IOException iOException)
        {
            LogManager.err_log("Cannot Initialize Keystore: " + iOException.getMessage());
        }

    }

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        long m_id = pt.GetLongValue("MA:");
        String type = pt.GetString("TY:");
        String keystore = pt.GetString("KS:");
        String alias = pt.GetString("AL:");
        String cmd = pt.GetString("CMD:");

        boolean system_keystore = false;
        if (keystore.compareTo("system") == 0)
            system_keystore = true;
        if (alias.length() == 0)
            alias = KeyToolHelper.MS_ALIAS;

        if (cmd.compareTo("import") == 0)
        {

            // SYNTAX: CERT:" + XStream.toXML( String cert );
            int cert_idx = data.indexOf("CERT:");
            if (cert_idx < 0)
                return false;

            boolean trust_certs = pt.GetBoolean("TC:");

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
            File cert_file = null;
            
            
            try
            {
                cert_file = tfh.create_temp_file(source_ip, "cert", ".imp", true);

                FileOutputStream fw = new FileOutputStream(cert_file);
                fw.write(cert_object.array());
                fw.close();
            }
            catch (IOException iOException)
            {
                iOException.printStackTrace();
                answer = "4: " + Main.Txt("cannot_write_cert_file") + ": " + cert_file.getAbsolutePath();
                return true;
            }
            finally
            {
                if (cert_file != null)
                    tfh.delete(cert_file);
            }

            if (type.compareTo("cacert") == 0)
            {
                answer = KeyToolHelper.import_cacert( alias, system_keystore, trust_certs );
            }
            else
            {
                answer = "5: unknown type of certificate";
            }            
        }

        if (cmd.compareTo("delete_alias") == 0)
        {
            answer = KeyToolHelper.delete_alias(alias, system_keystore);
            check_keystore( system_keystore );
        }
        if (cmd.compareTo("delete_cert") == 0)
        {
            String xml = pt.GetString("CERT:");
            xml = ZipUtilities.uncompress(xml);
            XStream xs = new XStream();
            Object o  = xs.fromXML(xml);
            if (o instanceof Certificate)
            {
                Certificate cert = (Certificate)o;
                alias = KeyToolHelper.get_alias_from_certificate(cert, system_keystore);
                answer = KeyToolHelper.delete_alias(alias, system_keystore);

                check_keystore( system_keystore );
            }
        }
        

        if (cmd.compareTo("list") == 0)
        {
            try
            {                
                ArrayList<X509Certificate[]> cert_list = KeyToolHelper.list_certificates( system_keystore);

                XStream xs = new XStream();
                String xml = xs.toXML(cert_list);
                xml = ZipUtilities.compress(xml);
                answer = "0: CL:\"" + xml + "\"";
            }
            catch (Exception exc)
            {
                answer = "1: " + exc.getMessage();
            }
        }
        if (cmd.compareTo("create_csr") == 0)
        {
            try
            {
                StringBuffer sb = new StringBuffer();
                boolean ok = KeyToolHelper.create_csr(alias, system_keystore, sb);

                if (ok)
                {
                    XStream xs = new XStream();
                    String xml = xs.toXML(sb.toString());
                    xml = ZipUtilities.compress(xml);
                    answer = "0: CSR:\"" + xml + "\"";
                }
                else
                {
                    answer = "1: " + sb.toString();
                }
            }
            catch (Exception exc)
            {
                answer = "2: " + exc.getMessage();
            }
        }
        return true;
    }


}
