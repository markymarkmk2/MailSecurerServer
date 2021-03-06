/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hiber_dao.DiskSpaceDAO;
import dimm.home.index.IndexManager;
import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.DiskSpace;
import home.shared.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Notification.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ParallelMultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searchable;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author mw
 */
public class DiskVault implements Vault, StatusHandler
{


    MandantContext context;
    DiskArchive disk_archive;
    String password;
    ArrayList<DiskSpaceHandler> dsh_list;



    public DiskVault( MandantContext _context, DiskArchive da )
    {
        disk_archive = da;
        context = _context;
        password = context.getPrefs().get_password();

        dsh_list = new ArrayList<DiskSpaceHandler>();
        Set<DiskSpace> dss = disk_archive.getDiskSpaces();

        Iterator<DiskSpace> it = dss.iterator();

        while (it.hasNext())
        {
            DiskSpace ds = it.next();
            DiskSpaceHandler dsh = new DiskSpaceHandler( context, ds);

            if (dsh.is_disabled())
                continue;

            dsh_list.add( dsh );
        }
    }

    public DiskArchive get_da()
    {
        return disk_archive;
    }
    public ArrayList<DiskSpaceHandler>get_dsh_list()
    {
        return dsh_list;
    }
    public DiskSpaceHandler get_dsh( long ds_idx)
    {
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler diskSpaceHandler = dsh_list.get(i);
            if (diskSpaceHandler.getDs().getId() == ds_idx)
                return diskSpaceHandler;
        }

