/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;


import dimm.home.hiber_dao.BackupDAO;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.serverconnect.DimmCommand;
import dimm.home.vault.BackupScript;
import home.shared.hibernate.Backup;

/**
 *
 * @author Administrator
 */


public class BackupCommand extends AbstractCommand
{

    
    /** Creates a new instance of HelloCommand */
    public BackupCommand()
    {
        super("backup");
        
    }
    

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String sub_command = pt.GetString("CMD:");


        try
        {
            if (sub_command.equals("start"))
            {
                long m_id = pt.GetLongValue("MA:");
                long da_id = pt.GetLongValue("DA:");
                long bs_id = pt.GetLongValue("BS:");
                MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                Backup backup = m_ctx.get_backup_by_id( bs_id );
                if (backup != null)
                {
                    BackupDAO dao = new BackupDAO();
                    dao.refresh(backup);
                }
                BackupScript script = (BackupScript)Main.get_control().get_ba_server().get_child(backup);
                if (script == null)
                {
                    answer = "3: invalid script";
                }
                else
                {
                    if (script.start_backup())
                    {
                        answer = "0: ok";
                    }
                    else
                    {
                        answer = "2: start failed";
                    }
                }
            }
            else if (sub_command.equals("abort"))
            {
                long m_id = pt.GetLongValue("MA:");
                long da_id = pt.GetLongValue("DA:");
                long bs_id = pt.GetLongValue("BS:");
                MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                Backup backup = m_ctx.get_backup_by_id( bs_id );
                BackupScript script = (BackupScript)Main.get_control().get_ba_server().get_child(backup);
                script.abort_backup();
                answer = "0: ok";
            }
            else if (sub_command.equals("test"))
            {
                String ip = pt.GetString("AG:");
                int port = (int)pt.GetLongValue("PO:");

                DimmCommand sync_cmd = new DimmCommand( (int)Main.get_long_prop(GeneralPreferences.SYNCSRV_PORT, 11170 ) );
                sync_cmd.connect();
                String ret = sync_cmd.send_cmd( "list_agent_features " + ip + " " + port  );
                sync_cmd.disconnect();

                if (ret != null)
                {
                    answer = ret;
                }
                else
                {
                    answer = "1: no answer";
                }
            }
            else if (sub_command.equals("status"))
            {
                long m_id = pt.GetLongValue("MA:");
                long da_id = pt.GetLongValue("DA:");
                long bs_id = pt.GetLongValue("BS:");
                MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                Backup backup = m_ctx.get_backup_by_id( bs_id );
                BackupScript script = (BackupScript)Main.get_control().get_ba_server().get_child(backup);
                if (script != null)
                {
                    String status = script.get_job_status();
                    if (status != null)
                        answer = status;
                    else
                        answer = "2: cannot get status";
                }
            }
            else
            {
                answer = "1: unknown subcommand";
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            answer = "9: " + e.getMessage();
        }
                
        return true;
    }        
}
