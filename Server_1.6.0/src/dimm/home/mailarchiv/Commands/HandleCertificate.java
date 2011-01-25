/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import home.shared.Utilities.ParseToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
/*
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
*/
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
            boolean trust_certs = pt.GetBoolean("TC:");

            ByteBuffer cert_object = pt.GetObject("CERT:", ByteBuffer.class);
           
            // CREATE UNIQUE BUT STRUCTURED NAME -> PREFIX, IP, TIME, SUFFIX
            File cert_file = null;
            
            
            try
            {
                cert_file = Main.get_control().create_temp_file("cert");

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
            
            answer = KeyToolHelper.import_cacert( alias, cert_file, system_keystore, trust_certs );
            
            cert_file.delete();
        }

        if (cmd.compareTo("delete_alias") == 0)
        {
            answer = KeyToolHelper.delete_alias(alias, system_keystore);
        }
        if (cmd.compareTo("delete_cert") == 0)
        {
            Object o  = pt.GetCompressedObject("CERT:");
            if (o instanceof Certificate[])
            {
                Certificate[] certs = (Certificate[])o;
                alias = KeyToolHelper.get_alias_from_certificate(certs[0], system_keystore);
                answer = KeyToolHelper.delete_alias(alias, system_keystore);

            }
        }
        if (cmd.compareTo("create") == 0)
        {
            String dn_string = "\"" +
                    "CN=" + pt.GetString("CN:") + ", " +
                    "O=" + pt.GetString("O_:") + ", " +
                    "OU=" + pt.GetString("OU:") + ", " +
                    "S=" + pt.GetString("S_:") + ", " +
                    "L=" + pt.GetString("L_:") + ", " +
                    "C=" + pt.GetString("C_:") + "\"";

            String key_length = pt.GetString("KL:");

            answer = KeyToolHelper.create_key(dn_string, alias, key_length, system_keystore);            
        }
        

        if (cmd.compareTo("list") == 0)
        {
            try
            {                
                ArrayList<X509Certificate[]> cert_list = KeyToolHelper.list_certificates( system_keystore);

                String xml = ParseToken.BuildCompressedObjectString(cert_list);
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
                    String xml = ParseToken.BuildCompressedObjectString(sb.toString());
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
