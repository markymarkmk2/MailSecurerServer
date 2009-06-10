/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hibernate.DiskSpace;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.beans.XMLEncoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 *
 * @author mw
 */
public class DiskSpaceHandler
{
    DiskSpace ds;

    public DiskSpaceHandler( DiskSpace _ds )
    {
        ds = _ds;
    }
    
    public DiskSpace getDs()
    {
        return ds;
    }
    void del_recursive(File f)
    {
        if (f.isDirectory())
        {
            File[] f_list = f.listFiles();
            for (int i = 0; i < f_list.length; i++)
            {
                del_recursive( f_list[i] );
            }
        }
        f.delete();
    }
    private long cap_recursive(File f, long cap)
    {
        if (f.isDirectory())
        {
            File[] f_list = f.listFiles();
            for (int i = 0; i < f_list.length; i++)
            {
                cap = cap_recursive( f_list[i], cap );
            }
        }
        else
        {
            cap += f.length();
        }
        return cap;
    }

    public long calc_real_capacity() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (!path.exists())
        {
            throw new VaultException( ds, "Missing directory");
        }
        long cap = 0;
        cap = cap_recursive( path, cap );

        return cap;
    }

    public boolean exists()
    {
        File path = new File( ds.getPath() );
        return path.exists();
    }


    public void clear() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (!path.exists())
        {
            throw new VaultException( ds, "Missing directory");
        }
        del_recursive( path );
    }


    public void create() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (!path.getParentFile().exists())
        {
            throw new VaultException( ds, "Missing parent directory");
        }

        path.mkdir();

        long cap = read_act_capacity();
        if (cap > 0)
            throw new VaultException( ds, "Contains data, should be empty" );

        File cap_file = new File( ds.getPath() + "/" + "cap.dat" );

        try
        {
            RandomAccessFile raf = new RandomAccessFile(cap_file, "rw");
            raf.writeBytes(Long.toString(0));
            raf.close();
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write cap file: " + ex.getMessage());
        }
        
        try
        {
            XMLEncoder e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(ds.getPath() + "/ds.xml")));
            e.writeObject(ds);
            e.close();
            e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(ds.getPath() + "/da.xml")));
            e.writeObject(ds.getDiskArchive());
            e.close();
            e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(ds.getPath() + "/ma.xml")));
            e.writeObject(ds.getDiskArchive().getMandant());
            e.close();
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write XML params: " + ex.getMessage());
        }
    }

    public static long parse_capacity( String s )
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
            LogManager.err_log_fatal( "Invalid capacity string " + s, ex);
            cap = 0;
        }
        return cap;
    }
    public static String format_capacity( long size )
    {
        String dim = "";
        if (size > 1024)
        {
            dim = "k";
            size /= 1024;
        }
        if (size > 1024)
        {
            dim = "M";
            size /= 1024;
        }
        if (size > 1024)
        {
            dim = "G";
            size /= 1024;
        }
        if (size > 1024)
        {
            dim = "T";
            size /= 1024;
        }

        if (dim.length() > 0)
            return Long.toString(size) + " " + dim;

        return Long.toString(size);
    }


    public boolean checkCapacity(  File msg )
    {
        File path = new File( ds.getPath() );

        long allowed_cap = parse_capacity(ds.getMaxCapacity());

        // TEST IF FS IS FULL ANYWAY
        if (path.getFreeSpace() < msg.length() + 1024*1024)  // AT LEAST 1MB ON FS
            return false;

        // READ SIZE FROM DISK
        long act_cap = read_act_capacity();

        // CHECK IF OKAY
        if (allowed_cap > 0 && act_cap + msg.length() > allowed_cap)
            return false;

        return true;
    }

    public long read_act_capacity()
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
                LogManager.err_log_fatal( "Cannot read capacity file for DiskSpace " + ds.getPath() , ex);
            }

        }
        return cap;
    }
    public long incr_act_capacity( long diff  )
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
                LogManager.err_log_fatal( "Cannot write capacity file for DiskSpace " + ds.getPath() , ex);
            }

        }
        return cap;
    }

}
