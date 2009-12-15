/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.DAO.DiskSpaceDAO;
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
import dimm.home.mailarchiv.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.DirectoryEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.IndexWriter;

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
                LogManager.err_log_fatal("DiskSpace <" + dsf.getAbsolutePath() + "> was not found, skipping");
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
                throw new ArchiveMsgException("Error while writing to disk" + _ex.getMessage()  );
            }
        }
        return ret;
    }

    public DiskSpaceHandler open_dsh( DiskSpaceHandler dsh, long free_space) throws VaultException
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
        catch (VaultException vaultException)
        {
            LogManager.err_log(Main.Txt("Cannot_open_active_diskspace") + " " + dsh.getDs().getPath(), vaultException);
            if (dsh.getDs().getStatus().compareTo(CS_Constants.DS_EMPTY) == 0)
            {
                dsh.create();
                dsh.open();
                // OPEN WRITE_INDEX
                dsh.open_write_index();
            }
            else
            {
                throw new VaultException( vaultException.getMessage() );
            }
        }


        while (!dsh.checkCapacity(free_space))
        {
            DiskSpace ds = dsh.getDs();

            status.set_status(StatusEntry.BUSY, "DiskSpace " + ds.getPath() + " is full" );
            Notification.throw_notification( disk_archive.getMandant(), Notification.NF_INFORMATIVE, status.get_status_txt() );
            ds.setStatus( CS_Constants.DS_FULL);

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.save(ds);

            DiskSpaceHandler new_dsh = get_next_active_diskspace( 0, dsh.is_data() );

            if (new_dsh == null)
            {
                throw new VaultException("No diskspace for " + (dsh.is_data() ? "data" : "index") + " found" );
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
                LogManager.err_log( Main.Txt("Cannot_open_active_diskspace") + " " + dsh.getDs().getPath(), vaultException);
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
            throw new VaultException("No diskspace for data found" );
        }
        if (index_dsh == null)
        {
            throw new VaultException("No diskspace for index found" );
        }
        if (index_dsh.islock_for_rebuild() || data_dsh.islock_for_rebuild())
        {
            throw new VaultException("Index Rebuild in process" );
        }

        // GET THE DISKSPACES FOR DATA AND INDEX
        data_dsh = open_dsh( data_dsh, msg.get_length() );
        index_dsh = open_dsh( index_dsh, 1024*1024 );



        // AND SHOVE IT RIGHT IN!!!!
        write_mail_file( context, data_dsh, index_dsh, msg, background_index );

        // ADD CAPACITY COUNTER
        data_dsh.add_message_info(msg);

        // TODO: MAYBE THIS SLOWS DOWN
        data_dsh.flush();

        return true;
    }

    void write_mail_file( MandantContext m_ctx, DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean background_index ) throws ArchiveMsgException, IndexException
    {
        
        int da_id = -1;
        int ds_id = -1;

        da_id = data_dsh.ds.getDiskArchive().getId();
        ds_id = data_dsh.ds.getId();

        IndexManager idx = m_ctx.get_index_manager();



        // IS MAIL ALREADY IN INDEX? THEN HANDLE UPDATE DOCUMENT
        boolean handled = idx.handle_existing_mail_in_vault(this, msg);
        if (handled)
        {
            // WE ARE DONE, REMOVE MESSAGE
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
            LogManager.log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Cannot write data file: " + ex.getMessage());
        }

        String uuid = data_dsh.get_message_uuid(msg);
        LogManager.log(Level.FINE, "Wrote mail file " + uuid);
        

        // AND INDEX IT AFTERWARDS
        // USE THREAD ?
        boolean parallel_index = m_ctx.getPrefs().get_boolean_prop(MandantPreferences.INDEX_TASK, true);
        //

        
        parallel_index = Main.get_bool_prop(GeneralPreferences.INDEX_MAIL_IN_BG, parallel_index);
        
        if (parallel_index && background_index)
        {
            idx.create_IndexJobEntry_task(m_ctx, uuid, da_id, ds_id,  index_dsh, msg, /*delete_after_index*/true);
        }
        else
        {
            if (!parallel_index)
                LogManager.log(Level.SEVERE, "No parallel index");
            
            // NO, DO RIGHT HERE
            idx.handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, /*delete_after_index*/true, parallel_index);
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
            catch (IndexException ex)
            {
                Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (VaultException ex)
            {
                Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    @Override
    public void close() throws VaultException
    {
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            dsh.close();
        }
    }

    @Override
    public String get_password()
    {
        return password;
    }



}
