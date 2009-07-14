/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import home.shared.hibernate.Proxy;

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
        setType(p.getType());
        setMandant(p.getMandant());
        setDiskArchive(p.getDiskArchive());
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
        return getType() + "\t" + getLocalPort() + "\t" + getRemoteServer() + "\t" + getRemotePort() + "\n";
    }



}
