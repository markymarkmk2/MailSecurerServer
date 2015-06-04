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
import dimm.home.vault.ExportContext;
import home.shared.Utilities.ParseToken;
import java.io.File;

/**
 *
 * @author Administrator
 */


public class Export extends AbstractCommand
{
   
   
    public Export()
    {
        super("export");        
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
        ExportContext ectx = m_ctx.getEctx();

        try
        {
            if (cmd.compareTo("start") == 0)
            {
                String type = pt.GetString("TY:");
                String pathStr = pt.GetString("PA:");
                File path = new File(pathStr);
                if (!path.exists()) {
                     answer = "2: invalid path: " + pathStr;
                    return true;
                }                
                
                int da_id = (int)pt.GetLongValue("DA:");

                if (ectx != null && ectx.isBusy())
                {
                    answer = "2: " + Main.Txt("Export_is_already_running");
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
                        ectx = new ExportContext(m_ctx, da_id, ds_id, path);

                        m_ctx.setEctx(ectx);
                        ectx.start();
                        

                        answer = "0: ok";
                    }
                }
                else if (type.compareTo("one_da") == 0)
                {
                    ectx = new ExportContext(m_ctx, da_id, path);

                    m_ctx.setEctx(ectx);
                    ectx.start();
                    
                    answer = "0: ok";
                }
                else if (type.compareTo("all") == 0)
                {
                    ectx = new ExportContext(m_ctx, path);

                    m_ctx.setEctx(ectx);
                    ectx.start();
                    
                    answer = "0: ok";
                }
                else
                {
                    answer = "3: unknown subtype: " + type;
                }
            }
            else if (cmd.compareTo("check") == 0)
            {
                if (ectx == null)
                {
                     answer = "2: " + Main.Txt("Export_was_not_started");
                     return true;
                }
                
                answer = "0: " + ectx.get_statistics_string();
                return true;
            }
            else if (cmd.compareTo("pause") == 0)
            {
                ectx.set_pause( true );
                answer = "0: paused";
                return true;
            }
            else if (cmd.compareTo("resume") == 0)
            {
                ectx.set_pause( false );
                answer = "0: resuming";
                return true;
            }
            else if (cmd.compareTo("abort") == 0)
            {
                ectx.set_abort( true);
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
