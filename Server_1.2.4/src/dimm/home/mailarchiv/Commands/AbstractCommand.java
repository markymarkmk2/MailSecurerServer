/*
 * AbstractCommand.java
 *
 * Created on 8. Oktober 2007, 14:53
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.SQL.UserSSOEntry;
import java.io.InputStream;
import java.net.Socket;

/**
 *
 * @author Administrator
 */
public abstract class AbstractCommand
{
    String token;
    protected String answer;
    protected Socket sock;
    protected UserSSOEntry sso_entry;
    
    
    public static final String MISS_ARGS = "2: missing args";
    public static final String WRONG_ARGS = "3: wrong args";
    
    /** Creates a new instance of AbstractCommand */
    public AbstractCommand(String _token)
    {
        token = _token;
    }
    
    public abstract boolean do_command(String data);
    
    public String get_answer()  { return answer; }
       
    public String get_opts( String data )
    {
        return data.substring( token.length() + 1 );  // WG DOPPELPUNKT !!!!!!
    }
    public void set_socket( Socket s )
    {
        sock = s;
    }

    public UserSSOEntry getSsoEntry()
    {
        return sso_entry;
    }

    public void setSsoEntry( UserSSOEntry ssoc )
    {
        this.sso_entry = ssoc;
    }

  
    
    public boolean is_cmd( String data )
    {
        String deli_token = token + " ";
        
        // NUR BEFEHL
        if (data.length() == token.length())
        {
            if (data.compareTo( token ) == 0)
                return true;
        }
            
        // BEFEHL + OPTS
        if (data.length() >= deli_token.length())
        {
            if (data.substring(0, deli_token.length()).compareTo( deli_token) == 0)
            {
                return true;
            }
        }
        return false;        
    }

    public String get_token()
    {
        return token;
    }

    public boolean do_command(byte[] add_data)
    {
        String data = token + " ";
        if (add_data != null && add_data.length > 0)
            data += new String( add_data );
        
        return do_command( data );        
    }
        
    public boolean has_stream()
    {
        return false;
    }
    public InputStream get_stream()
    {
        return null;
    }
    public long get_data_len()
    {
        return 0;
    }
    public void log( int lvl, String txt )
    {
        log(lvl, txt, null);
    }
    public void log( int lvl, String txt, Exception exc )
    {
        LogManager.msg_cmd(lvl, "Command " + token + ": " + txt, exc );
    }
}
