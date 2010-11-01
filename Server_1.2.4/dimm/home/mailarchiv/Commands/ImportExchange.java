/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;


import com.microsoft.schemas.exchange.services._2006.types.BaseFolderIdType;
import dimm.home.exchange.ExchangeImportServer;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskVault;
import home.shared.Utilities.ParseToken;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */


public class ImportExchange extends AbstractCommand
{

    
    /** Creates a new instance of HelloCommand */
    public ImportExchange()
    {
        super("import_exchange_folder");
        
    }
    

    @Override
    public boolean do_command(String data)
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        try
        {
            int m_id = (int)pt.GetLongValue("MA:");
            int da_id = (int)pt.GetLongValue("DA:");

            String user = pt.GetString("US:");
            String domain = pt.GetString("DO:");
            String server = pt.GetString("SV:");
            String pwd = pt.GetString("PW:");

            String mode = pt.GetString("MD:");

            if (mode.compareTo("folder") == 0)
            {
                String folder_xml = pt.GetString("FD:");



                ArrayList<BaseFolderIdType>folder_list = null;

                Object o = ParseToken.DeCompressObject(folder_xml);
                if (o instanceof ArrayList)
                {
                    folder_list = (ArrayList<BaseFolderIdType>) o;
                }



                // GET STRUCTS FROM ARGS
                MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                Mandant m = m_ctx.getMandant();
                DiskArchive da = m_ctx.get_da_by_id(da_id);
                DiskVault dv = m_ctx.get_vault_by_da_id(da_id);

                // CHECK FOR SPACE
                if (!dv.has_sufficient_space())
                {
                    answer = "2: " + Main.Txt("Cannot_import_mail,_not_enough_space") + " " + da_id;
                    return true;
                }

                ExchangeImportServer.register_import( m_ctx, da, folder_list, user, pwd, domain, server );


                // YEEHAW, WE'RE DONE
                answer = "0: ok";
            }
            if (mode.compareTo("users") == 0)
            {
                long ac_id = pt.GetLongValue("AC:");

                String folder_xml = pt.GetString("FD:");
                ArrayList<BaseFolderIdType>folder_list = null;

                Object o = ParseToken.DeCompressObject(folder_xml);
                if (o instanceof ArrayList)
                {
                    folder_list = (ArrayList<BaseFolderIdType>) o;
                }

                String user_xml = pt.GetString("US:");
                ArrayList<String>user_list = null;

                Object u = ParseToken.DeCompressObject(user_xml);
                if (u instanceof ArrayList)
                {
                    user_list = (ArrayList<String>) o;
                }

                boolean user_folders = pt.GetBoolean("UF:");



                // GET STRUCTS FROM ARGS
                MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
                Mandant m = m_ctx.getMandant();
                DiskArchive da = m_ctx.get_da_by_id(da_id);
                DiskVault dv = m_ctx.get_vault_by_da_id(da_id);

                // CHECK FOR SPACE
                if (!dv.has_sufficient_space())
                {
                    answer = "2: " + Main.Txt("Cannot_import_mail,_not_enough_space") + " " + da_id;
                    return true;
                }

                ExchangeImportServer.register_import( m_ctx, da, ac_id, folder_list, user_list, user_folders );


                // YEEHAW, WE'RE DONE
                answer = "0: ok";
            }
        }
        catch (Exception e)
        {
            LogManager.printStackTrace(e);
            answer = "9: " + e.getMessage();
        }
                
        return true;
    }        
}
