/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.DAO.DiskSpaceDAO;
import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import home.shared.hibernate.DiskSpace;
import home.shared.mail.CryptAESInputStream;
import home.shared.mail.CryptAESOutputStream;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;



/**
 *
 * @author mw
 */


public class DiskSpaceHandler
{

    DiskSpace ds;
    DiskSpaceInfo dsi;
    boolean _open;
    //IndexReader read_index;
    IndexWriter write_index;
    MandantContext m_ctx;
    public final String idx_lock = "idx";
    public final String data_lock = "data";

    public DiskSpaceHandler( MandantContext _m_ctx, DiskSpace _ds )
    {
        m_ctx = _m_ctx;
        ds = _ds;
        _open = false;
    }

    public boolean is_open()
    {
        return _open;
    }
    public int get_enc_mode()
    {
        return dsi.getEncMode();
    }
/*
    public IndexReader get_read_index()
    {
        return read_index;
    }
 * */
    public IndexWriter get_write_index()
    {
        return write_index;
    }
    public void commit_index()
    {
        if (is_index())
        {
            try
            {
                write_index.commit();
            }
            catch (CorruptIndexException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }
            catch (IOException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }
        }
    }

    boolean test_flag( DiskSpace ds, int flag )
    {
        int f = Integer.parseInt(ds.getFlags());
        return ((f & flag) == flag);
    }

