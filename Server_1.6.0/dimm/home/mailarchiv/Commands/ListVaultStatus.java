/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Commands;

import dimm.home.hibernate.HParseToken;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskSpaceInfo;
import home.shared.Utilities.ParseToken;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.DiskSpace;
import home.shared.hibernate.Mandant;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;

/**
 *
 * @author mw
 */
public class ListVaultStatus extends AbstractCommand
{

    public ListVaultStatus()
    {
        super("ListVaultData");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts(data);

        ParseToken pt = new ParseToken(opt);
        StringBuffer result_list = new StringBuffer();

        String command = pt.GetString("CMD:");
        if (command.compareTo("status") == 0)
        {
            int m_id = (int) pt.GetLongValue("MA:");
            int da_id = (int) pt.GetLongValue("DA:");

            MandantContext m_ctx = Main.get_control().get_mandant_by_id(m_id);
            Mandant mandant = m_ctx.getMandant();



            // PRUEFE FÜR ALLE ARCHIVE DIESES MANDANTEN
            for (Iterator<DiskArchive> it = mandant.getDiskArchives().iterator(); it.hasNext();)
            {
                DiskArchive da = it.next();
                if (da.getId() != da_id)
                {
                    continue;
                }
                for (Iterator<DiskSpace> it1 = da.getDiskSpaces().iterator(); it1.hasNext();)
                {
                    DiskSpace ds = it1.next();
                    DiskSpaceHandler dsh = m_ctx.get_dsh(ds.getId());

                    // NO DATA ONLY DS
                    if (!dsh.is_index())
                        continue;

                    IndexWriter wr = dsh.get_write_index();
                    int docs = 0;
                    if (wr != null)
                    {
                        try
                        {
                            docs = wr.numDocs();
                        }
                        catch (IOException iOException)
                        {
                        }
                    }
                    else
                    {
                        try
                        {
                            IndexReader rdr = dsh.create_read_index();
                            docs = rdr.numDocs();
                            rdr.close();
                        }
                        catch (Exception e)
                        {
                        }
                    }
                    DiskSpaceInfo dsi = dsh.getDsi();
                    if (dsi != null)
                    {
                        File fs = new File( ds.getPath() );
                        long free_space = fs.getFreeSpace();
                        long total_space = fs.getTotalSpace();
                        long last_mod = dsi.getLastEntryTS();

                        String line = "TP:DS ID:" + ds.getId() + " CNT:" + docs + " CAP:" + dsi.getCapacity() + " FS:" + free_space + " TS:" + total_space + " LM:" + last_mod + "\n";

                        result_list.append(line);
                    }
                }


                String cxml = HParseToken.BuildCompressedString(result_list);

                answer = "0: " + cxml;
                return true;
            }
            answer = "1: Unknown diskarchive";
            return true;
        }
        answer = "2: Unknown subcommand: " + data;
        return true;
    }
}