        return null;
    }

    
    DiskSpaceHandler get_next_active_data_diskspace(int index)
    {
        return get_next_active_diskspace(index, true);
    }

    DiskSpaceHandler get_next_active_index_diskspace(int index )
    {
        return get_next_active_diskspace(index, false);
    }
    
    private DiskSpaceHandler get_next_active_diskspace(int index, boolean is_data )
    {

        Iterator<DiskSpaceHandler> it = dsh_list.iterator();

        while (it.hasNext())
        {
            DiskSpaceHandler dsh = it.next();

            if (dsh.is_disabled())
                continue;
            
            DiskSpace ds = dsh.getDs();
            if ( ds.getStatus().compareTo( CS_Constants.DS_FULL) == 0 ||
                 ds.getStatus().compareTo( CS_Constants.DS_ERROR) == 0 ||
                 ds.getStatus().compareTo( CS_Constants.DS_OFFLINE) == 0)
                continue;

            // CHECK FOR CORRECT MODE
            if (is_data && !dsh.test_flag(ds, CS_Constants.DS_MODE_DATA))
                continue;

            if (!is_data && !dsh.test_flag(ds, CS_Constants.DS_MODE_INDEX))
                continue;

            File dsf = new File( ds.getPath() );
            if ( !dsf.exists() )
            {
                LogManager.msg_archive( LogManager.LVL_ERR, Main.Txt("DiskSpace_was_not_found") + ": " + dsf.getAbsolutePath());
                continue;
            }

            if (index > 0)
            {
                index--;
                continue;
            }

            // GOT IT
            return dsh;
        }

        // NADA
        return null;
    }


    @Override
    public String get_status_txt()
    {
        return status.get_status_txt();
    }

    @Override
    public int get_status_code()
    {
        return status.get_status_code();
    }

    @Override
    public boolean archive_mail( RFCGenericMail msg, MandantContext context, DiskArchive diskArchive, boolean background_index ) throws ArchiveMsgException, VaultException, IndexException
    {
        boolean ret = false;
        try
        {
            ret = low_level_archive_mail(msg, context, diskArchive, background_index);
        }
        catch (IOException ex)
        {
            // RETRY IN CASE OF ERROR ONCE, MAYBE DISKSPACE IS FULL
            // IF MAIL IS BIGGER THAN DISKSPACE WE CHOKE
            try
            {
                ret = low_level_archive_mail(msg, context, diskArchive, background_index);
            }
            catch (IOException _ex)
            {
                status.set_status(StatusEntry.BUSY, toString() + ": " + Main.Txt("Error_while_writing_to_disk") + ": " +  _ex.getMessage());
                LogManager.msg_archive( LogManager.LVL_ERR,  status.get_status_txt() );
                throw new ArchiveMsgException( status.get_status_txt()   );
            }
        }
        return ret;
    }

    @Override
    public String toString()
    {
        return Main.Txt("DiskArchive") + " " + get_da().getName();
    }


    public DiskSpaceHandler open_dsh( DiskSpaceHandler dsh, long free_space) throws VaultException, IndexException, CorruptIndexException, IOException
    {
        try
        {
            if (!dsh.is_open())
            {
                dsh.open();
            }

            // OPEN WRITE_INDEX
            dsh.open_write_index();
            
        }
        catch (Exception vaultException)
        {
            if (dsh.getDs().getStatus().compareTo(CS_Constants.DS_EMPTY) == 0)
            {
                dsh.create();
                dsh.open();
                // OPEN WRITE_INDEX
                dsh.open_write_index();
            }
            else
            {
                status.set_status(StatusEntry.BUSY, toString() + ": " + Main.Txt("Cannot_open_active_diskspace") + " " + dsh.getDs().getPath() + ": " +  vaultException.getMessage());
                LogManager.msg_archive( LogManager.LVL_ERR, status.get_status_txt() );
                if (vaultException.getCause() != null)
                    LogManager.msg_archive( LogManager.LVL_ERR, "Cause: " + vaultException.getCause().getLocalizedMessage() );
                
                throw new VaultException( vaultException.getMessage() );
            }
        }


        while (!dsh.checkCapacity(free_space))
        {
            DiskSpace ds = dsh.getDs();

            status.set_status(StatusEntry.BUSY, toString() + ": " + Main.Txt("DiskSpace") + " <" + ds.getPath() + "> " + Main.Txt("is_full") );
            Notification.throw_notification( disk_archive.getMandant(), Notification.NF_INFORMATIVE, status.get_status_txt() );
            ds.setStatus( CS_Constants.DS_FULL);

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.update(ds);

            DiskSpaceHandler new_dsh = get_next_active_diskspace( 0, dsh.is_data() );

            if (new_dsh == null)
            {
                status.set_status(StatusEntry.ERROR, toString() + ": " + Main.Txt("No_DiskSpace_found_for_type") + " " + Main.Txt((dsh.is_data() ? "data" : "index")));
                Notification.throw_notification( disk_archive.getMandant(), Notification.NF_ERROR, status.get_status_txt() );
                throw new VaultException(status.get_status_txt() );
            }
            dsh = new_dsh;

            try
            {
                if (!dsh.is_open())
                {
                    dsh.open();
                    // OPEN WRITE_INDEX
                    dsh.open_write_index();
                }
            }
            catch (VaultException vaultException)
            {
                LogManager.msg_archive( LogManager.LVL_ERR, toString() + ": " + Main.Txt("Cannot_open_active_diskspace") + " " + dsh.getDs().getPath(), vaultException);
                dsh.create();
                
                dsh.open();
                // OPEN WRITE_INDEX
                dsh.open_write_index();
            }
        }
        return dsh;
    }

    

    boolean low_level_archive_mail( RFCGenericMail msg, MandantContext context, DiskArchive diskArchive, boolean background_index ) throws ArchiveMsgException, IOException, VaultException, IndexException
    {
        int index = 0;

        DiskSpaceHandler data_dsh = get_next_active_data_diskspace( index );
        DiskSpaceHandler index_dsh = get_next_active_index_diskspace( index );
        if (data_dsh == null)
        {
            throw new VaultException(toString() + ": " + Main.Txt("No_diskspace_for_data_found") );
        }
        if (index_dsh == null)
        {
            throw new VaultException(toString() + ": " + Main.Txt("No_diskspace_for_index_found") );
        }
        if (index_dsh.islock_for_rebuild() || data_dsh.islock_for_rebuild())
        {
            throw new VaultException(toString() + ": " + Main.Txt("Index_Rebuild_in_process") );
        }

        // GET THE DISKSPACES FOR DATA AND INDEX
        data_dsh = open_dsh( data_dsh, msg.get_length() );
        index_dsh = open_dsh( index_dsh, 1024*1024 );

        // AND SHOVE IT RIGHT IN!!!!
        write_mail_file( context, data_dsh, index_dsh, msg, background_index );

        return true;
    }

    private boolean handle_existing_mail_in_vault( IndexManager idx, RFCGenericMail msg, DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh ) throws CorruptIndexException, IOException
    {

        if (IndexManager.no_single_instance())
            return false;

        // return false;
        boolean ret = false;
        String hash = new String(Base64.encodeBase64(msg.get_hash()));


        // BUILD SEARCHABLE ARRAY
        DocHashEntry dhe = null;

        ArrayList<Searchable> search_arr = new ArrayList<Searchable>();

        
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);

            // SKIP DATA ONLY DS
            if (!dsh.is_index())
                continue;

            if (dsh.get_searcher() != null)
                search_arr.add( dsh.get_searcher() );
            else
                LogManager.msg_index(LogManager.LVL_ERR, "Found missing hash checker " + dsh.ds.getPath());
            
            dhe = dsh.has_hash_entry(hash);
            
            if (dhe != null)
            {
                index_dsh = dsh;
                LogManager.msg_index(LogManager.LVL_DEBUG, "Found short term hash for msg " + msg.toString());
                break;
            }

        }

        // IN SHORT TERM HASH?
        if (dhe != null)
        {
            Document doc = dhe.getDoc();
            int doc_ds_id = dhe.get_ds_id();

            data_dsh = get_dsh(doc_ds_id);

            if (data_dsh.getDs().getId() == doc_ds_id)
            {
                // WE HAVE TO FLUSH TO DISK FIRST BECAUSE OF OPEN STREAM READERS
                try
                {
                    index_dsh.flush();
                    search_arr.clear();
                    search_arr.add(index_dsh.get_searcher());
                }
                catch (IndexException indexException)
                {
                }
                catch (VaultException vaultException)
                {
                }
            }
        }

        // SEARCH IN LONGTERM HASH
        try
        {
            // PARALLEL SEARCH
            ParallelMultiSearcher pms = new ParallelMultiSearcher(search_arr.toArray( new Searchable[0] ));

            Term term = new Term(CS_Constants.FLD_HASH, hash);
            Query qry = new MatchAllDocsQuery();

            TermsFilter filter = new TermsFilter();
            filter.addTerm(term);
            // SSSSEEEEAAAARRRRCHHHHHHH AND GIVE FIRST RESULT
            TopDocs tdocs = pms.search(qry, filter, 1/*, null*/);

            if (tdocs.totalHits == 1)
            {
                // FOUND SAME MAIL
                Document doc = pms.doc(tdocs.scoreDocs[0].doc);

                // NOW RESOLVE INDEX FOR THIS DOCUMENT
                int s_idx = pms.subSearcher(tdocs.scoreDocs[0].doc);
                Searchable s_hit  = search_arr.get(s_idx);
                for (int i = 0; i < dsh_list.size(); i++)
                {
                    DiskSpaceHandler dsh = dsh_list.get(i);

                    // SKIP DATA ONLY DS
                    if (!dsh.is_index())
                        continue;

                    if (s_hit == dsh.get_searcher())
                    {
                        index_dsh = dsh;
                        break;
                    }
                }

                // GET DATA DSH
                int doc_ds_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DS);                
                data_dsh = get_dsh(doc_ds_id);

                if (data_dsh.getDs().getId() == doc_ds_id)
                {
                    // UPDATE IF NECESSARY WITH NEW BCC
                    LogManager.msg_index(LogManager.LVL_DEBUG, "Found long term hash for msg " + msg.toString());

                    ret = IndexManager.handle_bcc_and_update(data_dsh, index_dsh, doc, msg);
                }
            }

            pms.close();
        }
        catch (Exception iOException)
        {
            LogManager.msg_archive( LogManager.LVL_ERR, "Error while updating index", iOException);
        }

        return ret;
    }


    void write_mail_file( MandantContext m_ctx, DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean background_index ) throws ArchiveMsgException, IndexException, CorruptIndexException, IOException
    {
        
        int da_id = -1;
        int ds_id = -1;

        da_id = data_dsh.ds.getDiskArchive().getId();
        ds_id = data_dsh.ds.getId();

        IndexManager idx = m_ctx.get_index_manager();



        // IS MAIL ALREADY IN INDEX? THEN HANDLE UPDATE DOCUMENT
        boolean handled = handle_existing_mail_in_vault(idx, msg, data_dsh, index_dsh);
        if (handled)
        {
            // WE ARE DONE, REMOVE MESSAGE, THIS FITS TO delete_after_index BELOW
            msg.delete();
            return;
        }

        // WRITE OUT MAIL DATA
        try
        {
            data_dsh.write_encrypted_file(msg, password);
        }
        catch (Exception ex)
        {
            
            throw new ArchiveMsgException(toString() + ": " + Main.Txt("Cannot write data file") + ": " +   ex.getMessage());
        }
        



        String uuid = data_dsh.get_message_uuid(msg);
        LogManager.msg_archive( LogManager.LVL_DEBUG,  "Wrote mail file " + uuid);
        

        // AND INDEX IT AFTERWARDS
        // USE THREAD ?
        boolean parallel_index = m_ctx.getPrefs().get_boolean_prop(MandantPreferences.INDEX_TASK, true);
        //

        
        parallel_index = Main.get_bool_prop(GeneralPreferences.INDEX_MAIL_IN_BG, parallel_index);
        
        if (parallel_index && background_index)
        {
            idx.create_IndexJobEntry_task(m_ctx, uuid, da_id, ds_id,  index_dsh, msg, /*delete_after_index*/true,/*skip_account_match*/ false);
        }
        else
        {
            if (!parallel_index)
                LogManager.msg_archive( LogManager.LVL_ERR,  "No parallel index");
            
            // NO, DO RIGHT HERE
            idx.handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, /*delete_after_index*/true, parallel_index, /*skip_account_match*/ false);
        }
    }

    
    @Override
    public void flush()
    {
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            try
            {
                dsh.flush();
            }
            catch (Exception ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR, "Error while flushing DiskSpace " + dsh.ds.getPath(), ex);
            }
        }
    }

    @Override
    public void close() throws VaultException
    {
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            try
            {
                dsh.close();
            }
            catch (Exception ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR, "Error while closing DiskSpace " + dsh.ds.getPath(), ex);
            }
        }
    }

    @Override
    public String get_password()
    {
        return password;
    }

    @Override
    public String get_name()
    {
        return disk_archive.getName();
    }

    @Override
    public boolean has_sufficient_space()
    {
        for (int i = 0; ; i++)
        {
            DiskSpaceHandler dsh = get_next_active_data_diskspace(i);
            if (dsh == null)
                break;

            if (!dsh.no_space_left())
                return true;
        }
        return false;
    }
    
    @Override
    public boolean has_sufficient_space(long size)
    {
        for (int i = 0; ; i++)
        {
            DiskSpaceHandler dsh = get_next_active_data_diskspace(i);
            if (dsh == null)
                break;

            if (!dsh.no_space_left())
            {
                if (dsh.checkCapacity(size))
                    return true;
            }
        }
        return false;
    }

    @Override
    public void set_status( int code, String text )
    {
        status.set_status(code, text);
    }

    @Override
    public boolean is_in_rebuild()
    {
        // FIRST PASS, OPEN INDEX READERS
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            if (dsh.islock_for_rebuild())
            {
                return true;
            }
        }

        return false;
    }
}
