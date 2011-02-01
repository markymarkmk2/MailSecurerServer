/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import com.thoughtworks.xstream.XStream;
import dimm.home.hiber_dao.DiskSpaceDAO;
import dimm.home.hibernate.HXStream;
import dimm.home.index.IndexJobEntry;
import dimm.home.index.IndexManager;
import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Notification.Notification;
import dimm.home.mailarchiv.Utilities.DirectoryEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import home.shared.SQL.SQLArrayResult;
import home.shared.Utilities.SizeStr;
import home.shared.hibernate.DiskSpace;
import home.shared.hibernate.Mandant;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Searchable;



/**
 *
 * @author mw
 */
class DocHashEntry
{
    String hash;
    Document doc;
    int ds_id;

    public DocHashEntry( String hash, Document doc, int ds_id )
    {
        this.hash = hash;
        this.doc = doc;
        this.ds_id = ds_id;
    }

    public String getHash()
    {
        return hash;
    }

    public Document getDoc()
    {
        return doc;
    }

    public int get_ds_id()
    {
        return ds_id;
    }

}

class HashChecker
{
    IndexReader reader;
    IndexSearcher searcher;
    final HashMap<String,DocHashEntry> map;

    public HashChecker( IndexReader reader )
    {
        this.reader = reader;
        searcher = new IndexSearcher(reader);
        map = new HashMap<String, DocHashEntry>();
    }

    public IndexSearcher getSearcher() throws CorruptIndexException, IOException
    {
        synchronized( map )
        {
            IndexReader new_reader = reader.reopen();
            if (new_reader != reader)
            {
                reader.close();
                reader = new_reader;
                searcher.close();
                searcher = new IndexSearcher(reader);
                map.clear();
            }
        }

        return searcher;
    }

    public IndexReader getReader()
    {
        return reader;
    }
    public void close() throws IOException
    {
        synchronized( map )
        {
            map.clear();
        }
        searcher.close();
        reader.close();
    }
    DocHashEntry get_short_term_hash( String hash )
    {
        synchronized( map )
        {
            return map.get(hash);
        }
    }

    void reopen() throws CorruptIndexException, IOException
    {
        synchronized( map )
        {
            IndexReader new_reader = reader.reopen();
            if (new_reader != reader)
            {
                reader.close();
                reader = new_reader;
                searcher.close();
                searcher = new IndexSearcher(reader);
            }
            map.clear();
        }
    }
    void add_entry( Document doc, int ds_id, String hash )
    {
        if (IndexManager.no_single_instance())
            return;

        synchronized( map )
        {
            DocHashEntry dhe = new DocHashEntry(hash, doc, ds_id);
            map.put(hash, dhe);
        }
    }

}
public class DiskSpaceHandler
{

    DiskSpace ds;
    DiskSpaceInfo dsi;
    boolean info_touched;

    boolean _open;
    IndexWriter write_index;
    //AsyncIndexWriter async_index_writer;
    ThreadPoolExecutor write_index_thread_pool;

    MandantContext m_ctx;  
    public final String idx_lock = "idx";
    public final String data_lock = "data";
    private long next_flush_optimize_ts;
    boolean low_space_left;
    boolean no_space_left;

    public final String DSINFO_FILENAME = "dsinfo.xml";

    private static long FLUSH_OPTIMIZE_CYCLE_MS = 60*60*1000;
    private static final long MIN_FREE_SPACE = 100000000l;  // 100 MB
    private static final long LOW_FREE_SPACE = 10000000000l; // 10GB

    public static int LUC_OPTIMIZE_FILES = 10; // OPTIMIZE DOWN TO 10 FILES

    HashChecker hash_checker;