    public IndexReader create_read_index() throws VaultException
    {
        if (!is_open())
        {
            open();
        }
        try
        {
            if (is_index())
            {
                return m_ctx.get_index_manager().open_read_index(getIndexPath());
              /*  read_index = m_ctx.get_index_manager().open_read_index(getIndexPath());
                return read_index;
               * */
            }
            else
                throw new VaultException( ds, "Cannot open read index on non-index ds: " + ds.getPath());
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot open read index: " + iex.getMessage());
        }
    }
  /*  public void close_read_index() throws VaultException
    {
        try
        {
            if (is_index())
            {
                read_index.close();
            }
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot close read index: " , iex);
        }
    }*/
    public IndexWriter open_write_index() throws VaultException
    {
        if (!is_open())
        {
            open();
        }
        try
        {
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    if (write_index != null)
                        return write_index;

                    write_index = m_ctx.get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), /* do_index*/true);
                }
                return write_index;
            }
            else
                throw new VaultException( ds, "Cannot open write index on non-index ds: " + ds.getPath());
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot open write index: " + iex.getMessage());
        }
    }
    public void close_write_index() throws VaultException
    {
        try
        {
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    write_index.commit();
                    write_index.close();
                    write_index = null;
                }
            }
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot close write index: " , iex);
        }
    }



    public void open() throws VaultException
    {
        if (is_open())
        {
            close();
        }

        read_info();

       _open = true;
    }

    public Analyzer create_read_analyzer()
    {
        return m_ctx.get_index_manager().create_analyzer(dsi.getLanguage(), /*do_indexs*/ false);
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
            dsi = null;

            read_info();
        }
        catch (Exception ex)
        {
            // REGULAR EXIT OF CHECK
        }
        if (dsi != null)
        {
            if (dsi.getCapacity() > 0)
                throw new VaultException( ds, "Contains index data and info, should be empty" );
        }

        dsi = new DiskSpaceInfo();
        dsi.setLanguage( m_ctx.getPrefs().get_language());
        dsi.setEncMode( RFCFileMail.dflt_encoded ? RFCFileMail.dflt_encoding : RFCFileMail.ENC_NONE);

        try
        {
            // TOUCH (NOT OPEN!) INDEX
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    write_index = m_ctx.get_index_manager().create_index(getIndexPath(), dsi.getLanguage());
                    write_index.commit();
                    write_index.close();
                }
            }
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot create index: " , iex);
        }


        update_info();

        try
        {
            write_info_object( ds, "ds.xml" );
            write_info_object( ds.getDiskArchive(), "da.xml" );
            write_info_object( ds.getDiskArchive().getMandant(), "ma.xml" );
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write XML params: " , ex);
        }
    }

    private void write_info_object( Object o, String filename ) throws FileNotFoundException, IOException
    {
        File save_file = new File(ds.getPath() + "/" + filename + ".sav");
        if (save_file.exists())
            save_file.delete();
        File file = new File(ds.getPath() + "/" + filename);
        if (file.exists())
            file.renameTo(save_file);

        OutputStream bos = new FileOutputStream(file);
        XMLEncoder e = new XMLEncoder(bos);
        e.writeObject(o);
        e.close();
        bos.close();
    }

    private Object read_info_object( String filename ) throws FileNotFoundException, IOException
    {
        InputStream bis = new FileInputStream(ds.getPath() + "/" + filename);
        XMLDecoder e = new XMLDecoder(bis);
        Object o = e.readObject();
        e.close();
        bis.close();
        return o;
    }


    public boolean is_data()
    {
        return test_flag( ds, CS_Constants.DS_MODE_DATA);
    }
    public boolean is_index()
    {
        return test_flag( ds, CS_Constants.DS_MODE_INDEX);
    }
    
    private void read_info() throws VaultException
    {
        try
        {
            synchronized( data_lock )
            {
                Object o = read_info_object( "dsinfo.xml" );

                if (o instanceof DiskSpaceInfo)
                    dsi = (DiskSpaceInfo)o;
                else
                    throw new VaultException( ds, "Invalid info file" );
            }
        }
        catch (Exception ex)
        {
            try
            {
                Object o = read_info_object("dsinfo.xml.sav");
                if (o instanceof DiskSpaceInfo)
                {
                    LogManager.err_log_warn("Recovering from broken ds info object: " + ex.getMessage());
                    dsi = (DiskSpaceInfo)o;
                    return;
                }
            }
            catch (IOException iOException)
            {
            }
            
            throw new VaultException( ds, "Cannot read info file: " + ex.getMessage());
        }
    }
    public void close() throws VaultException
    {
        update_info();

        _open = false;
    }

    private void update_info() throws VaultException
    {
        try
        {
            synchronized( data_lock )
            {
                write_info_object( dsi, "dsinfo.xml" );
            }
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write info file: " , ex);
        }
        if (ds.getStatus().compareTo( CS_Constants.DS_EMPTY) == 0)
        {
            ds.setStatus( CS_Constants.DS_DATA);

            DiskSpaceDAO dao = new DiskSpaceDAO();
            dao.update(ds);
        }
    }
    
    public DiskSpace getDs()
    {
        return ds;
    }


    public void add_message_info( RFCGenericMail msg ) throws VaultException
    {
        dsi.setCapacity( dsi.getCapacity() + msg.get_length());
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

    public void delete_mail( RFCGenericMail mail )
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    // THIS IS THE FIXED MAIL UID STRUCT
    // MA_ID.DA_ID.DS_ID.Messagedate(long)[.enc]
    public String get_message_uuid( RFCGenericMail msg )
    {
        StringBuffer sb = new StringBuffer();
        sb.append(m_ctx.getMandant().getId() );
        sb.append(".");
        sb.append(ds.getDiskArchive().getId());
        sb.append(".");
        sb.append(ds.getId());
        sb.append(".");
        sb.append(msg.getDate().getTime() );
        if (msg.isEncoded())
        {
            sb.append(RFCGenericMail.get_suffix_for_encoded());
        }
        return sb.toString();

        //String ret = m_ctx.getMandant().getId() + "." + ds.getDiskArchive().getId() + "." + ds.getId() + "." + msg.getDate().getTime() + (msg.isEncoded() ? RFCGenericMail.get_suffix_for_encoded() : "");
        //return ret;
    }

    public long build_time_from_path( String absolutePath ) throws VaultException
    {
        try
        {
            String rel_path = absolutePath.substring(getMailPath().length());

            return RFCFileMail.get_time_from_mailfile(rel_path);
        }
        catch (ParseException parseException)
        {
            throw new VaultException("Invalid path for build_time_from_path: " + absolutePath,  parseException );
        }
    }
    
    public RFCGenericMail get_mail_from_time( long time, int enc_mode ) throws VaultException
    {
        String parent_path = getMailPath();
        String absolutePath = RFCFileMail.get_mailpath_from_time( parent_path, time, enc_mode );

        File mail_file = new File( absolutePath );
        if (!mail_file.exists())
            throw new VaultException( ds, "Cannot retrieve mail file for " + time + ": " + absolutePath );

        Date d = new Date(time);
        RFCGenericMail mail = new RFCFileMail( mail_file, d, false );
        return mail;
    }

    public static int get_da_id_from_uuid( String uuid ) throws VaultException
    {
        int id = -1;

        try
        {
            String[] arr = uuid.split("\\.");
            id =  Integer.parseInt(arr[1]);
        }
        catch (Exception numberFormatException)
        {
            throw new VaultException(  "Invalid format in uuid " + uuid);
        }

        return id;
    }

    public static int get_ma_id_from_uuid( String uuid ) throws VaultException
    {
        int id = -1;

        try
        {
            String[] arr = uuid.split("\\.");
            id =  Integer.parseInt(arr[0]);
        }
        catch (Exception numberFormatException)
        {
            throw new VaultException(  "Invalid format in get_ds_id_from_uuid for uuid " + uuid);
        }

        return id;
    }

    public static int get_ds_id_from_uuid( String uuid ) throws VaultException
    {
        int id = -1;

        try
        {
            String[] arr = uuid.split("\\.");
            id =  Integer.parseInt(arr[2]);
        }
        catch (Exception numberFormatException)
        {
            throw new VaultException(  "Invalid format in get_ds_id_from_uuid for uuid " + uuid);
        }

        return id;
    }
    public static long get_time_from_uuid( String uuid ) throws VaultException
    {
        long id = -1;

        try
        {
            String[] arr = uuid.split("\\.");
            id =  Long.parseLong(arr[3]);
        }
        catch (Exception numberFormatException)
        {
            throw new VaultException(  "Invalid format in get_time_from_uuid for uuid " + uuid);
        }

        return id;
    }
    public static boolean is_encoded_from_uuid( String uuid ) throws VaultException
    {
        return uuid.endsWith(RFCGenericMail.get_suffix_for_encoded());
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
            try
            {
                long time = build_time_from_path(f.getAbsolutePath());
                if (time > 0)
                {
                    if (time > local_dsi.getLastEntryTS())
                    {
                        local_dsi.setLastEntryTS(time);

                    }
                    if (local_dsi.getFirstEntryTS() == 0 || time < local_dsi.getFirstEntryTS())
                    {
                        local_dsi.setFirstEntryTS(time);

                    }
                }
            }
            catch (VaultException vaultException)
            {
                LogManager.err_log("Invalid path in info_recursive", vaultException);
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
        s = s.trim();

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
        idx = s.toUpperCase().indexOf("T");
        if (idx > 0)
        {
            s = s.substring(0, idx);
            f = 1024*1024*1024;
        }

        try
        {
            cap = Long.parseLong(s.trim());
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
            dim = "K";
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


    public boolean checkCapacity(  long len )
    {
        File path = new File( ds.getPath() );

        long allowed_cap = parse_capacity(ds.getMaxCapacity());

        // TEST IF FS IS FULL ANYWAY
        if (path.getFreeSpace() < len + 1024*1024)  // AT LEAST 1MB ON FS
            return false;

        // READ SIZE FROM DISK
        long act_cap = dsi.getCapacity();

        // CHECK IF OKAY
        if (allowed_cap > 0 && act_cap + len > allowed_cap)
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
    private String getMailPath()
    {
        return ds.getPath() + "/mail";
    }
    private String getIndexPath()
    {
        return ds.getPath() + "/index";
    }

    public static boolean  no_encryption = false;
    public void write_encrypted_file(RFCGenericMail msg, String password ) throws  VaultException
    {
        OutputStream bos = null;
        InputStream bis = null;
        byte[] buff = new byte[ CS_Constants.STREAM_BUFFER_LEN ];

        try
        {
//            int enc_mode = no_encryption ? RFCGenericMail.ENC_NONE : RFCGenericMail.ENC_AES;
            int enc_mode = dsi.getEncMode();
            File out_file = msg.create_unique_mailfile(getMailPath(), enc_mode );

            if (out_file == null)
            {
                throw new VaultException( ds, "Cannot create unique mailpath" );
            }
            //CryptTools.ENC_MODE encrypt = CryptTools.ENC_MODE.ENCRYPT;

            bis = msg.open_inputstream();
            OutputStream os = new FileOutputStream(out_file);



            if (enc_mode == RFCGenericMail.ENC_NONE)
            {
                LogManager.err_log_fatal( "No encryption!");
                bos = os;
            }
            else
            {
                bos = new CryptAESOutputStream(os,
                        m_ctx.getPrefs().get_KeyPBEIteration(),
                        m_ctx.getPrefs().get_KeyPBESalt(), password);
            }


            while (true)
            {
                int rlen = bis.read(buff);
                if (rlen == -1)
                {
                    break;
                }
                bos.write(buff, 0, rlen);
            }

            dsi.setCapacity( dsi.getCapacity() + msg.get_length());
            
        }
        catch (Exception e)
        {
            throw new VaultException(ds, e);
        }
        finally
        {
            if (bos != null)
            {
                try
                {
                    bos.close();
                }
                catch (IOException ex)
                {
                }
            }
            if (bis != null)
            {
                try
                {
                    bis.close();
                }
                catch (IOException ex)
                {
                }
            }
        }
    }
    public InputStream open_decrypted_mail_stream(RFCGenericMail msg, String password ) throws  VaultException
    {
        try
        {
            //CryptTools.ENC_MODE encrypt = CryptTools.ENC_MODE.DECRYPT;

            InputStream mis = msg.open_inputstream();

            if ( dsi.getEncMode() == RFCGenericMail.ENC_NONE)
                return mis;

            mis = new CryptAESInputStream(mis,
                        m_ctx.getPrefs().get_KeyPBEIteration(),
                        m_ctx.getPrefs().get_KeyPBESalt(), password);

            //InputStream is = CryptTools.create_crypt_AES_instream(m_ctx, mis, password, encrypt);

            return mis;
        }
        catch (Exception e)
        {
            throw new VaultException(ds, e);
        }
    }

    void flush() throws IndexException, VaultException
    {
        if (!is_open())
            return;
        
        if (is_index() && write_index != null)
        {
            try
            {
                synchronized( idx_lock )
                {
                    write_index.commit();
                }
            }
            catch (CorruptIndexException ex)
            {
                LogManager.log(Level.SEVERE, "Index on Diskspace " + ds.getPath() + " is corrupted: ", ex);
                throw new IndexException( ex.getMessage() );
            }
            catch (IOException ex)
            {
                LogManager.log(Level.SEVERE, "Index on Diskspace " + ds.getPath() + " cannot be accessed: ", ex);
                throw new IndexException( ex.getMessage() );
            }
            catch (Exception ex)
            {
                LogManager.log(Level.SEVERE, "Error in Index " + ds.getPath() + " cannot be accessed: ", ex);
                throw new IndexException( ex.getMessage() );
            }
        }
        if (is_data())
        {
            try
            {
                update_info();
            }
           
            catch (Exception ex)
            {
                LogManager.log(Level.SEVERE, "Update info failed on Diskspace " + ds.getPath() + ": ", ex);
                throw new VaultException( ex.getMessage() );
            }
        }
    }

    public boolean is_disabled()
    {
        try
        {
            int flags = Integer.parseInt(getDs().getFlags());
            if ((flags & CS_Constants.DS_DISABLED) == CS_Constants.DS_DISABLED)
            {
                return true;
            }
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return false;
    }
}
