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
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.ParseToken;

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
        super("GETLOG");
        dump = null;
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        
        ParseToken pt = new ParseToken(opt);
        
        boolean del_after_dump = pt.GetBoolean("DEL:");                   
        boolean ok = true;
        
        dump =  Main.build_log_dump( del_after_dump );
        
        if (dump == null)
            ok = false; 
        else if (!dump.exists() )
            ok = false; 
        
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
