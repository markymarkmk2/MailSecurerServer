/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.vault.ReIndexContext;

/**
 *
 * @author Administrator
 */


public class ReIndex extends AbstractCommand
{
   
   
    public ReIndex()
    {
        super("reindex");        
    }

    // reindex CMD:start TY:one_da MA:1 DA:2

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        String cmd = pt.GetString("CMD:");
        
        long m_id = pt.GetLongValue("MA:");


        MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
        ReIndexContext rctx = m_ctx.getRctx();

        try
        {
            if (cmd.compareTo("start") == 0)
            {
                String type = pt.GetString("TY:");
                int da_id = (int)pt.GetLongValue("DA:");

                if (rctx != null && rctx.isBusy())
                {
                    answer = "2: " + Main.Txt("Reindex_is_already_running");
                    return true;
                }
                if (da_id == 0)
                {
                    answer = "2: invalid diskarchive: " + data;
                    return true;
                }

                if (type.compareTo("one_ds") == 0)
                {
                    int ds_id = (int)pt.GetLongValue("DS:");
                    if (ds_id == 0)
                    {
                        answer = "2: invalid diskspace: " + data;
                    }
                    else
                    {
                        rctx = new ReIndexContext(m_ctx, da_id, ds_id);

                        rctx.start();
                        m_ctx.setRctx(rctx);

                        answer = "0: ok";
                    }
                }
                else if (type.compareTo("one_da") == 0)
                {
                    rctx = new ReIndexContext(m_ctx, da_id);

                    rctx.start();
                    m_ctx.setRctx(rctx);
                    answer = "0: ok";
                }
                else
                {
                    answer = "3: unknown subtype: " + type;
                }
            }
            else if (cmd.compareTo("check") == 0)
            {
                if (rctx == null)
                {
                     answer = "2: " + Main.Txt("Reindex_was_not_started");
                     return true;
                }
                
                answer = "0: " + rctx.get_statistics_string();
                return true;
            }
            else if (cmd.compareTo("pause") == 0)
            {
                rctx.set_pause( true );
                answer = "0: paused";
                return true;
            }
            else if (cmd.compareTo("resume") == 0)
            {
                rctx.set_pause( false );
                answer = "0: resuming";
                return true;
            }
            else if (cmd.compareTo("abort") == 0)
            {
                rctx.set_abort( true);
                answer = "0: aborting";
                return true;
            }
            else
            {
                answer = "1: unknown subcommand";
                return true;
            }
        }
        catch (Exception exc)
        {
            answer = "4: " + exc.getMessage();
        }


        return true;
    }        
}
