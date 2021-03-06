/*
 * Preferences.java
 *
 * Created on 5. Oktober 2007, 18:58
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Utilities.Preferences;




/**
 *
 * @author Administrator
 */
public class MandantPreferences extends Preferences
{
    
    public static final String TEMPFILEDIR ="TempFileDir";
    public static final String LANG = "Language";
    public static final String INDEX_TASK = "IndexTask";
    public static final String SERVER_IP = "ServerIP";
    
    
    public static final String ALLOW_CONTINUE_ON_ERROR = "AllowContinueOnError";
    public static final String INDEX_THREADS = "IndexyThreads";
    public static final long DFTL_INDEX_THREADS = 8;

    
    public static final String SSO_TIMEOUT_S = "SSOTimeout_s";
    public static final long   DFTL_SSO_TIMEOUT_S = 7200;  // 20 MINUTES

    public static final String DSH_HOUR_DIRS = "DiskSpaceHourDirectories";
    public static final String HTTPD_PORT = "HttpdPort";
    public static final String PORT = "Port";
    public static final String IMAP_TIMING = "IMAPFetcherTiming";
    
    MandantContext context;

    

    
    /** Creates a new instance of Preferences */
    public MandantPreferences(MandantContext _context)
    {
        this( Main.PREFS_PATH );
        context = _context;
    }

    public MandantPreferences( String _path)
    {
        super(_path);

        context = null;
        
        prop_names.add( TEMPFILEDIR );
        
        prop_names.add( ALLOW_CONTINUE_ON_ERROR );
        prop_names.add( INDEX_TASK );
     
        prop_names.add( SERVER_IP );
        prop_names.add( SSO_TIMEOUT_S );
        prop_names.add( INDEX_THREADS );
        prop_names.add( DSH_HOUR_DIRS );
        prop_names.add( LANG );
        prop_names.add( HTTPD_PORT );
        prop_names.add( PORT );
        prop_names.add( IMAP_TIMING );
    }
    
    public void setContext( MandantContext _context )
    {
        context = _context;
    }

 
    
    public String get_language()
    {
        String lang =  get_prop( LANG);
        if (lang == null)
            lang = "de";

        return lang;
    }


   
    

    
}
