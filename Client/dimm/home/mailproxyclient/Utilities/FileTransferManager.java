/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



/**
 *
 * @author Administrator
 */

package dimm.home.mailproxyclient.Utilities;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import dimm.home.mailproxyclient.Main;
import dimm.home.mailproxyclient.Preferences;

class ServerEntry
{
    private String host;
    private int port;
    
    public ServerEntry( String i, int p )
    {
        host = i;
        port = p;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }
}
        
        
/**
 *
 * @author Administrator
 */
public class FileTransferManager extends WorkerParent 
{
            
    int actual_server_idx;
    public static final String NAME = "FileTransferManager";
    String last_errortext = "";
    
    ArrayList<ServerEntry> server_list;
    Header[] response_hlist;
    
    
    /** Creates a new instance of DataGatherer */
    public FileTransferManager()
    {
        super(NAME);
        server_list = new ArrayList<ServerEntry>();
        
        initialize();
    }
    public FileTransferManager(String server)
    {
        this( server, 80 );
    }

    public FileTransferManager(String server, int port )
    {
        super(NAME);
        server_list = new ArrayList<ServerEntry>();
        server_list.add( new ServerEntry( server, port ) );
        
    }
    
    public String get_resonse_header( String key)
    {
        if (response_hlist == null)
            return null;
        
        for (int i = 0; i < response_hlist.length; i++)
        {
            if (response_hlist[i].getName().compareTo(key) == 0)
                return response_hlist[i].getValue();
        }
        return null;
    }

    public String get_last_errortext()
    {
        return last_errortext;
    }
    
    ArrayList<ServerEntry> get_server_list()
    {
        return server_list;
    }
    
    public boolean initialize()
    {
        // DEFAULT
        server_list.clear();
        server_list.add( new ServerEntry( "thales.ebiz-webhosting.de", 80 ) );
        
                
        return true;
    }
    
    
   
    // LOOKS ON ALL SERVERS
    public boolean download_file( String server_path, String local_path, boolean obfuscate)
    {
        if (server_list.size() == 0)
            return false;
        
        ServerEntry entry = server_list.get( actual_server_idx );
        
        boolean ok = download_file( entry , server_path, local_path, obfuscate);
        
        if (ok)
            return true;
        
        for (int i = 0; i < server_list.size() && !ok; i++)
        {
            actual_server_idx++;
            actual_server_idx = actual_server_idx % server_list.size();            

            entry = server_list.get( actual_server_idx );
            ok = download_file( entry , server_path, local_path, obfuscate);
        }
        
        return ok;
    }
    // LOOKS ON ALL SERVERS
    public GetMethod open_dl_stream( String server_path)
    {
        if (server_list.size() == 0)
            return null;
        
        ServerEntry entry = server_list.get( actual_server_idx );
        
        GetMethod ret = open_dl_stream( entry , server_path);
        
        if (ret != null)
            return ret;
        
        for (int i = 0; i < server_list.size() && ret == null; i++)
        {
            actual_server_idx++;
            actual_server_idx = actual_server_idx % server_list.size();            

            entry = server_list.get( actual_server_idx );
            ret = open_dl_stream( entry , server_path);
        }
        
        return ret;
    }
    
    
    // LOOKS ON ALL SERVERS
    public boolean upload_file( String server_path, String local_path, String user, String pwd, boolean obfuscate)
    {
        if (server_list.size() == 0)
            return false;
        
        ServerEntry entry = server_list.get( actual_server_idx );
        
        boolean ok = upload_file( entry , server_path, local_path, user, pwd, obfuscate);
        
        if (ok)
            return true;
        
        for (int i = 0; i < server_list.size() && !ok; i++)
        {
            actual_server_idx++;
            actual_server_idx = actual_server_idx % server_list.size();            

            entry = server_list.get( actual_server_idx );
            ok = upload_file( entry , server_path, local_path, user, pwd, obfuscate);
        }
        
        return ok;
    }
    
