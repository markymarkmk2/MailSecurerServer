/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;
import dimm.home.extraction.Extractor;
import dimm.home.mail.RFCFileMail;
import dimm.home.mail.RFCMimeMail;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import home.shared.hibernate.MailHeaderVariable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimePart;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;

class MyMimetype
{

    String mimetype;

    MyMimetype( String m )
    {
        mimetype = m;
    }

    boolean is_type( String t )
    {
        return mimetype.compareToIgnoreCase(t) == 0;
    }
}

class IndexJobEntry
{

    private MandantContext m_ctx;
    String unique_id;
    int da_id;
    int ds_id;
    private DiskSpaceHandler index_dsh;
    RFCFileMail msg;
    boolean delete_after_index;

    public IndexJobEntry( MandantContext m_ctx, String unique_id, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCFileMail msg, boolean delete_after_index )
    {
        this.m_ctx = m_ctx;
        this.unique_id = unique_id;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.index_dsh = index_dsh;
        this.msg = msg;
        this.delete_after_index = delete_after_index;
    }

    void handle_index()
    {
        Document doc = new Document();
        IndexManager idx = m_ctx.get_index_manager();
        try
        {
            // WE USE data_dsh BECAUSE WE ADD PARAMS OF IT TO INDEX (DA, DS, MA)
            idx.index_mail_file(m_ctx, unique_id, da_id, ds_id, msg, doc);

            IndexWriter writer = index_dsh.get_write_index();
            writer.addDocument(doc);

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
                msg.delete_msg();
            }
        }
        catch (Exception ex)
        {
            LogManager.log(Level.SEVERE, "Error occured while indexing message: ", ex);
        }
    }
}

/**
 *
 * @author mw
 */
public class IndexManager extends WorkerParent
{

    protected Charset utf8_charset = Charset.forName("UTF-8");
    ArrayList<MailHeaderVariable> header_list;
    final ArrayList<IndexJobEntry> index_job_list;
    //boolean do_index_body = false;
    boolean do_index_attachments = false;
    boolean do_detect_lang = false;
    public static final String FLD_ATTACHMENT = "FLDN_ATTACHMENT";
    public static final String FLD_ATTACHMENT_NAME = "FLDN_ATTNAME";
    public static final String FLD_BODY = "FLDN_BODY";
    public static final String FLD_UID_NAME = "FLDN_UID";
    public static final String FLD_LANG = "FLDN_LANG";
    public static final String FLD_HEADERVAR_VALUE = "FLDN_HEADERVAR_VALUE";
    public static final String FLD_HEADERVAR_NAME = "FLDN_HEADERVAR_NAME";
    public static final String FLD_MA = "FLDN_MA";
    public static final String FLD_DA = "FLDN_DA";
    public static final String FLD_DS = "FLDN_DS";
    public static final String FLD_TM = "FLDN_TM";
    Map<String, String> analyzerMap;
    Extractor extractor;
    MandantContext m_ctx;
    private SwingWorker idle_worker;

    public IndexManager( MandantContext _m_ctx, ArrayList<MailHeaderVariable> _header_list, boolean _do_index_attachments )
    {
        super("Indexmanager");
        m_ctx = _m_ctx;
        header_list = _header_list;
        do_index_attachments = _do_index_attachments;

        extractor = new Extractor(m_ctx);

        analyzerMap = new LinkedHashMap<String, String>();
        analyzerMap.put("en", "org.apache.lucene.analysis.StandardAnanlyzer");
        analyzerMap.put("pt", "org.apache.lucene.analysis.br.BrazilianAnalyzer");
        analyzerMap.put("zh", "org.apache.lucene.analysis.cn.ChineseAnalyzer");
        analyzerMap.put("cs", "org.apache.lucene.analysis.cz.CzechAnalyzer");
        analyzerMap.put("de", "org.apache.lucene.analysis.de.GermanAnalyzer");
        analyzerMap.put("el", "org.apache.lucene.analysis.el.GreekAnalyzer");
        analyzerMap.put("fr", "org.apache.lucene.analysis.fr.FrenchAnalyzer");
        analyzerMap.put("nl", "org.apache.lucene.analysis.nl.DutchAnalyzer");
        analyzerMap.put("ru", "org.apache.lucene.analysis.ru.RussianAnalyzer");
        analyzerMap.put("ja", "org.apache.lucene.analysis.cjk.CJKAnalyzer");
        analyzerMap.put("ko", "org.apache.lucene.analysis.cjk.CJKAnalyzer");
        analyzerMap.put("th", "org.apache.lucene.analysis.th.ThaiAnalyzer");
        analyzerMap.put("tr", "org.apache.lucene.analysis.tr.TurkishAnalyzer");

        index_job_list = new ArrayList<IndexJobEntry>();
    }

