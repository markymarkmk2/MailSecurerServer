/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.hibernate.Proxy;
import java.io.IOException;
import java.net.ServerSocket;
import org.apache.commons.lang.builder.EqualsBuilder;

/**
 *
 * @author mw
 */
public class ProxyEntry implements WorkerParentChild
{
    private int instanceCnt;
    boolean finish;
    boolean started;
    ProxyConnection conn;
    ServerSocket ss;

    public void set_started( boolean started )
    {
        this.started = started;
    }

    @Override
    public boolean is_started()
    {
        return started;
    }

    Proxy proxy;
    public ProxyEntry( Proxy p )
    {
        proxy = p;
        instanceCnt = 0;
        finish = false;
        conn = null;
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
        if (conn != null)
        {
            conn.closeConnections();
        }
        if (ss != null)
        {
            try
            {
                ss.close();
            }
            catch (IOException iOException)
            {
            }
        }
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
    public boolean is_finished()
    {
        return finish;
    }

    @Override
    public Object get_db_object()
    {
        return proxy;
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

    void set_connection( ProxyConnection aThis )
    {
        conn = aThis;
    }

    @Override
    public boolean is_same_db_object( Object db_object )
    {
        return EqualsBuilder.reflectionEquals( proxy, db_object);
    }

    public void set_server_sock( ServerSocket _ss )
    {
        ss = _ss;
    }



}
