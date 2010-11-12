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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.mail.MessagingException;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

/**
 *
 * @author mw
 */
public class IndexJobEntry
{
    private MandantContext m_ctx;
    String unique_id;
    int da_id;
    int ds_id;
    private DiskSpaceHandler index_dsh;
    RFCGenericMail msg;
    boolean delete_after_index;
    boolean skip_account_match;
    final IndexManager ixm;
    Document doc;
    IndexWriter writer;
    RFCMimeMail mime_msg;
    boolean sequential_ret;


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
        sequential_ret = false;
    }
    public void close()
    {
        doc = null;
        writer = null;
        mime_msg = null;
        msg = null;
    }

    
    public void index_mail_parallel()
    {
        try
        {

            Exception ex = null;
            boolean ret = handle_pre_index(mime_msg);

            if (ret)
            {
                // IF START IN BACKGROUND FAILS, WE START IN FG
                Future<IndexJobEntry> result = index_dsh.execute_write(this);
                if (result == null)
                {
                    handle_post_index();
                }
            }

        }
        catch (Exception ex)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "IndexJobEntry::run fails", ex);
            close();
        }
        
    }

    void load_mail_file(boolean parallel)
    {
        Exception ex = null;

        if (parallel)
        {
            try
            {
                Future<IndexJobEntry> result = ixm.execute_load(this);
                result.get();
            }
            catch (ExecutionException ex1)
            {
                ex = ex1;
            }
            catch (InterruptedException ex1)
            {
                ex = ex1;
            }
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
                ex = messagingException;
            }
            catch (IOException iOException)
            {
                ex = iOException;
            }
       }

        if (mime_msg == null)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Error occured while loading message " + unique_id, ex);
            
        }
    }
   
    boolean handle_index()
    {
        boolean parallel_index = Main.get_bool_prop(GeneralPreferences.INDEX_MAIL_IN_BG, true);
        return handle_index(parallel_index);
    }
    void index_mail_sequential()
    {
        int max_load_tries = 3;

        while (max_load_tries > 0)
        {
            load_mail_file(false);

            if (mime_msg != null)
                break;

            max_load_tries--;
            LogicControl.sleep(1000);
        }
        if (max_load_tries <= 0)
        {
            LogManager.msg_index(LogManager.LVL_ERR,  "Could not load message " + unique_id );
            close();
            sequential_ret = false;
            return;
        }


        boolean ret = handle_pre_index(mime_msg);
        if (ret)
        {
            ret = handle_post_index();
        }
        close();

        sequential_ret = ret;
    }

    boolean handle_index(boolean parallel_index)
    {
        ixm.setStatusTxt(Main.Txt("Indexing mail file") + " " + unique_id);

        if (!parallel_index)
        {
            Future<IndexJobEntry> result;
            try
            {
                result = ixm.execute_single_run(this);
            }
            catch (Exception ex)
            {
                LogManager.msg_index(LogManager.LVL_ERR,  "handle_single_index failed", ex);
                close();
                return false;
            }

            while (!result.isDone() && !result.isCancelled())
            {
                try
                {
                    result.get(1000, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException interruptedException)
                {
                }
                catch (ExecutionException executionException)
                {
                }
                catch (TimeoutException timeoutException)
                {
                }
                if (ixm.isShutdown())
                {
                    try
                    {
                        result.cancel(true);
                    }
                    catch (Exception e)
                    {
                    }
                    break;
                }
            }

            return sequential_ret;
        }

        try
        {
            //LogManager.debug_msg(8, "Adding to extract queue, size is: " + index_dsh.get_async_index_writer().get_extract_queue_size());
            load_mail_file( true );

            if (mime_msg != null)
            {
                ixm.execute_run(this);
            }
        }
        catch (Exception ex)
        {
            LogManager.msg_index(LogManager.LVL_ERR,  "handle_index failed", ex);
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
            LogManager.msg_index(LogManager.LVL_WARN,  "Error occured while indexing message " + unique_id + ": ", ex);
            //ex.getStackTrace()[0];
        }
        return false;
    }

    public boolean handle_post_index()
    {
        try
        {
            // SHOVE IT RIGHT OUT!!!
            synchronized (index_dsh.idx_lock)
            {

                //LogManager.msg_index(LogManager.LVL_WARN,  "Not adding to index: " + unique_id);
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
                if (IndexManager.index_buffer_test)
                    LogManager.msg_index(LogManager.LVL_WARN, "Skipping delete because of test: " + unique_id);
                else
                    msg.delete();
            }
            
            return true;
        }
        catch (Exception ex)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error occured while indexing message " + unique_id + ": ", ex);
        }
        finally
        {
            close();
        }

        return false;
    }

    int get_ma_id()
    {
        return m_ctx.getMandant().getId();
    }

}