    public IndexWriter open_index( String path, String language, boolean do_index ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);

        Analyzer analyzer = create_analyzer(language, do_index);

        if (IndexWriter.isLocked(dir))
        {
            Main.err_log("Unlocking already locked IndexWriter");
            IndexWriter.unlock(dir);
        }

        IndexWriter writer = new IndexWriter(dir, analyzer, false, IndexWriter.MaxFieldLength.UNLIMITED /*new IndexWriter.MaxFieldLength(50000)*/);

        return writer;
    }

    public IndexReader open_read_index( String path ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);


        IndexReader reader = IndexReader.open(dir, /*rd_only*/ true);

        return reader;
    }

    public IndexWriter create_index( String path, String language ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);

        Analyzer analyzer = create_analyzer(language, true);
        IndexWriter writer = new IndexWriter(dir, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED /*new IndexWriter.MaxFieldLength(50000)*/);
        return writer;
    }

    public void close_index( IndexWriter writer ) throws IOException
    {
        writer.commit();
        writer.close();
    }

    protected Analyzer create_analyzer( String language, boolean do_index )
    {
        Analyzer analyzer = null;
        if (language == null)
        {
            language = "en";
        }

        String className = null;

        try
        {
            if (analyzerMap.containsKey(language))
            {
                className = (String) analyzerMap.get(language);
            }
            if (className == null)
            {
                className = (String) analyzerMap.get("en");
            }

            Class analyzerClass = Class.forName(className);
            analyzer = (Analyzer) analyzerClass.newInstance();
        }
        catch (Exception e)
        {
            analyzer = new StandardAnalyzer();
        }
        PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(analyzer);

        if (do_index)
        {
            wrapper.addAnalyzer("to", new EmailAnalyzer());
            wrapper.addAnalyzer("from", new EmailAnalyzer());
            wrapper.addAnalyzer("cc", new EmailAnalyzer());
            wrapper.addAnalyzer("bcc", new EmailAnalyzer());
            wrapper.addAnalyzer("deliveredto", new EmailAnalyzer());
            wrapper.addAnalyzer("attachname", new FileNameAnalyzer());
        }
        else
        {
            wrapper.addAnalyzer("to", new WhitespaceAnalyzer());
            wrapper.addAnalyzer("from", new WhitespaceAnalyzer());
            wrapper.addAnalyzer("cc", new WhitespaceAnalyzer());
            wrapper.addAnalyzer("bcc", new WhitespaceAnalyzer());
            wrapper.addAnalyzer("deliveredto", new WhitespaceAnalyzer());
            wrapper.addAnalyzer("attachname", new FileNameAnalyzer());
        }
        return wrapper;
    }

    public void index_mail_file( MandantContext m_ctx, String unique_id, int da_id, int ds_id, RFCFileMail mail_file, Document doc ) throws MessagingException, IOException, IndexException
    {
        RFCMimeMail mime_msg = new RFCMimeMail();
        mime_msg.parse(mail_file);


        doc.add(new Field(FLD_UID_NAME, unique_id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FLD_MA, Integer.toString(m_ctx.getMandant().getId()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FLD_DA, Integer.toString(da_id), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FLD_DS, Integer.toString(ds_id), Field.Store.YES, Field.Index.NOT_ANALYZED));

        // TIME AS HEX
        doc.add(new Field(FLD_TM, Long.toString(mail_file.getDate().getTime(), 16), Field.Store.YES, Field.Index.NOT_ANALYZED));


        Message msg = mime_msg.getMsg();
        try
        {
            Enumeration mail_header_list = msg.getAllHeaders();

            /*            while (mail_header_list.hasMoreElements())
            {
            Object h = mail_header_list.nextElement();
            if (h instanceof Header)
            {
            Header ih = (Header) h;
            System.out.println("N: " + ih.getName() + " V: " + ih.getValue());
            }
            }*/
            index_headers(doc, unique_id, msg.getAllHeaders());

            Object content = msg.getContent();
            if (content instanceof Multipart)
            {
                Multipart mp = (Multipart) content;

                index_mp_content(doc, unique_id, mp);
            }
            else if (content instanceof Part)
            {
                Part p = (Part) content;
                index_part_content(doc, unique_id, p);
            }
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            LogManager.log(Level.SEVERE, null, fileNotFoundException);
        }
        catch (IOException iox)
        {
            LogManager.log(Level.SEVERE, null, iox);
        }
        catch (MessagingException messagingException)
        {
            LogManager.log(Level.SEVERE, null, messagingException);
        }


    }

    protected void index_headers( Document doc, String uid, Enumeration mail_header_list )
    {
        while (mail_header_list.hasMoreElements())
        {
            Object h = mail_header_list.nextElement();
            if (h instanceof Header)
            {
                Header ih = (Header) h;
                // STORE ALL HEADERS INTO INDEX DB, DO NOT ANALYZE, WE NEED ORIGINAL CONTENT FOR SEARCH
                doc.add(new Field(FLD_HEADERVAR_NAME, ih.getName(), Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field(FLD_HEADERVAR_VALUE, ih.getValue(), Field.Store.YES, Field.Index.NOT_ANALYZED));
                doc.add(new Field(ih.getName(), ih.getValue(), Field.Store.YES, Field.Index.NOT_ANALYZED));

            }
        }
    }

    protected void index_mp_content( Document doc, String uid, Multipart mp ) throws MessagingException, IOException
    {

        for (int i = 0; i < mp.getCount(); i++)
        {
            Part p = mp.getBodyPart(i);
            if (p instanceof Multipart)
            {
                index_mp_content(doc, uid, (Multipart) p);
            }
            else
            {
                index_part_content(doc, uid, p);
            }

        }
    }

    protected void index_part_content( Document doc, String uid, Part p ) throws MessagingException, IOException
    {
        // SELECT THE PLAIN PART OF AN ALTERNATIVE MP
        if (p.isMimeType("multipart/alternative"))
        {
            Multipart mp = (Multipart) p.getContent();
            Part chosen = null;
            for (int i = 0; i < mp.getCount(); i++)
            {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain"))
                {
                    chosen = bp;
                    break;
                }
                else
                {
                    chosen = bp;
                }
            }
            index_content(doc, uid, chosen);
        }
        else if (p.isMimeType("message/rfc822"))
        {
            index_part_content(doc, uid, (Part) p.getContent());
        }
        else if (p.isMimeType("multipart/*"))
        {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++)
            {
                index_part_content(doc, uid, mp.getBodyPart(i));
            }
        }
        else
        {
            index_content(doc, uid, p);
        }
    }

    protected void index_content( Document doc, String uid, Part p ) throws MessagingException
    {
        String disposition = p.getDisposition();
        String mimetype = normalize_mimetype(p.getContentType());
        String filename = p.getFileName();
        Charset charset = Charset.forName("UTF-8");

        MyMimetype mt = new MyMimetype(mimetype);

        try
        {
            // IS THIS PART AN ATTACHMENT ?
            if (filename != null || (disposition != null && disposition.compareToIgnoreCase(Part.ATTACHMENT) == 0))
            {
                // YES
                if (do_index_attachments)
                {
                    if (mt.is_type("application/octet-stream"))
                    {
                        extract_octet_stream(p.getInputStream(), doc, filename, charset);
                    }
                    else if (mt.is_type("application/zip") ||
                            mt.is_type("application/x-zip") ||
                            mt.is_type("application/x-zip-compressed") ||
                            mt.is_type("application/x-compress") ||
                            mt.is_type("application/x-compressed"))
                    {
                        extract_zip_file(p.getInputStream(), doc, charset);
                    }
                    else if (mt.is_type("application/x-tar"))
                    {
                        extract_tgz_file(p.getInputStream(), doc, charset);
                    }
                    else if (mt.is_type("application/gzip") ||
                            mt.is_type("application/x-gzip") ||
                            mt.is_type("application/gzipped") ||
                            mt.is_type("application/gzip-compressed") ||
                            mt.is_type("application/x-compressed") ||
                            mt.is_type("application/x-compress") ||
                            mt.is_type("gzip/document"))
                    {
                        extract_gzip_file(p.getInputStream(), doc, filename, charset);
                    }
                    else
                    {
                        Reader textReader = extractor.getText(p.getInputStream(), mimetype, charset);
                        if (textReader != null)
                        {
                            doc.add(new Field(FLD_ATTACHMENT, textReader));
                        }
                    }
                }
                if (filename != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT_NAME, filename, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
            else
            {
                Reader textReader = extractor.getText(p.getInputStream(), mimetype, charset);
                if (textReader != null)
                {
                    doc.add(new Field(FLD_BODY, textReader));
                    // WE NEED A NEW READER FOR TEXT DETECTION -> STREAM IS NOT ATOMIC
                    Reader detectReader = extractor.getText(p.getInputStream(), mimetype, charset);
                    String[] languages = ((MimePart) p).getContentLanguage();
                    add_lang_field(languages, doc, detectReader);
                }
            }
        }
        catch (Exception ee)
        {
            LogManager.log(Level.SEVERE, "Error in index_content for mime_type <" + mimetype + ">: ", ee);
            return;
        }

    }

    protected void extract_tgz_file( InputStream is, Document doc, Charset charset )
    {
        try
        {
            extract_tar_file(new GZIPInputStream(is), doc, charset);
        }
        catch (IOException io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_tgz_file: ", io);
        }
    }

    protected void extract_tar_file( InputStream is, Document doc, Charset charset )
    {
        try
        {
            TarInputStream gis = new TarInputStream(is);
            TarEntry entry;
            while ((entry = gis.getNextEntry()) != null)
            {
                String name = entry.getName();
                int dot = name.lastIndexOf('.');
                if (dot == -1)
                {
                    continue;
                }
                String extention = name.substring(dot + 1, name.length());
                Reader textReader = extractor.getText(gis, extention, charset);
                if (textReader != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT, textReader));
                }
                if (name != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT_NAME, name, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
            gis.close();
        }
        catch (Exception io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_tar_file: ", io);
        }
    }

    protected void extract_octet_stream( InputStream is, Document doc, String filename, Charset charset ) throws ExtractionException
    {
        int dot = filename.lastIndexOf('.');
        if (dot == -1)
        {
            return;
        }
        String extension = filename.substring(dot + 1, filename.length());

        if (extension.compareToIgnoreCase("tar") == 0)
        {
            extract_tar_file(is, doc, charset);
        }
        else if (extension.compareToIgnoreCase("gz") == 0)
        {
            extract_gzip_file(is, doc, filename, charset);
        }
        else if (extension.compareToIgnoreCase("zip") == 0)
        {
            extract_zip_file(is, doc, charset);
        }
        else
        {
            try
            {
                Reader textReader = extractor.getText(is, extension, charset);
                if (textReader != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT, textReader));
                }
            }
            catch (Exception io)
            {
                LogManager.log(Level.SEVERE, "Error in extract_octet_stream: ", io);
            }
        }
    }

    protected void extract_gzip_file( InputStream is, Document doc, String filename, Charset charset )
    {
        try
        {
            GZIPInputStream gis = new GZIPInputStream(is);
            String extension = "";

            for (int i = 0; i <= 1; i++)
            {
                int dot = filename.lastIndexOf('.');
                if (dot == -1)
                {
                    return;
                }
                extension = filename.substring(dot + 1, filename.length());
                filename = filename.substring(0, dot);
            }
            if (extension.compareToIgnoreCase("tar") == 0)
            {
                extract_tar_file(gis, doc, charset);
            }
            else
            {
                Reader textReader = extractor.getText(gis, extension, charset);
                if (textReader != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT, textReader));
                }
            }
        }
        catch (Exception io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_octet_stream: ", io);
        }
    }

    protected void extract_zip_file( InputStream is, Document doc, Charset charset ) throws ExtractionException
    {
        try
        {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null)
            {
                String name = entry.getName();
                int dot = name.lastIndexOf('.');
                if (dot == -1)
                {
                    continue;
                }
                String extention = name.substring(dot + 1, name.length());
                Reader textReader = extractor.getText(zis, extention, charset);
                if (textReader != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT, textReader));
                    try
                    {
                        textReader.close();
                    }
                    catch (Exception e)
                    {
//                        logger.debug("failed to close extraction stream()");
                    }
                }
                if (name != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT_NAME, name, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
        }
        catch (IOException io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_octet_stream: ", io);
        }
    }

    protected void add_lang_field( String[] languages, Document doc, Reader detectReader )
    {
        String lang = null;
        if (do_detect_lang && doc.get(FLD_LANG) == null)
        {
            try
            {
                if (languages != null && languages.length > 0)
                {
                    lang = languages[0].trim().toLowerCase(Locale.ENGLISH);
                }
            }
            catch (Exception e)
            {
                LogManager.log(Level.WARNING, "Error while detecting language: ", e);
                return;
            }
            if (lang != null)
            {
                doc.add(new Field(FLD_LANG, lang, Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
        }
    }

    protected String normalize_mimetype( String mimeType )
    {
        // check this code may be a dodgy
        int index = mimeType.indexOf(";");
        if (index != -1)
        {
            mimeType = mimeType.substring(0, index);
        }
        mimeType.toLowerCase(Locale.ENGLISH);
        mimeType.trim();
        return mimeType;
    }

    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting Indexmanager");


        try
        {
            File index_file_dir = m_ctx.getTempFileHandler().get_index_buffer_mail_path();
            if (index_file_dir.exists() && index_file_dir.listFiles().length > 0)
            {
                File[] flist = index_file_dir.listFiles();

                for (int i = 0; i < flist.length; i++)
                {
                    File file = flist[i];
                    String uuid = file.getName();
                    int da_id = DiskSpaceHandler.get_da_id_from_uuid(uuid);
                    int ds_id = DiskSpaceHandler.get_ds_id_from_uuid(uuid);

                    DiskVault dv = m_ctx.get_vault_by_da_id(da_id);
                    DiskSpaceHandler index_dsh = dv.get_dsh(ds_id);
                    index_dsh = dv.open_dsh(index_dsh, 1024*1024);
                    
                    if (index_dsh != null)
                    {
                        RFCFileMail msg = new RFCFileMail(file);

                        // NO, DO RIGHT HERE
                        handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, /*delete_after_index*/ true);
                    }



                    if (da_id == -1)
                    {
                    }


                }

            }
        }
        catch (Exception e)
        {
            LogManager.err_log("Error while cleaning up index holf buffer", e);
            e.printStackTrace();
        }

        idle_worker = new SwingWorker()
        {

            @Override
            public Object construct()
            {
                do_idle();

                return null;
            }
        };

        idle_worker.start();

        this.setStatusTxt("Running");
        this.setGoodState(true);
        return true;
    }

    void work_index_jobs()
    {
        while (true)
        {
            IndexJobEntry ije = null;
            synchronized (index_job_list)
            {
                if (index_job_list.size() > 0)
                {
                    ije = index_job_list.get(0);
                }
            }

            if (ije == null)
            {
                break;
            }

            // NOT LOCKED, OTHERS CAN ADD ENTRIES TO LIST
            ije.handle_index();

            // READY WITH THIS ONE, GET RID OF IT
            synchronized (index_job_list)
            {
                index_job_list.remove(ije);
            }
        }
    }

    void do_idle()
    {
        long last_index_flush = 0;

        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);
            long now = System.currentTimeMillis();

            work_index_jobs();

            // ALLE MINUTE INDEX FLUSHEN SETZEN
            if ((now - last_index_flush) > 60 * 1000)
            {
                m_ctx.flush_index();
                last_index_flush = now;
            }

        }
    }

    public String get_status_txt()
    {
        StringBuffer stb = new StringBuffer();

        return stb.toString();
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        return true;
    }

    @Override
    public boolean initialize()
    {
        return true;
    }

    public void create_IndexJobEntry_task( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCFileMail msg, boolean delete_after_index ) throws IndexException
    {
        IndexJobEntry ije = new IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index);

        File index_path = m_ctx.getTempFileHandler().get_index_buffer_mail_path();


        File index_msg = new File(index_path, uuid);
        if (index_msg.exists())
        {
            throw new IndexException("Index file exists already: " + index_msg.getAbsolutePath());
        }
        msg.rename_to(index_msg);

        synchronized (index_job_list)
        {
            index_job_list.add(ije);
        }
    }

    public void handle_IndexJobEntry( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCFileMail msg, boolean delete_after_index )
    {
        IndexJobEntry ije = new IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index);
        ije.handle_index();
    }
}

