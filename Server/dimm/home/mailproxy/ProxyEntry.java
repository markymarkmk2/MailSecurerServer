/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailproxy;

/**
 *
 * @author Administrator
 */
public class ProxyEntry
{
    public static final int POP3 = 1;
    public static final int SMTP = 2;
    public static final int IMAP = 3;
    public static String prt_list[] = {"invalid", "POP3", "SMTP", "IMAP" };
    
    private String host;
    private int localPort;
    private int remotePort;
    private int protokoll;
    private int instanceCnt;
    
    ProxyEntry( String _host, int l, int r, int p )
    {
        host = _host;
        localPort = l;
        remotePort = r;
        protokoll = p;
    }
    
    static public ProxyEntry create_pop3_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, POP3 );
    }
    static public ProxyEntry create_smtp_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, SMTP );
    }

    public String getHost()
    {
        return host;
    }
    public String getConfigLine()
    {        
        return getProtokollStr() + "\t" + getLocalPort() + "\t" + getHost() + "\t" + getRemotePort() + "\n";
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public int getRemotePort()
    {
        return remotePort;
    }

    public int getProtokoll()
    {
        return protokoll;
    }
    public String getProtokollStr()
    {
        return prt_list[protokoll];
    }

    public int getInstanceCnt()
    {
        return instanceCnt;
    }
    public void incInstanceCnt()
    {
        instanceCnt++;
    }
    public void decInstanceCnt()
    {
        instanceCnt--;
    }
/*    
    static public ProxyEntry create_imap_entry( String _host, int l, int r )
    {
        return new ProxyEntry( _host, l, r, IMAP );
    }
  */          
}
