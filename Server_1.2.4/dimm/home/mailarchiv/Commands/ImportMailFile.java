/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;


import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.serverconnect.OutputStreamEntry;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.vault.DiskVault;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import java.io.File;

/**
 *
 * @author Administrator
 */


public class ImportMailFile extends AbstractCommand
{

    
    /** Creates a new instance of HelloCommand */
    public ImportMailFile()
    {
        super("import_mail_file");
        
    }
    

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        try
        {
            String oid = pt.GetString("OI:");  // STREAM HANDLE FROM A PREVIOUS upload_mail_file CALL
            int m_id = (int)pt.GetLongValue("MA:");
            int da_id = (int)pt.GetLongValue("DA:");

            // GET STRUCTS FROM ARGS
            MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
            Mandant m = m_ctx.getMandant();
            DiskArchive da = m_ctx.get_da_by_id(da_id);
            DiskVault dv = m_ctx.get_vault_by_da_id(da_id);

            // CHECK FOR SPACE
            if (!dv.has_sufficient_space())
            {
                answer = "2: " + Main.Txt("Cannot_import_mail,_not_enough_space") + " " + da_id;
                return true;
            }

            TCPCallConnect conn = m_ctx.get_tcp_call_connect();
            OutputStreamEntry ose = null;

            // GET ALREADY OPEN STREAM ENTRY
            try
            {
                ose = conn.get_ostream(conn.get_id(oid));
            }
            catch (Exception exc)
            {
                answer = "2: stream " + oid + " is not open";
                return true;
            }

            // NOW CLOSE THE STREAM
            conn.RMX_CloseOutStream( getSsoEntry(), oid);

            File nf = m_ctx.getTempFileHandler().create_new_import_file(ose.file.getName(), da_id);
            if (!ose.file.renameTo(nf))
            {
                answer = "3: " + Main.Txt("cannot rename") + " " + ose.file.getName() + " -> " + nf.getAbsolutePath();
                return true;
            }

            // THIS FILE CONTAINS THE MAIL FILE
            String path = nf.getAbsolutePath();  // == new_path

            // PREFIX IS UploadMailFile.IMPMAIL_PREFIX, SUFFIX DEPENDS ON SOURCE
            // REGISTER AT TASK
            Main.get_control().register_new_import( m_ctx, da, path );

            // YEEHAW, WE'RE DONE
            answer = "0: ok";
        }
        catch (Exception e)
        {
            e.printStackTrace();
            answer = "9: " + e.getMessage();
        }
                
        return true;
    }        
}
