/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

/**
 *
 * @author mw
 */
public interface WorkerParentChild
{
    public void idle_check();
    public void finish();
    public void run_loop();

}