    public DiskSpaceHandler( MandantContext _m_ctx, DiskSpace _ds )
    {
        m_ctx = _m_ctx;
        ds = _ds;        
        _open = false;
        info_touched = false;

        FLUSH_OPTIMIZE_CYCLE_MS = Main.get_long_prop(GeneralPreferences.OPTIMIZE_FLUSH_CYCLETIME, FLUSH_OPTIMIZE_CYCLE_MS);
        next_flush_optimize_ts = 0;  // OPTIMIZE AT STARTUP
//        next_flush_optimize_ts = System.currentTimeMillis() + FLUSH_OPTIMIZE_CYCLE_MS;
    }
    public ExecutorService get_write_index_thread_pool()
    {
        return write_index_thread_pool;
    }
    public Future<IndexJobEntry> execute_write( final IndexJobEntry aThis )
    {

        Callable<IndexJobEntry> call = new Callable<IndexJobEntry>()
        {

            @Override
            public IndexJobEntry call() throws Exception
            {
                aThis.handle_post_index();
                return aThis;
            }
        };

        try
        {
            Future<IndexJobEntry> result = write_index_thread_pool.submit(call);

            return result;
        }
        catch (Exception ex)
        {
            
            LogManager.msg_archive( LogManager.LVL_ERR,  "Cannot start background write task ("
                    + write_index_thread_pool.getActiveCount() + "/" + write_index_thread_pool.getPoolSize() + "/" + write_index_thread_pool.isTerminating() +
                    "/" + write_index_thread_pool.isTerminated(), ex);
        }
        return null;
    }


    HashChecker get_hash_checker()
    {
        return hash_checker;
    }

    public boolean is_open()
    {
        return _open;
    }
    public int get_enc_mode()
    {
        if (dsi == null)
        {
            try
            {
                read_info();
            }
            catch (VaultException vaultException)
            {
            }
        }

        return dsi.getEncMode();
    }
    public RFCGenericMail.FILENAME_MODE get_fmode()
    {
        if (dsi == null)
        {
            try
            {
                read_info();
            }
            catch (VaultException vaultException)
            {
            }
        }
        return dsi.getFmode();
    }


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
                LogManager.msg_archive( LogManager.LVL_ERR,  "commit_index failed", ex);
            }
            catch (IOException ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR,  "commit_index failed", ex);
            }
        }
    }

    boolean test_flag( DiskSpace ds, int flag )
    {
        int f = Integer.parseInt(ds.getFlags());
        return ((f & flag) == flag);
    }

    public IndexReader create_read_index() throws VaultException, IndexException
    {

        try
        {
            if (!is_open())
            {
                if (!exists_info() && (ds.getStatus().compareTo( CS_Constants.DS_EMPTY) == 0))
                {
                    create();
                }
                open();
            }
            
            if (is_index())
            {
                return IndexManager.open_read_index(getIndexPath());
            }
            else
                throw new VaultException( ds, "Cannot open read index on non-index ds: " + ds.getPath());
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot open read index: " + iex.getMessage());
        }

    }

    int get_indexthreads()
    {
        return (int)m_ctx.getPrefs().get_long_prop( MandantPreferences.INDEX_THREADS, MandantPreferences.DFTL_INDEX_THREADS );
    }

   
    public void open_write_index_pool()
    {

        if (write_index_thread_pool != null)
        {
            m_ctx.getThreadWatcher().shutdown_thread_pool(write_index_thread_pool, 5000);
        }


        write_index_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "WriteIndex" , 1, 20 );
/*        OfferBlockingQueue<Runnable> run_queue = new OfferBlockingQueue<Runnable>(20);
        write_index_thread_pool = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, run_queue);*/
        //write_index_thread_pool = Executors.newSingleThreadExecutor();
    }
    public IndexWriter create_write_index() throws VaultException, IndexException
    {
        try
        {
            if (!is_open())
            {
                open();
            }
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    write_index = m_ctx.get_index_manager().create_index(getIndexPath(), dsi.getLanguage());
                    write_index.commit();

                    open_write_index_pool();
//                    async_index_writer = new AsyncIndexWriter(get_indexthreads());

                    return write_index;
                }
            }
            else
                return null;
                //throw new VaultException( ds, "Cannot create write index on non-index ds: " + ds.getPath());
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot create write index: " + iex.getMessage());
        }        
    }
  
    public IndexWriter open_write_index() throws VaultException, IndexException
    {
        try
        {
            if (!is_open())
            {
                open();
            }
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    if (write_index != null)
                        return write_index;

                    // WE ALLOW INDEX CREATION ONLY IF WE HAVE NO INFO OBJECT
                    boolean  can_create_index = !exists_info();

                    write_index = m_ctx.get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), can_create_index);

                    open_write_index_pool();
                    /*
                    if (async_index_writer != null)
                    {
                        async_index_writer.close();
                    }
                    async_index_writer = new AsyncIndexWriter(get_indexthreads());*/
                }
                return write_index;
            }
            else
                return null;