    // LOOKS ON ALL SERVERS
    public boolean exists_file( String server_path)
    {
        if (server_list.size() == 0)
            return false;
        
        ServerEntry entry = server_list.get( actual_server_idx );
        
        boolean ok = exists_file( entry , server_path);
        
        if (ok)
            return true;
        
        for (int i = 0; i < server_list.size() && !ok; i++)
        {
            actual_server_idx++;
            actual_server_idx = actual_server_idx % server_list.size();            

            entry = server_list.get( actual_server_idx );
            ok = exists_file( entry , server_path);
        }
        
        return ok;
    }
    
    
    boolean download_file( ServerEntry entry, String server_path, String local_path, boolean obfuscate)
    {
        HostConfiguration host_conf = new HostConfiguration();
        
        String host = entry.getHost();
        int port    = entry.getPort();
        
        host_conf.setHost( host,  port );
        
                
        GetMethod get = new GetMethod(server_path);
        
        HttpClient http_client = new HttpClient();

        // PROXY SETTINGS
        if (Main.is_proxy_enabled())
        {
            String px_server = Main.get_prop( Preferences.PXSERVER );
            // HTTP-PROXY
            int px_port = (int)Main.get_long_prop( Preferences.PXPORT );
            
            if (px_server == null || px_server.length() == 0 || px_port == 0)
            {
                Main.err_log("Proxysettings are invalid! I will ignore these and try direct connect");
            }
            else
            {
                host_conf.setProxy( px_server, px_port );            
            }
        }
        
        int data_len = 0;
        try
        {
            //Main..debug_msg( 1, "Executing GET from <" + host + ":" + port + server_path + " to " + local_path );
            
            http_client.executeMethod(host_conf, get );
            
            int rcode = get.getStatusCode();
            StatusLine sl = get.getStatusLine();
            
            if (rcode < 200 || rcode > 210)
            {                
                // SERVER ANSWERS WITH ERROR
                last_errortext = get.getStatusText();
                return false;
            }
            response_hlist = get.getResponseHeaders();
            
            InputStream istr = get.getResponseBodyAsStream();
                        
            BufferedInputStream bis = new BufferedInputStream( istr );
            FileOutputStream fw = new FileOutputStream( local_path );
            
            int bs = 4096;
            byte[] buffer = new byte[bs];
            
            
            while (true)
            {
                int rlen = bis.read( buffer );
                
                data_len += rlen;
                
                if (rlen == -1)
                    break;
                if (obfuscate)
                {
                    for (int i = 0; i < rlen; i++)
                    {
                        buffer[i] = (byte)~buffer[i];   // KOMPLEMENT: BILLIG UND GUT
                    }
                }
                fw.write( buffer,0, rlen );                
            }
            fw.close();
            bis.close();
            
        }
        catch ( Exception exc )
        {
            Main.err_log("Download of " + server_path + " from " +  host + " failed: " + exc.getMessage() );      
            last_errortext = exc.getMessage();
            return false;
        }
        
        
        if (data_len == 0)
            return false;
        
        File f = new File( local_path );
        if (f.exists())           
            return true;
        
        return false;
    }

    GetMethod open_dl_stream( ServerEntry entry, String server_path)
    {
        HostConfiguration host_conf = new HostConfiguration();
        
        String host = entry.getHost();
        int port    = entry.getPort();
        
        host_conf.setHost( host,  port );
        
                
        GetMethod get = new GetMethod(server_path);
        
        HttpClient http_client = new HttpClient();

        // PROXY SETTINGS
        if (Main.is_proxy_enabled())
        {
            String px_server = Main.get_prop( Preferences.PXSERVER );
            // HTTP-PROXY
            int px_port = (int)Main.get_long_prop( Preferences.PXPORT );
            
            if (px_server == null || px_server.length() == 0 || px_port == 0)
            {
                Main.err_log("Proxysettings are invalid! I will ignore these and try direct connect");
            }
            else
            {
                host_conf.setProxy( px_server, px_port );            
            }
        }
        
        
        try
        {
            //UserMain.self.debug_msg( 1, "Executing GET from <" + host + ":" + port + server_path + " to local stream" );
            
            http_client.executeMethod(host_conf, get );
            
            int rcode = get.getStatusCode();
            //StatusLine sl = get.getStatusLine();
            
            if (rcode < 200 || rcode > 210)
            {                
                // SERVER ANSWERS WITH ERROR
                last_errortext = get.getStatusText();
                return null;
            }
            response_hlist = get.getResponseHeaders();
            
            
               
            
            
            return get;
            
        }
        catch ( Exception exc )
        {
            Main.err_log("Download of " + server_path + " from " +  host + " failed: " + exc.getMessage() );      
            last_errortext = exc.getMessage();
        }
        return null;
    }
    
