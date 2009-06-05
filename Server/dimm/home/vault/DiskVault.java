/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.DAO.DiskSpaceDAO;
import dimm.home.hibernate.DiskArchive;
import dimm.home.hibernate.DiskSpace;
import dimm.home.hibernate.Mandant;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Notification;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOException;

/**
 *
 * @author mw
 */
public class DiskVault implements Vault, StatusHandler
{

    public static final int DS_FULL = 0x0001;
    public static final int DS_ERROR = 0x0002;
    public static final int DS_OFFLINE = 0x0004;

    DiskArchive disk_archive;


    public DiskVault( DiskArchive da )
    {
        disk_archive = da;
    }

    long parse_capacity( String s )
    {
        long cap = 0;
        long f = 1;

        int idx = s.toUpperCase().indexOf("K");
        if (idx > 0)
        {
            s = s.substring(0, idx);
            f = 1024;
        }
        idx = s.toUpperCase().indexOf("M");
        if (idx > 0)
        {
            s = s.substring(0, idx);
            f = 1024*1024;
        }
        idx = s.toUpperCase().indexOf("G");
        if (idx > 0)
        {
            s = s.substring(0, idx);
            f = 1024*1024*1024;
        }

        try
        {
            cap = Long.parseLong(s);
            cap *= f;
        }
        catch (Exception ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, "Invalid capacity string " + s, ex);
            cap = 0;
        }
        return cap;
    }
    private boolean checkCapacity( DiskSpace ds, File msg )
    {
        File path = new File( ds.getPath() );

        long allowed_cap = parse_capacity(ds.getMaxCapacity());

        // TEST IF FS IS FULL ANYWAY
        if (path.getFreeSpace() < msg.length() + 1024*1024)  // AT LEAST 1MB ON FS
            return false;

        // READ SIZE FROM DISK
        long act_cap = read_act_capacity( ds );

        // CHECK IF OKAY
        if (allowed_cap > 0 && act_cap + msg.length() > allowed_cap)
            return false;

        return true;
    }

    synchronized private File create_unique_mailfile( DiskSpace ds, File mail )
    {
        String path = ds.getPath();
        File trg_file = null;

        do
        {
            Date d = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("/yyyy/MM/dd/HHmmss.SSS");
            path = path + sdf.format(d);
            trg_file = new File( path );
            
            if (trg_file.exists())
            {
                try
                {
                    Thread.sleep(5);
                }
                catch (InterruptedException ex)
                {}
            }

        } while (trg_file.exists() );

        return trg_file;
    }

    private long read_act_capacity( DiskSpace ds )
    {
        File cap_file = new File( ds.getPath() + "/" + "cap.dat" );
        long cap = 0;

        if (cap_file.exists())
        {
            try
            {
                RandomAccessFile raf = new RandomAccessFile(cap_file, "r");
                cap = Long.parseLong(raf.readLine());
                raf.close();
            }
            catch (Exception ex)
            {
                Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        return cap;
    }
    private long incr_act_capacity( DiskSpace ds, long diff  )
    {
        File cap_file = new File( ds.getPath() + "/" + "cap.dat" );
        long cap = 0;

        if (cap_file.exists())
        {
            try
            {
                RandomAccessFile raf = new RandomAccessFile(cap_file, "rw");
                cap = Long.parseLong(raf.readLine());
                cap += diff;
                raf.seek(0);
                raf.writeBytes(Long.toString(cap));
                raf.close();
            }
            catch (Exception ex)
            {
                Logger.getLogger(DiskVault.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        return cap;
    }

    private boolean test_flag( DiskSpace ds, int flag )
    {
        return ((ds.getFlags() & flag) == flag);
    }

    DiskSpace get_next_active_diskspace(int index )
    {
        Set<DiskSpace> dss = disk_archive.getDiskSpaces();

        Iterator<DiskSpace> it = dss.iterator();

        while (it.hasNext())
        {
            DiskSpace ds = it.next();
            if (test_flag( ds, DS_FULL) || test_flag(ds, DS_ERROR) || test_flag(ds, DS_OFFLINE))
                continue;

            if (index > 0)
            {
                index--;
                continue;
            }

            // GOT IT
            return ds;
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
    public boolean archive_mail( File msg, Mandant mandant, DiskArchive diskArchive ) throws ArchiveMsgException
    {
        boolean ret = false;
        try
        {
            ret = low_level_archive_mail(msg, mandant, diskArchive);
        }
        catch (IOException ex)
        {
            // RETRY IN CASE OF ERROR ONCE, MAYBE DISKSPACE IS FULL
            // IF MAIL IS BIGGER THAN DISKSPACE WE CHOKE
            try
            {
                ret = low_level_archive_mail(msg, mandant, diskArchive);
            }
            catch (IOException _ex)
            {
                throw new ArchiveMsgException("Error while writing to disk" + _ex.getMessage()  );
            }
        }


        return ret;
    }

    boolean low_level_archive_mail( File msg, Mandant mandant, DiskArchive diskArchive ) throws ArchiveMsgException, IOException
    {
        int ds_idx = 0;

        DiskSpace ds = get_next_active_diskspace( ds_idx );

        if (ds == null)
        {
            throw new ArchiveMsgException("No Diskspace found" );
        }
        while (!checkCapacity(ds, msg))
        {
            status.set_status(StatusEntry.BUSY, "DiskSpace " + ds.getPath() + " is full" );
            Notification.throw_notification( disk_archive.getMandant(), Notification.NF_INFORMATIVE, status.get_status_txt() );
            ds.setFlags(ds.getFlags() | DS_FULL);

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.save(ds);

            ds = get_next_active_diskspace( ds_idx );

            if (ds == null)
            {
                throw new ArchiveMsgException("No Diskspace found" );
            }
        }


        write_mail_file( ds, msg );

        // ADD CAPACITY COUNTER
        incr_act_capacity(ds, msg.length());

        return true;
    }

    void write_mail_file( DiskSpace ds, File mail ) throws ArchiveMsgException, IOException
    {
        BufferedOutputStream bos = null;
        byte[] buff = new byte[8192];

        // NOW WE HAVE A FILE ON E FREE DISKSPACE

        File out_file = create_unique_mailfile(ds, mail);

        try
        {

            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mail));
            bos = new BufferedOutputStream(new FileOutputStream(out_file));

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
    }

}