//            else
//                throw new VaultException( ds, "Cannot open write index on non-index ds: " + ds.getPath());
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
                    if (write_index_thread_pool != null)
                    {
                        m_ctx.getThreadWatcher().shutdown_thread_pool(write_index_thread_pool, 5000);
                    }
/*                    if (async_index_writer != null)
                    {
                        async_index_writer.close();
                        async_index_writer = null;
                    }*/
                    if (write_index != null)
                    {
                        write_index.commit();
                        write_index.close();
                        write_index = null;
                    }
                    if (hash_checker != null)
                    {
                        hash_checker.close();
                        hash_checker = null;
                    }
                }
            }
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, "Cannot close write index: " , iex);
        }
    }



    public void open() throws VaultException, IndexException, CorruptIndexException, IOException
    {
        if (is_open())
        {
            close();
        }
        else
        {
            if (!exists_info() && (ds.getStatus().compareTo( CS_Constants.DS_EMPTY) == 0))
            {
                create();
            }
        }


        read_info();

       _open = true;
    }

    public Analyzer create_read_analyzer()
    {


        if (!is_open())
        {

            try
            {
                open();
            }
            catch (Exception ex)
            {
                LogManager.msg_archive(LogManager.LVL_ERR, "Cannot read_info on Diskspace " + ds.getPath(), ex);
                return null;
            }
        }
        return m_ctx.get_index_manager().create_analyzer(dsi.getLanguage(), /*do_indexs*/ false);
    }

    public void create() throws VaultException
    {
        File path = new File( ds.getPath() );
        if (path.getParentFile() == null || !path.getParentFile().exists())
        {
            throw new VaultException( ds, Main.Txt("Missing_parent_directory"));
        }
        if (!path.exists())
        {
            throw new VaultException( ds, Main.Txt("Path_for_diskspace_doesnt_exist"));
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
                throw new VaultException( ds, Main.Txt("Contains_index_data_and_info,_should_be_empty") );
        }

        // CREATE NEW DISKINFO UND SET DEFAULT VALUES
        dsi = new DiskSpaceInfo();
        dsi.setLanguage( m_ctx.getPrefs().get_language());
        dsi.setEncMode( RFCFileMail.dflt_encoded ? RFCFileMail.dflt_encoding : RFCFileMail.ENC_NONE);
        dsi.setFmode( m_ctx.getPrefs().get_boolean_prop(MandantPreferences.DSH_HOUR_DIRS, true) ?
            RFCGenericMail.FILENAME_MODE.H_OK_DIR_MS_FILE : RFCGenericMail.FILENAME_MODE.HMS_FILE);

        info_touched = true;

        try
        {
            // TOUCH (NOT OPEN!) INDEX
            if (is_index())
            {
                synchronized( idx_lock )
                {
                    write_index = m_ctx.get_index_manager().open_index(getIndexPath(), dsi.getLanguage(), /*can_create*/ true);
                    write_index.commit();
                    write_index.close();
                    write_index = null;
                }
            }
        }
        catch (IOException iex)
        {
            throw new VaultException( ds, Main.Txt("Cannot_create_index") , iex);
        }


        update_info();

        try
        {
            write_info_enc_object( ds, "ds.xml" );
            write_info_enc_object( ds.getDiskArchive(), "da.xml" );
            write_info_enc_object( ds.getDiskArchive().getMandant(), "ma.xml" );
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, "Cannot write XML params: " , ex);
        }
    }

    boolean exists_info_enc_object( String filename )
    {
        File file = new File(ds.getPath() + "/" + filename + ".enc");
        return file.exists();
    }
    private void write_info_enc_object( Object o, String filename ) throws FileNotFoundException, IOException
    {
        File save_file = new File(ds.getPath() + "/" + filename + ".enc.sav");
        if (save_file.exists())
            save_file.delete();
        File file = new File(ds.getPath() + "/" + filename + ".enc");
        if (file.exists())
            file.renameTo(save_file);

        OutputStream bos = new FileOutputStream(file);
        try
        {
            CryptAESOutputStream cos = new CryptAESOutputStream( bos, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), CS_Constants.get_InternalPassPhrase() );
            bos = cos;
        }
        catch (Exception exc)
        {
            fall_back_write_info_object( o, filename );
            return;
        }

        HXStream xs = new HXStream();       
        xs.toXML(o, bos);
        bos.flush();
        bos.close();

        // GET RID OF OLD XML DATA
        File old_file = new File(ds.getPath() + "/" + filename + ".sav");
        if (old_file.exists())
            old_file.delete();
        old_file = new File(ds.getPath() + "/" + filename );
        if (old_file.exists())
            old_file.delete();

    }

    private Object read_info_enc_object( String filename ) throws FileNotFoundException, IOException
    {
        if (!exists_info_enc_object( filename ))
        {
            return fall_back_read_info_object(  filename );
        }

        
        InputStream bis = new FileInputStream(ds.getPath() + "/" + filename + ".enc");
        try
        {
            CryptAESInputStream cis = new CryptAESInputStream( bis, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), CS_Constants.get_InternalPassPhrase() );
            bis = cis;
        }
        catch (Exception exc)
        {
            return fall_back_read_info_object(  filename );
        }

        XStream xs = new XStream();
        Object o = xs.fromXML(bis);
        bis.close();
        return o;
    }
    private Object read_sav_info_enc_object( String filename ) throws FileNotFoundException, IOException
    {


        InputStream bis = new FileInputStream(ds.getPath() + "/" + filename + ".enc.sav");
        try
        {
            CryptAESInputStream cis = new CryptAESInputStream( bis, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), CS_Constants.get_InternalPassPhrase() );
            bis = cis;
        }
        catch (Exception exc)
        {
            return fall_back_read_info_object(  filename + ".sav" );
        }

        XStream xs = new XStream();
        Object o = xs.fromXML(bis);
        bis.close();
        return o;
    }



    private void fall_back_write_info_object( Object o, String filename ) throws FileNotFoundException, IOException
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

    private Object fall_back_read_info_object( String filename ) throws FileNotFoundException, IOException
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

    private boolean exists_info()
    {
        if (exists_info_enc_object(DSINFO_FILENAME))
            return true;

        File f = new File(ds.getPath() + "/" + DSINFO_FILENAME);
        return f.exists();
    }
    private void read_info() throws VaultException
    {
        try
        {
            synchronized( data_lock )
            {
                Object o = read_info_enc_object( DSINFO_FILENAME );

                if (o instanceof DiskSpaceInfo)
                {
                    dsi = (DiskSpaceInfo)o;
                    info_touched = false;
                }
                else
                    throw new VaultException( ds, "Invalid info file" );

                Object m = null;
                try
                {
                    m = read_info_enc_object("ma.xml");
                }
                catch (Exception iOException)
                {
                    write_info_enc_object( ds.getDiskArchive().getMandant(), "ma.xml" );
                    m = read_info_enc_object("ma.xml");
                }                

                if ( m != null && m  instanceof Mandant)
                {
                    Mandant ma = (Mandant)m;
                    LogManager.msg_archive( LogManager.LVL_INFO, "Found Mandant " + ma.getName() + " on DS " + ds.getPath());
                    
                    // CHEKC IF WE HAVE NEW ENCODED SAVE FILE (WITH COMPLETE STRUCTS)
                    if (ma.getDiskArchives().size() > 0)
                    {
                        // DELETE UNENCODED FILES
                        File f = new File( ds.getPath() + "/" + "ds.xml" );
                        if (f.exists())
                            f.delete();
                        f = new File( ds.getPath() + "/" + "da.xml" );
                        if (f.exists())
                            f.delete();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            try
            {
                Object o = read_sav_info_enc_object(DSINFO_FILENAME);
                if (o instanceof DiskSpaceInfo)
                {
                    LogManager.msg_archive( LogManager.LVL_WARN, Main.Txt("Recovering_from_broken_ds_info_object") + ": " + ex.getMessage());
                    dsi = (DiskSpaceInfo)o;
                    info_touched = true;
                    update_info();

                    return;
                }
            }
            catch (Exception iOException)
            {
                LogManager.msg_archive( LogManager.LVL_WARN, Main.Txt("Recovering_from_broken_ds_info_object_failed") + ": " + iOException.getMessage());
            }
            // DATAPATH EXISTS AND INFO IS BROKEN???
            File mail_path = new File(getMailPath());
            if (mail_path.exists())
            {
                // WE ARE LOST, BUILD A NEW ONE FROM SCRATCH, WE PROBABLY NEED TO REINDEX
                dsi = new DiskSpaceInfo();
                dsi.setLanguage( m_ctx.getPrefs().get_language());
                LogManager.msg_archive( LogManager.LVL_WARN, Main.Txt("Building_entry_list") + " " + mail_path.getAbsolutePath());
                DirectoryEntry de = new DirectoryEntry( mail_path );
                Iterator<File> it = de.get_file_iterator();
                LogManager.msg_archive( LogManager.LVL_WARN, Main.Txt("Done_building_entry_list"));
                int enc_mode = RFCFileMail.dflt_encoded ? RFCFileMail.dflt_encoding : RFCFileMail.ENC_NONE;
                long capacity = 0;

                LogManager.msg_archive( LogManager.LVL_WARN, Main.Txt("Rescanning_disk_space") + " " + ds.getPath());
                while (it.hasNext())
                {
                    File f = it.next();

                    // DETECT ENCODING
                    int local_enc_mode = RFCFileMail.get_encrypt_mode_for_suffix( f.getName() );
                    if (local_enc_mode != RFCFileMail.ENC_NONE)
                        enc_mode = local_enc_mode;

                    capacity += f.length();
                }
                
                dsi.setCapacity(capacity);
                dsi.setEncMode(  enc_mode );
                info_touched = true;
                LogManager.msg_archive( LogManager.LVL_ERR, Main.Txt("We_recovered_disk_info_and_need_reindex_for") + ": " + ds.getPath());
                update_info();

                return;
            }
            
            throw new VaultException( ds, "Cannot read info file: " + ex.getMessage());
        }
    }
    public void close() throws VaultException, IndexException, CorruptIndexException, IOException
    {
        flush();
        info_touched = true;
        update_info();

        close_write_index();
        
        _open = false;
    }

    private void update_info() throws VaultException
    {
        // ONLY UPDATE IF DATA CHANGED
        if (!info_touched)
            return;

        try
        {
            synchronized( data_lock )
            {
                write_info_enc_object( dsi, DSINFO_FILENAME );
                write_info_enc_object( ds.getDiskArchive().getMandant(), "ma.xml" );
                info_touched = false;
            }
        }
        catch (Exception ex)
        {
            throw new VaultException( ds, Main.Txt("Cannot_write_info_file") + ": " , ex);
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
    public DiskSpaceInfo getDsi()
    {
        return dsi;
    }


    public void add_message_info( RFCGenericMail msg )
    {
        long last_capacity = dsi.getCapacity();
        dsi.incCapacity( msg.get_length() );
        long new_capacity = dsi.getCapacity();

        check_capacity_limits( last_capacity, new_capacity );

        dsi.setLastEntryTS(msg.getDate().getTime());
        if (dsi.getFirstEntryTS() == 0)
            dsi.setFirstEntryTS(dsi.getLastEntryTS());

        info_touched = true;
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
        dsi.decCapacity(f.length());
        info_touched = true;
    }

    public void delete_mail( long time, int enc_mode, RFCGenericMail.FILENAME_MODE fmode   ) throws VaultException
    {
        String parent_path = getMailPath();
        String absolutePath = RFCFileMail.get_mailpath_from_time( parent_path, time, enc_mode, fmode );


        File mail_file = new File( absolutePath );
        if (!mail_file.exists())
        {
           throw new VaultException( ds, "Cannot delete mail file for " + time + ": " + absolutePath );
        }
        try
        {
            mail_file.delete();
            dsi.decCapacity(mail_file.length());
            info_touched = true;
        }
        catch (Exception e)
        {
           throw new VaultException( ds, "Cannot delete mail file for " + time + ": " + e.getLocalizedMessage() );
        }
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

    public long build_time_from_path( String absolutePath, int enc_mode, RFCGenericMail.FILENAME_MODE fmode ) throws VaultException
    {
        String rel_path = absolutePath.substring(getMailPath().length());
        try
        {
            return RFCFileMail.get_time_from_mailfile(rel_path, enc_mode, fmode);
        }
        catch (ParseException parseException)
        {
        }
        // TRY OTHER VARIANT MAYBE WE HAVE SWITCHED?
        if (fmode == RFCGenericMail.FILENAME_MODE.HMS_FILE)
            fmode = RFCGenericMail.FILENAME_MODE.H_OK_DIR_MS_FILE;
        try
        {
            return RFCFileMail.get_time_from_mailfile(rel_path, enc_mode, fmode);
        }
        catch (ParseException parseException)
        {
            throw new VaultException("Invalid path for build_time_from_path: " + absolutePath,  parseException );
        }
    }
    
    public RFCGenericMail get_mail_from_time( long time, int enc_mode, RFCGenericMail.FILENAME_MODE fmode  ) throws VaultException
    {
        String parent_path = getMailPath();
        String absolutePath = RFCFileMail.get_mailpath_from_time( parent_path, time, enc_mode, fmode );

        File mail_file = new File( absolutePath );
        if (!mail_file.exists())
        {
            // TRY OTHER VARIANT MAYBE WE HAVE SWITCHED?
            if (fmode == RFCGenericMail.FILENAME_MODE.HMS_FILE)
                fmode = RFCGenericMail.FILENAME_MODE.H_OK_DIR_MS_FILE;
            else
                fmode = RFCGenericMail.FILENAME_MODE.HMS_FILE;

           absolutePath = RFCFileMail.get_mailpath_from_time( parent_path, time, enc_mode, fmode );
        }
        mail_file = new File( absolutePath );
        if (!mail_file.exists())
        {
           throw new VaultException( ds, "Cannot retrieve mail file for " + time + ": " + absolutePath );
        }

        Date d = new Date(time);
        
        RFCFileMail mail = new RFCFileMail( mail_file, d, (enc_mode == RFCFileMail.ENC_NONE) ? false : true);
        if (enc_mode != RFCGenericMail.ENC_NONE)
        {
            Vault vault = m_ctx.get_vault_for_ds_idx(ds.getId());
            mail.set_encryption(vault.get_password(), CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt());
        }
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
                long time = build_time_from_path(f.getAbsolutePath(), local_dsi.getEncMode(), local_dsi.getFmode() );
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
                LogManager.msg_archive( LogManager.LVL_ERR, "Invalid path in info_recursive", vaultException);
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

        // TODO: CHECK ENCMODE AND FMODE
        dsi = info_recursive( path, local_dsi );
        info_touched = true;
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
            LogManager.msg_archive( LogManager.LVL_ERR,  "Invalid capacity string " + s, ex);
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
        return SQLArrayResult.decode(ds.getPath()) + "/mail";
    }
    public String getIndexPath()
    {
        return SQLArrayResult.decode(ds.getPath()) + "/index";
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
            File out_file = msg.create_unique_mailfile(getMailPath(), enc_mode, dsi.getFmode() );

            if (out_file == null)
            {
                throw new VaultException( ds, "Cannot create unique mailpath" );
            }
            //CryptTools.ENC_MODE encrypt = CryptTools.ENC_MODE.ENCRYPT;

            bis = msg.open_inputstream();
            OutputStream os = new FileOutputStream(out_file);



            if (enc_mode == RFCGenericMail.ENC_NONE)
            {
                LogManager.msg_archive( LogManager.LVL_ERR,  "No encryption!");
                bos = os;
            }
            else
            {
                bos = new CryptAESOutputStream(os, CS_Constants.get_KeyPBEIteration(), CS_Constants.get_KeyPBESalt(), password);
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
            bos.close();
            bos = null;
                       
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
            
            
            // ADD CAPACITY COUNTER
            add_message_info(msg);

        }
    }/*
    public InputStream open_decrypted_mail_stream(RFCGenericMail msg, String password ) throws  VaultException
    {
        try
        {
            //CryptTools.ENC_MODE encrypt = CryptTools.ENC_MODE.DECRYPT;

            InputStream mis = msg.open_inputstream();

            if ( dsi.getEncMode() == RFCGenericMail.ENC_NONE)
                return mis;

            mis = new CryptAESInputStream(mis,
                        CS_Constants.get_KeyPBEIteration(),
                        CS_Constants.get_KeyPBESalt(), password);

            //InputStream is = CryptTools.create_crypt_AES_instream(m_ctx, mis, password, encrypt);

            return mis;
        }
        catch (Exception e)
        {
            throw new VaultException(ds, e);
        }
    }*/
    private static long size_recursive(File f)
    {
        long len = 0;
        if (f.isDirectory())
        {
            File[] f_list = f.listFiles();
            for (int i = 0; i < f_list.length; i++)
            {
                len += size_recursive( f_list[i] );
            }
        }
        else
        {
            len = f.length();
        }
        return len;
    }

    void flush() throws IndexException, VaultException
    {
        flush( false);
    }

    void flush(boolean and_optimize) throws IndexException, VaultException
    {
        if (!is_open())
        {
            check_free_space();
            return;
        }
        
        if (is_index() && write_index != null)
        {
            boolean optimize = false;
            if (System.currentTimeMillis() > next_flush_optimize_ts || and_optimize)
            {
                next_flush_optimize_ts = System.currentTimeMillis() + FLUSH_OPTIMIZE_CYCLE_MS;
                optimize = true;
            }
            try
            {
                synchronized( idx_lock )
                {
                    write_index.commit();
                    if (optimize)
                    {
                        LogManager.msg_archive( LogManager.LVL_INFO, Main.Txt("Optimizing_index_on_diskspace") + " " + ds.getPath());

                        write_index.optimize(LUC_OPTIMIZE_FILES);
                        
                        LogManager.msg_archive( LogManager.LVL_INFO, Main.Txt("Optimizing_finished_on_diskspace") + " " + ds.getPath());
                    }
                }
                if (hash_checker != null)
                {
                    hash_checker.reopen();
                }

                String path = getIndexPath();
                long index_capacity = size_recursive( new File(path) );
                if (!is_data())
                {
                    dsi.setCapacity(index_capacity);
                    info_touched = true;
                    update_info();
                }
            }
            catch (CorruptIndexException ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR,  "Index on Diskspace " + ds.getPath() + " is corrupted: ", ex);
                throw new IndexException( ex.getMessage() );
            }
            catch (IOException ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR,  "Index on Diskspace " + ds.getPath() + " cannot be accessed: ", ex);
                throw new IndexException( ex.getMessage() );
            }
            catch (Exception ex)
            {
                LogManager.msg_archive( LogManager.LVL_ERR,  "Error in Index " + ds.getPath() + " cannot be accessed: ", ex);
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
                LogManager.msg_archive( LogManager.LVL_ERR,  "Update info failed on Diskspace " + ds.getPath() + ": ", ex);
                throw new VaultException( ex.getMessage() );
            }
        }
        // CHECK FOR HARD DISK LIMITS
        check_free_space();
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

    boolean rebuild_lock;
    public void lock_for_rebuild()
    {
        rebuild_lock = true;
        LogManager.msg_archive( LogManager.LVL_WARN,  "ReIndex Lock fehlt");
    }
    public void unlock_for_rebuild()
    {
        rebuild_lock = false;
        LogManager.msg_archive( LogManager.LVL_WARN,  "ReIndex Lock fehlt");
    }
    public boolean islock_for_rebuild()
    {
        return rebuild_lock ;
    }


    private void set_no_space_left( boolean b)
    {
        // DETECT STATUS CHANGE
        if (no_space_left != b)
        {
            no_space_left = b;

            if (no_space_left)
            {
                Notification.throw_notification(m_ctx.getMandant(), Notification.NF_FATAL_ERROR,
                    Main.Txt("No_space_left_in_disk_space") + ": " + ds.getPath());
            }
            else
            {
                Notification.throw_notification(m_ctx.getMandant(), Notification.NF_INFORMATIVE,
                    Main.Txt("Space_in_disk_space_is_sufficient_again") + ": " + ds.getPath());
            }
        }
    }
    private void set_low_space_left( boolean b, double free)
    {
        if (low_space_left != b)
        {
            low_space_left = b;

            if (low_space_left)
            {
                String space = new SizeStr( free ).toString() + "B";
                Notification.throw_notification(m_ctx.getMandant(), Notification.NF_WARNING,
                    Main.Txt("Low_space_left_in_disk_space") + ": " + ds.getPath() + ": < " + space);
            }
        }
    }

    // CALLED DURING FLUSH
    void check_free_space()
    {
        File tmp_path = new File( ds.getPath() );
        long l = tmp_path.getFreeSpace();
        //l = Long.MAX_VALUE;
        double free = l;

        set_no_space_left( free <MIN_FREE_SPACE );
        set_low_space_left( free <LOW_FREE_SPACE,  free);
    }

    
    public boolean no_space_left()
    {
        return no_space_left;
    }

    // CALLED FOR EVERY MAIL, KEEP IT SIMPLE
    private void check_capacity_limits( long last_capacity, long new_capacity )
    {
        long allowed_cap = parse_capacity(ds.getMaxCapacity());
        int last_percent = (int)((last_capacity * 100) / allowed_cap);
        int new_percent = (int)((new_capacity * 100) / allowed_cap);

        int last_perdek = last_percent / 10;
        int new_perdek  = new_percent / 10;

        if ((last_perdek != new_perdek) || (new_perdek >= 9 && last_percent != new_percent))
        {
            // DONT BOTHER IF LESS THAN 50%
            if (new_perdek < 5)
                return;

            int level = Notification.NF_INFORMATIVE;
            if (new_perdek >= 7)
                level = Notification.NF_WARNING;
            if (new_perdek >= 9)
                level = Notification.NF_ERROR;

            Notification.throw_notification(m_ctx.getMandant(), level,
                    Main.Txt("Disk_space")  + " " + ds.getPath() + " " + Main.Txt("is_filled_by") + " >= " + Integer.toString(new_perdek * 10) + "%");

        }
    }

    // CALLED FOR EVERY MAIL, KEEP IT SIMPLE
    public boolean checkCapacity(  long len )
    {
        long allowed_cap = parse_capacity(ds.getMaxCapacity());

        // READ SIZE FROM DISK
        long act_cap = dsi.getCapacity();

        // CHECK IF OKAY
        if (allowed_cap > 0 && act_cap + len > allowed_cap)
            return false;

        return true;
    }

    public void create_hash_checker() throws VaultException, IOException, IndexException
    {
        if (IndexManager.no_single_instance())
            return;

        if (!is_index())
            return;

        if (hash_checker != null)
        {
            hash_checker.close();
        }
            
        IndexReader reader = create_read_index();
        hash_checker = new HashChecker(reader);
    }
    public void close_hash_checker() throws VaultException, IOException
    {
        if (hash_checker != null)
        {
            hash_checker.close();
            hash_checker = null;
        }
    }

    public void add_hash_entry( String txt_hash, Document doc, int ds_id )
    {
        if (IndexManager.no_single_instance())
            return;

        if (hash_checker != null)
            hash_checker.add_entry(doc, ds_id, txt_hash);
    }

    DocHashEntry has_hash_entry( String hash )
    {
        if (hash_checker != null)
            return hash_checker.get_short_term_hash(hash);
        return null;
    }

    Searchable get_searcher() throws CorruptIndexException, IOException
    {
        if (hash_checker != null)
            return hash_checker.getSearcher();
        return null;
    }

}
