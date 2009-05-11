/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.hibernate.Proxy;

/**
 *
 * @author mw
 */
public class ProxyEntry extends Proxy
{
    private int instanceCnt;

    public ProxyEntry( Proxy p )
    {
        setLocalPort(p.getLocalPort());
        setLocalServer(p.getLocalServer());
        setRemotePort(p.getRemotePort());
        setRemoteServer(p.getRemoteServer());
        setProtokoll(p.getProtokoll());
        setFlags(p.getFlags());
        instanceCnt = 0;
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
    public String getConfigLine()
    {
        return getProtokoll() + "\t" + getLocalPort() + "\t" + getRemoteServer() + "\t" + getRemotePort() + "\n";
    }



}
