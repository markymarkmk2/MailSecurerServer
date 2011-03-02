/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.Updater.UpdateWorker;
import dimm.home.mailarchiv.Main;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author mw
 */
public class Update extends AbstractCommand
{
    public Update()
    {
        super("Update");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String command = pt.GetString("CMD:");
        boolean ok = false;

        if (command.compareTo("check_ver") == 0)
        {
            UpdateWorker updw = Main.get_control().get_update_worker();

            String remote_ver = updw.get_remote_ver();
            String local_ver = updw.get_local_ver();


            if (remote_ver == null || remote_ver.length() == 0 || UpdateWorker.get_ver_code(remote_ver) == 0)
            {
                answer = "1: Cannot detect remote version";
            }
            else
            {
                answer = "0: LV:" + local_ver + " LNV:" + UpdateWorker.get_ver_code(local_ver) + " RV:" + remote_ver + " RNV:" + UpdateWorker.get_ver_code(remote_ver);
            }

            return true;
        }
        if (command.compareTo("update") == 0)
        {
            UpdateWorker updw = Main.get_control().get_update_worker();
            updw.check_updates();

            if (updw.update_in_progress())
            {
                answer = "0: ok";
            }
            else
            {
                answer = "1: Update was not started";
            }
                
        }


        answer = "1: Unknown subcommand: " + data;
        return false;
    }
}