/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import home.shared.CS_Constants;
import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCMimeMail;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.swing.SwingWorker;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

/**
 *
 * @author mw
 */
public class IndexJobEntry  implements Runnable
{
    private MandantContext m_ctx;
    String unique_id;
    int da_id;
    int ds_id;
    private DiskSpaceHandler index_dsh;
    RFCGenericMail msg;
    boolean delete_after_index;
    boolean skip_account_match;
    IndexManager ixm;
    Document doc;
    IndexWriter writer;
    RFCMimeMail mime_msg;

    public IndexJobEntry( IndexManager ixm, MandantContext m_ctx, String unique_id, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean delete_after_index, boolean skip_account_match )
    {
        this.ixm = ixm;
        this.m_ctx = m_ctx;
        this.unique_id = unique_id;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.index_dsh = index_dsh;
        this.msg = msg;
        this.delete_after_index = delete_after_index;
        this.skip_account_match = skip_account_match;
        doc = null;
        writer = null;
        mime_msg = null;

    }
    public void close()
    {
        doc = null;
        writer = null;
        mime_msg = null;
        msg = null;
    }

    @Override
    public void run()
    {
        try
        {
            synchronized( ixm )
            {
                mime_msg = ixm.load_mail_file(msg);
            }
            if (mime_msg == null)
                throw new Exception( "Cannot load message" );

            boolean ret = handle_pre_index(mime_msg);

            if (ret)
            {
                if (LogManager.get_debug_lvl() >= 8)
                {
                    LogManager.debug_msg(8, "Adding to write queue, size is: " + index_dsh.get_async_index_writer().get_write_queue_size());
                }
                index_dsh.get_async_index_writer().add_to_write_queue(this);                
                return;
            }

        }
        catch (Exception ex)
        {
            Logger.getLogger(IndexJobEntry.class.getName()).log(Level.SEVERE, null, ex);
        }
        close();
    }

    boolean load_mail_file(boolean parallel)
    {
        if (parallel)
        {
            SwingWorker sw = new SwingWorker()
            {
                @Override
                protected Object doInBackground() throws Exception
                {
                    try
                    {
                        return ixm.load_mail_file(msg);
                    }
                    catch (Exception ex)
                    {
                        LogManager.log(Level.SEVERE, "Error occured while loading message " + unique_id + ": ", ex);
                        mime_msg = null;
                    }
                    return null;
                }
            };
            sw.execute();

             // 20 MINUTES
            try
            {
                Object o = sw.get(1200, TimeUnit.MINUTES);
                if (o != null && o instanceof RFCMimeMail)
                {
                    mime_msg = (RFCMimeMail) o;
                }
            }
            catch (Exception interruptedException)
            {
                mime_msg = null;
            }

            sw = null;
        }
        else
        {
            try
            {
                synchronized( ixm )
                {
                    mime_msg = ixm.load_mail_file(msg);
                }
            }
            catch (MessagingException messagingException)
            {
            }
            catch (IOException iOException)
            {
            }
        }

        if (mime_msg == null)
        {
            LogManager.err_log("Error occured while loading message " + unique_id + ": ");
            return false;
        }

        return true;
    }
   
    boolean handle_index()
    {
        boolean parallel_index = Main.get_bool_prop(GeneralPreferences.INDEX_MAIL_IN_BG, true);
        return handle_index(parallel_index);
    }

    boolean handle_index(boolean parallel_index)
    {
        ixm.setStatusTxt(Main.Txt("Indexing mail file") + " " + unique_id);

        // LOAD MAIL IN AN OWN THREAD, WE CAN CATCH OOMs BETTER
        if (!parallel_index)
        {
            int max_load_tries = 3;

            while (max_load_tries > 0)
            {
                if (load_mail_file(parallel_index))
                    break;

                max_load_tries--;
                LogicControl.sleep(1000);
            }
            if (max_load_tries <= 0)
            {
                LogManager.log(Level.SEVERE, "Could not load message " + unique_id );
                close();
                return false;
            }


            boolean ret = handle_pre_index(mime_msg);
            if (ret)
            {
                ret = handle_post_index();
            }
            close();

            return ret;
        }

        try
        {
            //LogManager.debug_msg(8, "Adding to extract queue, size is: " + index_dsh.get_async_index_writer().get_extract_queue_size());

            ixm.execute(this);
        }
        catch (Exception ex)
        {
            Logger.getLogger(IndexJobEntry.class.getName()).log(Level.SEVERE, null, ex);
            close();
            return false;
        }

        return true;
    }

    boolean handle_pre_index( RFCMimeMail mime_mail)
    {
        doc = new Document();
        DocumentWrapper docw = new DocumentWrapper(doc, unique_id);

        try
        {
            // DO THE REAL WORK (EXTRACT AND INDEX)
            boolean ok = ixm.index_mail_file(m_ctx, unique_id, da_id, ds_id, msg, mime_mail, docw, delete_after_index, skip_account_match);

            // IF INDEX GIVES FALSE, WE DONT WANT THIS MAIL IN INDEX (EXCLUDE, WRONG DOMAIN ETC.)
            if (!ok)
                return false;


            writer = index_dsh.get_write_index();
            if (writer == null)
            {
                writer = index_dsh.open_write_index();
            }

            // DETECT LANG OF INDEX AND PUT INTO LUCENE
            String lang = ixm.get_lang_by_analyzer(writer.getAnalyzer());
            doc.add(new Field(CS_Constants.FLD_LANG, lang, Field.Store.YES, Field.Index.NOT_ANALYZED));

            byte[] hash = msg.get_hash();
            if (hash != null)
            {
                String txt_hash = new String(Base64.encodeBase64(hash));
                doc.add(new Field(CS_Constants.FLD_HASH, txt_hash, Field.Store.YES, Field.Index.NOT_ANALYZED));

                index_dsh.add_hash_entry( txt_hash, doc, ds_id );
            }
            return true;
        }
        catch (Exception ex)
        {
            LogManager.log(Level.WARNING, "Error occured while indexing message " + unique_id + ": ", ex);
            //ex.getStackTrace()[0];
        }
        return false;
    }

    boolean handle_post_index()
    {
        try
        {
            // SHOVE IT RIGHT OUT!!!
            synchronized (index_dsh.idx_lock)
            {
                writer.addDocument(doc);
            }


            // CLOSE ALL PENDING READERS, WE STARTED WITH A CLOSED DOCUMENT
            List field_list = doc.getFields();
            for (int i = 0; i < field_list.size(); i++)
            {
                if (field_list.get(i) instanceof Field)
                {
                    Field field = (Field) field_list.get(i);
                    Reader rdr = field.readerValue();
                    if (rdr != null)
                    {
                        rdr.close();
                    }
                }
            }


            if (delete_after_index)
            {
                msg.delete();
            }
            close();
            return true;
        }
        catch (Exception ex)
        {
            LogManager.log(Level.WARNING, "Error occured while indexing message " + unique_id + ": ", ex);
        }
        close();
        return false;
    }

}
