/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailproxyclient;

/**
 *
 * @author Administrator
 */
public class ProxyEntry
{
    
    private String host;
    private int localPort;
    private int remotePort;
    private String protokoll;
    private int instanceCnt;
    
    ProxyEntry( String _host, long l, long r, long ic, String p )
    {
        host = _host;
        localPort =  (int)l;
        remotePort =  (int)r;
        instanceCnt = (int)ic;
        protokoll = p;
    }
  
    String getHost()
    {
        return host;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public int getRemotePort()
    {
        return remotePort;
    }

    public String getProtokoll()
    {
        return protokoll;
    }
   
    public int getInstanceCnt()
    {
        return instanceCnt;
    }   
    public void setInstanceCnt(int i)
    {
        instanceCnt = i;
    }   
    
    public boolean is_equal( ProxyEntry pe )
    {
        if (pe.getLocalPort() != getLocalPort())
            return false;
        if (pe.getRemotePort() != getRemotePort())
            return false;
        
        if (!pe.getHost().equals(host))
            return false;
        if (!pe.getProtokoll().equals(getProtokoll()))
            return false;
        
        return true;
    }
        
}
