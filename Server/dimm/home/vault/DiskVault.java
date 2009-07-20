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
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.CryptTools;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOException;
import javax.mail.MessagingException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;

/**
 *
 * @author mw
 */
public class DiskVault implements Vault, StatusHandler
{

    public static final int DS_FULL = 0x0001;
    public static final int DS_ERROR = 0x0002;
    public static final int DS_OFFLINE = 0x0004;

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
            dsh_list.add( new DiskSpaceHandler(it.next()));
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




    private boolean test_flag( DiskSpace ds, int flag )
    {
        int f = Integer.parseInt(ds.getFlags());
        return ((f & flag) == flag);
    }

    DiskSpaceHandler get_next_active_diskspace(int index )
    {

        Iterator<DiskSpaceHandler> it = dsh_list.iterator();

        while (it.hasNext())
        {
            DiskSpaceHandler dsh = it.next();
            DiskSpace ds = dsh.getDs();
            if (test_flag( ds, DS_FULL) || test_flag(ds, DS_ERROR) || test_flag(ds, DS_OFFLINE))
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
    public boolean archive_mail( RFCFileMail msg, MandantContext context, DiskArchive diskArchive ) throws ArchiveMsgException, VaultException
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

    boolean low_level_archive_mail( RFCFileMail msg, MandantContext context, DiskArchive diskArchive ) throws ArchiveMsgException, IOException, VaultException
    {
        int ds_idx = 0;

        DiskSpaceHandler dsh = get_next_active_diskspace( ds_idx );

        if (dsh == null)
        {
            throw new ArchiveMsgException("No Diskspace found" );
        }

        while (!dsh.checkCapacity(msg.getFile()))
        {
            DiskSpace ds = dsh.getDs();

            status.set_status(StatusEntry.BUSY, "DiskSpace " + ds.getPath() + " is full" );
            Notification.throw_notification( disk_archive.getMandant(), Notification.NF_INFORMATIVE, status.get_status_txt() );
            ds.setFlags(Integer.toString(Integer.parseInt(ds.getFlags()) | DS_FULL));

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.save(ds);

            dsh = get_next_active_diskspace( ds_idx );

            if (dsh == null)
            {
                throw new ArchiveMsgException("No Diskspace found" );
            }
        }


        write_mail_file( context, dsh, msg );

        // ADD CAPACITY COUNTER
        dsh.add_message_info(msg);

        return true;
    }

    void write_mail_file( MandantContext context, DiskSpaceHandler dsh, RFCFileMail msg ) throws ArchiveMsgException, IOException
    {
        OutputStream bos = null;
        byte[] buff = new byte[8192];

        // NOW WE HAVE A FILE ON E FREE DISKSPACE

        File out_file = msg.create_unique_mailfile(dsh.getMailPath());
        CryptTools.ENC_MODE encrypt = CryptTools.ENC_MODE.ENCRYPT;

        try
        {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(msg.getFile()));
            OutputStream os = new FileOutputStream(out_file);
            bos = CryptTools.create_crypt_outstream(context, os, password, encrypt);


            while (true)
            {
                int rlen = bis.read(buff);
                if (rlen == -1)
                    break;

                bos.write(buff, 0, rlen);
            }

        }
        catch (IIOException ex)
        {
            throw new IOException("Cannot write to mail file: " + ex.getMessage());
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
            throw new ArchiveMsgException("Cannot create duplicate temp file: " + ex.getMessage());
        }
        finally
        {
            try
            {
                bos.close();
            }
            catch (IOException ex)
            {
            }
        }

        Document doc = new Document();
        IndexManager idx = Main.get_control().get_index_manager();
        try
        {
            idx.index_mail_file(msg, doc);
            IndexWriter writer = dsh.get_write_index();
            writer.addDocument(doc);
        }
        catch (MessagingException ex)
        {
            Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IndexException ex)
        {
            Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