    boolean upload_file( ServerEntry entry, String server_path, String local_path, String user, String pwd, boolean obfuscate)
    {
        HostConfiguration host_conf = new HostConfiguration();
        
        String host = entry.getHost();
        int port    = entry.getPort();
        
        host_conf.setHost( host,  port );
        
        HttpClient http_client = new HttpClient();
        Credentials defaultcreds = new UsernamePasswordCredentials(user, pwd);
        http_client.getState().setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM), defaultcreds);        

        // PROXY SETTINGS
        if (Main.is_proxy_enabled())
        {
            String px_server = Main.get_prop( Preferences.PXSERVER );
            // HTTP-PROXY
            int px_port = (int)Main.get_long_prop( Preferences.PXPORT );
            
            if (px_server == null || px_server.length() == 0 || px_port == 0)
            {
                Main.err_log("Proxysettings are invalid! I will ignore these and try direct connect");
            }
            else
            {
                host_conf.setProxy( px_server, px_port );            
            }
        }
               
        try
        {
            //UserMain.self.debug_msg( 1, "Executing POST " + local_path + " to <" + host + ":" + port + "/" + server_path + ">" );
            

            //File f = new File(local_path);
            /*PostMethod put = new PostMethod(server_path);
            
            Part[] parts = { new FilePart(f.getName(), f) };
            put.setRequestEntity( new MultipartRequestEntity(parts, put.getParams()) );
*/
          //  HeadMethod put = new HeadMethod("/websense/v5/userclips/ls.txt");
            //put.setRequestBody(new FileInputStream(local_path));
            
            
            //put.setDoAuthentication(true); 
            
            PutMethod put = new PutMethod(server_path);
            FileInputStream fis = new FileInputStream(local_path);
            
            put.setRequestBody( fis );
            
            put.setDoAuthentication(true); 
            
            int rcode = http_client.executeMethod(host_conf,  put);            
            
            fis.close();
                        
            if (rcode < 200 || rcode > 210)
            {                
                // SERVER ANSWERS WITH ERROR   
                last_errortext = put.getStatusText();
                return false;
            }                        
        }
        catch ( Exception exc )
        {
            Main.err_log("Upload of " + server_path + " from " +  host + " failed: " + exc.getMessage() ); 
            last_errortext = exc.getMessage();
            return false;
        }
        
        
        return true;
    }
    

    boolean exists_file( ServerEntry entry, String server_path )
    {
        HostConfiguration host_conf = new HostConfiguration();
        
        String host = entry.getHost();
        int port    = entry.getPort();
        
        host_conf.setHost( host,  port );
        
                
        HeadMethod get = new HeadMethod(server_path);
        
        HttpClient http_client = new HttpClient();

        // PROXY SETTINGS
        if (Main.is_proxy_enabled())
        {
            String px_server = Main.get_prop( Preferences.PXSERVER );
            // HTTP-PROXY
            int px_port = (int)Main.get_long_prop( Preferences.PXPORT );
            
            if (px_server == null || px_server.length() == 0 || px_port == 0)
            {
                Main.err_log("Proxysettings are invalid! I will ignore these and try direct connect");
            }
            else
            {
                host_conf.setProxy( px_server, px_port );            
            }
        }
        
       
        try
        {
            //UserMain.self.debug_msg( 1, "Executing HEADER from <" + host + ":" + port + server_path );
            
            http_client.executeMethod(host_conf, get );
            
            response_hlist = get.getResponseHeaders();
            int rcode = get.getStatusCode();
            
            
            if (rcode < 200 || rcode > 210)
            {                
                // SERVER ANSWERS WITH ERROR
                last_errortext = get.getStatusText();
                return false;
            }
            return true;
        }
        catch ( Exception exc )
        {
            Main.err_log("Existance check of " + server_path + " from " +  host + " failed: " + exc.getMessage() );       
            last_errortext = exc.getMessage();
        }
        
        return false;
    }
    
            
    
    
    public boolean check_requirements(StringBuffer sb)
    {
        boolean ok = true;
        
        if (server_list.size() == 0)
        {
            sb.append("Server list is empty" );
            ok = false;
        }
        return ok;        
        
    }

    @Override
    public boolean start_run_loop()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    
}
