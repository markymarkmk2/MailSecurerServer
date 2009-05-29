/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

/**
 *
 * @author mw
 */
public interface StatusHandler
{
    StatusEntry status = new StatusEntry();

    public String get_status_txt();
    public int get_status_code();


}
