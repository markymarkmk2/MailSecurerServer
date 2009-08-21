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
import dimm.home.httpd.OutputStreamEntry;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
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


        String oid = pt.GetString("OI:");  // STREAM HANDLE FROM A PREVIOUS upload_mail_file CALL
        long m_id = pt.GetLongValue("MA:");
        long da_id = pt.GetLongValue("DA:");

        // GET STRUCTS FROM ARGS
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
        Mandant m = m_ctx.getMandant();
        DiskArchive da = m_ctx.get_da_by_id(da_id);

        TCPCallConnect conn = Main.get_control().get_tcp_call_connect();
        OutputStreamEntry ose = null;

        // GET ALREADY OPEN STREAM ENTRY
        try
        {
            ose = conn.get_ostream( conn.get_id(oid) );
        }
        catch (Exception exc)
        {
            answer = "2: stream " + oid + " is not open";
            return true;
        }

        // NOW CLOSE THE STREAM
        conn.RMX_CloseOutStream(oid);


        // MOVE TO IMPORT PATH
        String new_path = m_ctx.get_tmp_path() + Main.IMPORTRELPATH + ose.file.getName();
        File nf = new File(new_path);
        
        // VERY UNLIKELY THERE IS ALREADY A FILE WITH SAME NAME...
        int i = 10;
        while (nf.exists() && i > 0)
        {
            new_path += Long.toString(System.currentTimeMillis() % 10000);
            nf = new File(new_path);
            i--;
        }

        if (!ose.file.renameTo( nf ))
        {
            answer = "3: " + Main.Txt("cannot rename") + " " + ose.file.getName() + " -> " + new_path;
            return true;
        }

        // THIS FILE CONTAINS THE MAIL FILE
        String path = ose.file.getAbsolutePath();  // == new_path


        // PREFIX IS UploadMailFile.IMPMAIL_PREFIX, SUFFIX DEPENDS ON SOURCE
        // REGISTER AT TASK
        Main.get_control().get_mb_import_server().add_mbox_import(m, da, path);

        // YEEHAW, WE'RE DONE
        answer = "0: ok";
                
        return true;
    }        
}
