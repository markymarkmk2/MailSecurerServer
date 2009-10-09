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
    public boolean is_started();
    public boolean is_finished();
    public Object get_db_object();
    public String get_task_status_txt();

}
