/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.File;
import java.io.FileOutputStream;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class WriteFile extends AbstractCommand
{
    File dump;
    
    
    private static final int WF_NAME_LEN = 1024;
    
    /** Creates a new instance of HelloCommand */
    public WriteFile()
    {
        super("WRITEFILE");
        dump = null;
    }
    @Override
    public boolean do_command(String data)
    {
        // NOT CALLED
        return false;
    }

    @Override
    public boolean do_command(byte[] data)
    {
        answer = "";
        int name_len = WF_NAME_LEN - (token.length()+1);
        
        if (data.length < name_len)
            return false;
        
        String opt_data = new String( data, 0, name_len );
         
        ParseToken pt = new ParseToken(opt_data);
        
        String filename = pt.GetString("PATH:");                   
                       
        int file_len = data.length - name_len;

        boolean ok = true;
        
        LogManager.msg_system(LogManager.LVL_DEBUG,  "Schreibe " + file_len + " Byte in Datei " + filename );
        
        try
        {
            FileOutputStream fw = new FileOutputStream(filename);
            fw.write( data, name_len, file_len );
            fw.close();
        }
        
        catch (Exception exc)
        {
            LogManager.msg_system(LogManager.LVL_ERR, "Kann Datei " + filename + " nicht schreiben: " + exc.getMessage() );
            ok = false;
        }        
        return ok;
    }
    
  
}
