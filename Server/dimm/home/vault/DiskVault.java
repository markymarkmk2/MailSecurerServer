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
import dimm.home.mail.RFCFileMail;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;

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
            dsh_list.add( new DiskSpaceHandler( context, it.next()));
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
    public boolean archive_mail( RFCFileMail msg, MandantContext context, DiskArchive diskArchive ) throws ArchiveMsgException, VaultException, IndexException
    {
        boolean ret = false;
        try
        {
            ret = low_level_archive_mail(msg, context, diskArchive);
        }
        catch (IOException ex)
        {
            // RETRY IN CASE OF ERROR ONCE, MAYBE DISKSPACE IS FULL
            // IF MAIL IS BIGGER THAN DISKSPACE WE CHOKE
            try
            {
                ret = low_level_archive_mail(msg, context, diskArchive);
            }
            catch (IOException _ex)
            {
                throw new ArchiveMsgException("Error while writing to disk" + _ex.getMessage()  );
            }
        }


        return ret;
    }

    DiskSpaceHandler open_data_dsh( int ds_idx, long free_space) throws VaultException
    {
        return open_dsh(ds_idx, true, free_space);
    }
    DiskSpaceHandler open_index_dsh( int ds_idx) throws VaultException
    {
        // HOW BIG IS INDEX COMPARED TO FILESIZE???? I DO IT SIMPLE, KEEP AT LEAST 1 MB FREE
        return open_dsh(ds_idx, false, 1024*1024);
    }

    public DiskSpaceHandler open_dsh( int ds_idx, boolean is_data, long free_space) throws VaultException
    {
        DiskSpaceHandler dsh = get_next_active_diskspace( ds_idx, is_data);

        if (dsh == null)
        {
            throw new VaultException("No diskspace for " + (is_data ? "data" : "index") + " found" );
        }

        try
        {
            if (!dsh.is_open())
            {
                dsh.open();
            }
        }
        catch (VaultException vaultException)
        {
            dsh.create();
            dsh.open();
        }


        while (!dsh.checkCapacity(free_space))
        {
            DiskSpace ds = dsh.getDs();

            status.set_status(StatusEntry.BUSY, "DiskSpace " + ds.getPath() + " is full" );
            Notification.throw_notification( disk_archive.getMandant(), Notification.NF_INFORMATIVE, status.get_status_txt() );
            ds.setStatus( CS_Constants.DS_FULL);

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.save(ds);

            dsh = get_next_active_diskspace( ds_idx, is_data );

            if (dsh == null)
            {
                throw new VaultException("No diskspace for " + (is_data ? "data" : "index") + " found" );
            }
        }
        return dsh;
    }


    boolean low_level_archive_mail( RFCFileMail msg, MandantContext context, DiskArchive diskArchive ) throws ArchiveMsgException, IOException, VaultException, IndexException
    {
        int ds_idx = 0;

        // GET THE DISKSPACES FOR DATA AND INDEX
        DiskSpaceHandler data_dsh = open_data_dsh( ds_idx, msg.get_length() );
        DiskSpaceHandler index_dsh = open_index_dsh( ds_idx );


        // AND SHOVE IT RIGHT IN!!!!
        write_mail_file( context, data_dsh, index_dsh, msg );

        // ADD CAPACITY COUNTER
        data_dsh.add_message_info(msg);

        return true;
    }

    void write_mail_file( MandantContext m_ctx, DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh, RFCFileMail msg ) throws ArchiveMsgException, IndexException
    {
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

        // AND INDEX IT AFTERWARDS
        IndexManager idx = m_ctx.get_index_manager();

        int da_id = data_dsh.ds.getDiskArchive().getId();
        int ds_id = data_dsh.ds.getId();
        String uuid = data_dsh.get_message_uuid(msg);

        // USE THREAD ?
        boolean parallel_index = m_ctx.getPrefs().get_boolean_prop(MandantPreferences.INDEX_TASK, true);
        if (parallel_index)
        {

            idx.create_IndexJobEntry_task(m_ctx, uuid, da_id, ds_id,  index_dsh, msg, /*delete_after_index*/true);
        }
        else
        {
            // NO, DO RIGHT HERE
            idx.handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, /*delete_after_index*/true);
        }
    }

    @Override
    public void flush()
    {
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            dsh.flush();
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
}
