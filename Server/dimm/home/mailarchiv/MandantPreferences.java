/*
 * Preferences.java
 *
 * Created on 5. Oktober 2007, 18:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Utilities.CryptTools;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.Preferences;
import org.apache.commons.codec.binary.Base64;


/****
 
 Bestehedne Config:
Playlists=/var/www/localhost/htdocs/websense/login/pls
Songs=/var/www/localhost/htdocs/websense/dev/music_v3

 ****/

/**
 *
 * @author Administrator
 */
public class MandantPreferences extends Preferences
{
    
    public static final String DEBUG = "Debug";
    public static final String TEMPFILEDIR ="TempFileDir";
    public static final String LANG = "Language";
    
    public static final String ALLOW_CONTINUE_ON_ERROR = "AllowContinueOnError";
/*    public static final String MAIL_ARCHIVA_URL = "MailArchivaURL";
    public static final String MAIL_ARCHIVA_AGENT_OPTS = "MailArchivaAgentOpts";
*/
//    public static final String MAIL_ARCHIVA_AGENT_OPTS = "MailArchivaAgentOpts";
    
    String password;
    public static final String ENC_PASSWORD = "EncryptionPassword";
    public static final String DFLT_PASSWORD = "12345";
    
    MandantContext context;
    
    /** Creates a new instance of Preferences */
    public MandantPreferences(MandantContext _context)
    {
        this( Main.PREFS_PATH );
    }

    public MandantPreferences( String _path)
    {
        super(_path);

        context = null;
        
        prop_names.add( DEBUG );
//        prop_names.add( MAIL_ARCHIVA_URL );
        prop_names.add( TEMPFILEDIR );
        prop_names.add( ENC_PASSWORD );
        
        prop_names.add( ALLOW_CONTINUE_ON_ERROR );
//        prop_names.add( MAIL_ARCHIVA_AGENT_OPTS );
        
                
        read_props();
    }
    public void setContext( MandantContext _context )
    {
        context = _context;
    }

    @Override
    public void read_props()
    {
        super.read_props();
        password = get_prop(ENC_PASSWORD);
        
        String str = CryptTools.crypt_internal(context, password, CryptTools.ENC_MODE.DECRYPT);
        if (str == null)
        {
            LogManager.err_log_fatal("Cannot decrypt password from preferences");
        }
        else
        {
            password = str;
        }
    }

    @Override
    public boolean store_props()
    {
        if (password == null)
        {
            LogManager.err_log_fatal("There is no password set");
        }
        else
        {
            String pwd = CryptTools.crypt_internal(context, password, CryptTools.ENC_MODE.ENCRYPT);
            set_prop(ENC_PASSWORD, pwd);
        }
        return super.store_props();
    }
    
    public String get_password()
    {
        if (password == null)
            return DFLT_PASSWORD;

        return password;
    }
    public String get_language()
    {
        String lang =  get_prop( LANG);
        if (lang == null)
            lang = "de";

        return lang;
    }



    public void set_password( String pwd )
    {
        if (pwd != null && pwd.length() > 0)
        {
            password = CryptTools.get_sha256( pwd.getBytes() );
            password = new String ( Base64.encodeBase64(password.getBytes()) );
        }
    }

    public String get_KeyAlgorithm()
    {
        return Main.prefs.get_KeyAlgorithm();
    }
    public byte[] get_KeyPBESalt()
    {
        return Main.prefs.get_KeyPBESalt();
    }
    public int get_KeyPBEIteration()
    {
        return Main.prefs.get_KeyPBEIteration();
    }
    
    // USED FOR ENCRYPTION END DECRYPTION OF INTERNAL SECRETS
    public String get_InternalPassPhrase()
    {
        return Main.prefs.get_InternalPassPhrase();
    }

    
}
