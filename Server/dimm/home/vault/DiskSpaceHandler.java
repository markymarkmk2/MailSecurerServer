/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.mail.RFCFileMail;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.hibernate.DiskSpace;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;



/**
 *
 * @author mw
 */

class DiskSpaceInfo
{
    private long capacity;
    private long firstEntryTS;
    private long lastEntryTS;
    private String language;

    DiskSpaceInfo()
    {
        capacity = 0;
        firstEntryTS = 0;
        lastEntryTS = 0;
        language = "de";
    }
    /**
     * @return the capacity
     */
    public long getCapacity()
    {
        return capacity;
    }

    /**
     * @param capacity the capacity to set
     */
    public void setCapacity( long capacity )
    {
        this.capacity = capacity;
    }

    /**
     * @return the firstEntryTS
     */
    public long getFirstEntryTS()
    {
        return firstEntryTS;
    }

    /**
     * @param firstEntryTS the firstEntryTS to set
     */
    public void setFirstEntryTS( long firstEntryTS )
    {
        this.firstEntryTS = firstEntryTS;
    }

    /**
     * @return the lastEntryTS
     */
    public long getLastEntryTS()
    {
        return lastEntryTS;
    }

    /**
     * @param lastEntryTS the lastEntryTS to set
     */
    public void setLastEntryTS( long lastEntryTS )
    {
        this.lastEntryTS = lastEntryTS;
    }

    /**
     * @return the language
     */
    public String getLanguage()
    {
        return language;
    }

    /**
     * @param language the language to set
     */
    public void setLanguage( String language )
    {
        this.language = language;
    }
}
public class DiskSpaceHandler
{
    DiskSpace ds;
    DiskSpaceInfo dsi;
    boolean _open;
    IndexWriter read_index;
    IndexWriter write_index;

    public DiskSpaceHandler( DiskSpace _ds )
    {
        ds = _ds;
        _open = false;
    }

    public boolean is_open()
    {
        return _open;
    }

    public IndexWriter get_read_index()
    {
        return read_index;
    }
    public IndexWriter get_write_index()
    {
        return write_index;
    }
    public void commit_index()
    {
        try
        {
            write_index.commit();
        }
        catch (CorruptIndexException ex)
        {
            Logger.getLogger(DiskSpaceHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex)
        {
            Logger.getLogger(DiskSpaceHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }



    public void open() throws VaultException
    {
        read_info();
        try
        {
            read_index = Main.get_control().get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), /* do_index*/false);
            write_index = Main.get_control().get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), /* do_index*/true);
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot open index: " + iex);
        }
       _open = true;
    }

    public void create() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (!path.getParentFile().exists())
        {
            throw new VaultException( ds, "Missing parent directory");
        }

        path.mkdir();

        try
        {
            read_info();
            if (dsi.getCapacity() > 0)
                throw new VaultException( ds, "Contains data and info, should be empty" );

        }
        catch (Exception ex)
        {
            // REGULAR EXIT OF CHECK
        }

        MandantContext m_ctx = Main.get_control().get_m_context( ds.getDiskArchive().getMandant() );

        dsi = new DiskSpaceInfo();
        dsi.setLanguage( m_ctx.getPrefs().get_language());

        try
        {
            write_index = Main.get_control().get_index_manager().create_index(getIndexPath(), dsi.getLanguage());
            write_index.commit();

            read_index = Main.get_control().get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), /* do_index*/false);
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot create index: " + iex);
        }


        update_info();

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


    
    void read_info() throws VaultException
    {
        try
        {
            XMLDecoder e = new XMLDecoder(new BufferedInputStream(new FileInputStream(ds.getPath() + "/dsinfo.xml")));
            Object o = e.readObject();
            e.close();
            if (o instanceof DiskSpaceInfo)
                dsi = (DiskSpaceInfo)o;
            else
                throw new VaultException( ds, "Invalid info file" );
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot read info file: " + ex.getMessage() );
        }
    }
    public void close() throws VaultException
    {
        update_info();
        try
        {
            read_index.close();
            write_index.commit();
            write_index.close();
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot close index: " + iex);
        }

        _open = false;
    }

    void update_info() throws VaultException
    {
        try
        {
            XMLEncoder e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(ds.getPath() + "/dsinfo.xml")));
            e.writeObject(dsi);
            e.close();
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write info file: " + ex.getMessage() );
        }
    }
    
    public DiskSpace getDs()
    {
        return ds;
    }


    public void add_message_info( RFCFileMail msg ) throws VaultException
    {
        dsi.setCapacity( dsi.getCapacity() + msg.getFile().length());
        dsi.setLastEntryTS(msg.getDate().getTime());
        if (dsi.getFirstEntryTS() == 0)
            dsi.setFirstEntryTS(dsi.getLastEntryTS());

        update_info();
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

    public void delete_mail( RFCFileMail mail )
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public String get_message_uuid( RFCFileMail msg )
    {
        String ret = ds.getDiskArchive().getId() + "." + ds.getId() + "." + msg.getDate().getTime();
        return ret;
    }

    public long build_time_from_path( String absolutePath )
    {
        String rel_path = absolutePath.substring( getMailPath().length());

        return RFCFileMail.get_time_from_mailfile(rel_path);
    }
    
    public static long get_da_id_from_uuid( String uuid )
    {
        long id = -1;

        try
        {
            int pos = uuid.indexOf('.');
            id = Long.parseLong(uuid.substring(0, pos));
        }
        catch (Exception numberFormatException)
        {
        }

        return id;
    }

    public static long get_ds_id_from_uuid( String uuid )
    {
        long id = -1;

        try
        {
            int pos = uuid.indexOf('.');
            pos++;
            int pos2 = uuid.substring(pos).indexOf('.');
            id = Long.parseLong(uuid.substring(pos, pos2));
        }
        catch (Exception numberFormatException)
        {
        }

        return id;
    }
    public static long get_time_from_uuid( String uuid )
    {
        long id = -1;

        try
        {
            int pos = uuid.indexOf('.');
            pos++;
            int pos2 = uuid.substring(pos).indexOf('.');
            pos2++;
            id = Long.parseLong(uuid.substring(pos2));
        }
        catch (Exception numberFormatException)
        {
        }

        return id;
    }
    
    private DiskSpaceInfo info_recursive(File f, DiskSpaceInfo local_dsi)
    {
        if (f.isDirectory())
        {
            File[] f_list = f.listFiles();
            for (int i = 0; i < f_list.length; i++)
            {
                local_dsi = info_recursive( f_list[i], local_dsi );
            }
        }
        else
        {
            local_dsi.setCapacity( local_dsi.getCapacity() + f.length());
            long time = build_time_from_path( f.getAbsolutePath() );
            if (time > 0)
            {
                if (time > local_dsi.getLastEntryTS())
                    local_dsi.setLastEntryTS(time);
                if (local_dsi.getFirstEntryTS() == 0 || time < local_dsi.getFirstEntryTS())
                    local_dsi.setFirstEntryTS(time);
            }
        }
        return local_dsi;
    }

    void rebuild_info() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (!path.exists())
        {
            throw new VaultException( ds, "Missing directory");
        }
        
        DiskSpaceInfo local_dsi = new DiskSpaceInfo();
        dsi = info_recursive( path, local_dsi );
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
        long act_cap = dsi.getCapacity();

        // CHECK IF OKAY
        if (allowed_cap > 0 && act_cap + msg.length() > allowed_cap)
            return false;

        return true;
    }
/*
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
*/
    public String getMailPath()
    {
        return ds.getPath() + "/mail";
    }
    public String getIndexPath()
    {
        return ds.getPath() + "/index";
    }
}
