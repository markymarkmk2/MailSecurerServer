/*
 * WorkerParent.java
 *
 * Created on 10. Oktober 2007, 11:36
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxy;

/**
 *
 * @author Administrator
 */
public abstract class WorkerParent
{
    private String name;
    private boolean shutdown;
    private String statusTxt = "Startup";
    private boolean goodState;
    
    /** Creates a new instance of WorkerParent */
    public WorkerParent(String _name)
    {
        name = _name;
        setGoodState(true);
    }
    
 
    public String getName()
    {
        return name;
    }
    abstract public boolean initialize();
    abstract public boolean start_run_loop();
    abstract public boolean check_requirements(StringBuffer sb);

    public boolean isShutdown()
    {
        return shutdown;
    }

    public void setShutdown(boolean shutdown)
    {
        this.shutdown = shutdown;
    }

    public String getStatusTxt()
    {
        return statusTxt;
    }

    public void setStatusTxt(String statusTxt)
    {
        this.statusTxt = statusTxt;
    }

    public boolean isGoodState()
    {
        return goodState;
    }

    public void setGoodState(boolean goodState)
    {
        this.goodState = goodState;
    }
    
    
    
    
    
}
