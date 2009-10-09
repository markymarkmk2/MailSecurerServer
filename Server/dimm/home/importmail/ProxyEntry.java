/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.hibernate.Proxy;

/**
 *
 * @author mw
 */
public class ProxyEntry implements WorkerParentChild
{
    private int instanceCnt;
    boolean finish;

    Proxy proxy;
    public ProxyEntry( Proxy p )
    {
        proxy = p;
        instanceCnt = 0;
        finish = false;
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
        return proxy.getType() + "\t" + proxy.getLocalPort() + "\t" + proxy.getRemoteServer() + "\t" + proxy.getRemotePort() + "\n";
    }

    public Proxy get_proxy()
    {
        return proxy;
    }

    @Override
    public void finish()
    {
        finish = true;
    }
    public boolean get_finish()
    {
        return finish;
    }

    @Override
    public void idle_check()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void run_loop()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean is_started()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean is_finished()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object get_db_object()
    {
        return this;
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }



}
