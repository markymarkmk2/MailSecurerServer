/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;
import dimm.home.extraction.Extractor;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCMimeMail;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import home.shared.CS_Constants;
import home.shared.hibernate.MailHeaderVariable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import home.shared.zip.LocZipInputStream;
import java.util.zip.ZipEntry;
import javax.mail.Address;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimePart;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;



class ZipEntryInputStream extends InputStream
{
    InputStream is;
    ZipEntry ze;

    public ZipEntryInputStream( InputStream is, ZipEntry ze )
    {
        this.is = is;
        this.ze = ze;
    }

    @Override
    public int available() throws IOException
    {        
        return is.available();
    }

    @Override
    public long skip( long n ) throws IOException
    {
        return is.skip(n);
    }

    @Override
    public boolean markSupported()
    {
        return is.markSupported();
    }


    @Override
    public int read( byte[] b ) throws IOException
    {
        return is.read(b);
    }

    @Override
    public int read( byte[] b, int off, int len ) throws IOException
    {
        return is.read(b, off, len);
    }



    @Override
    public int read() throws IOException
    {
        return is.read();
    }
}

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
    RFCGenericMail msg;
    boolean delete_after_index;
    IndexManager ixm;

    public IndexJobEntry( IndexManager ixm, MandantContext m_ctx, String unique_id, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean delete_after_index )
    {
        this.ixm = ixm;
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
        DocumentWrapper docw = new DocumentWrapper(doc, unique_id);

        ixm.setStatusTxt(Main.Txt("Indexing mail file" + " "+ unique_id));
        

        try
        {
            // DO THE REAL WORK (EXTRACT AND INDEX)
            ixm.index_mail_file(m_ctx, unique_id, da_id, ds_id, msg, docw);

            IndexWriter writer = index_dsh.get_write_index();

            // DETECT LANG OF INDEX AND PUT INTO LUCENE
            String lang = ixm.get_lang_by_analyzer( writer.getAnalyzer() );
            doc.add( new Field( CS_Constants.FLD_LANG, lang, Field.Store.YES, Field.Index.NOT_ANALYZED ) );

            byte[] hash = msg.get_hash();
            if (hash != null)
            {
                String txt_hash = new String(Base64.encodeBase64(hash));
                doc.add( new Field( CS_Constants.FLD_HASH, txt_hash, Field.Store.YES, Field.Index.NOT_ANALYZED ) );
            }

            // SHOVE IT RIGHT OUT!!!
            synchronized( index_dsh.idx_lock )
            {
                writer.addDocument(doc);
            }
            

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
                msg.delete();
            }
        }
        catch (Exception ex)
        {
            LogManager.log(Level.SEVERE, "Error occured while indexing message " + unique_id + ": ", ex);
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

    Map<String, String> analyzerMap;
    Extractor extractor;
    MandantContext m_ctx;
    private SwingWorker idle_worker;
    ArrayList<String> allowed_headers;

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

        allowed_headers = new ArrayList<String>();

        allowed_headers.add("From");
        allowed_headers.add("To");
        allowed_headers.add("CC");
        allowed_headers.add("BCC");
    }

    public IndexWriter open_index( String path, String language, boolean do_index ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);

        Analyzer analyzer = create_analyzer(language, do_index);

        if (IndexWriter.isLocked(dir))
        {
            Main.err_log("/*Unlocking already locked IndexWriter*/");
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

        // CHECK IF INDEX EXISTS
        boolean create = !IndexReader.indexExists(path);
        
        if (IndexWriter.isLocked(dir))
        {
            Main.err_log("Unlocking already locked IndexWriter");
            IndexWriter.unlock(dir);
        }

        // AND CREATE IF NOT
        IndexWriter writer = new IndexWriter(dir, analyzer, create, IndexWriter.MaxFieldLength.UNLIMITED /*new IndexWriter.MaxFieldLength(50000)*/);
        return writer;
    }

    public void close_index( IndexWriter writer ) throws IOException
    {
        writer.commit();
        writer.close();
    }

    protected String get_lang_by_analyzer( Analyzer analyzer )
    {
        Set<Entry<String, String>> map_set = analyzerMap.entrySet();
        Iterator<Entry<String, String>> it = map_set.iterator();
        while (it.hasNext())
        {
            Entry<String, String> e = it.next();
            if (e.getValue().compareTo(analyzer.getClass().getName()) == 0)
            {
                return e.getKey();
            }
        }
        return "de";
    }
    
    public Analyzer create_analyzer( String language, boolean do_index )
    {
        Analyzer analyzer = null;
        if (language == null)
        {
            language = "de";
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

    String to_field( int i )
    {
        return Integer.toString( i );
    }
    String to_hex_field( long l )
    {
        return Long.toString(l, 16);
    }

    public void index_mail_file( MandantContext m_ctx, String unique_id, int da_id, int ds_id, RFCGenericMail mail_file, DocumentWrapper docw ) throws MessagingException, IOException, IndexException
    {
        RFCMimeMail mime_msg = new RFCMimeMail();
        mime_msg.parse(mail_file);

        Document doc = docw.doc;

        doc.add(new Field(CS_Constants.FLD_UID_NAME, unique_id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_MA, to_field(m_ctx.getMandant().getId()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_DA, to_field(da_id), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_DS, to_field(ds_id), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_SIZE, to_hex_field(mail_file.get_length()), Field.Store.YES, Field.Index.NOT_ANALYZED));

        // SUBJECT IS STORED AND ANALYZED
        String subject = mime_msg.getMsg().getSubject();
        if (subject == null)
            subject = "";
        doc.add(new Field(CS_Constants.FLD_SUBJECT, subject, Field.Store.YES, Field.Index.ANALYZED));

        // LONGS AS HEX
        Date d = mime_msg.getMsg().getReceivedDate();
        if (d == null)
            d = mime_msg.getMsg().getSentDate();
        if (d == null)
            d = mail_file.getDate();

        doc.add(new Field(CS_Constants.FLD_DATE, to_hex_field(d.getTime()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_TM, to_hex_field(mail_file.getDate().getTime()), Field.Store.YES, Field.Index.NOT_ANALYZED));


        Message msg = mime_msg.getMsg();
        try
        {
            Enumeration mail_header_list = msg.getAllHeaders();
            
            index_headers(doc, unique_id, msg.getAllHeaders());

            Object content = msg.getContent();
            if (content instanceof Multipart)
            {
                Multipart mp = (Multipart) content;

                index_mp_content(docw, unique_id, mp);
            }
            else if (content instanceof Part)
            {
                Part p = (Part) content;
                index_part_content(docw, unique_id, p);
            }

            if (doc.getField( CS_Constants.FLD_ATTACHMENT_NAME) != null)
            {
                doc.add(new Field(CS_Constants.FLD_HAS_ATTACHMENT, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
            // ADD BCC TO MAIL IF NOT ALREADY DONE SO
            if (mail_file.get_bcc_list().size() > 0)
            {
                Field[] bcc_in_mail = doc.getFields("BCC");

                for (int i = 0; i < mail_file.get_bcc_list().size(); i++)
                {
                    String bcc = mail_file.get_bcc_list().get(i).toString();
                    boolean is_already_in_doc = false;
                    for (int j = 0; j < bcc_in_mail.length; j++)
                    {
                        Field field = bcc_in_mail[j];
                        if (field.toString().contains(bcc))
                        {
                            is_already_in_doc = true;
                            break;
                        }
                    }
                    if (!is_already_in_doc)
                        doc.add(new Field("BCC", bcc.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED ) );
                }
            }
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            LogManager.log(Level.SEVERE, unique_id, fileNotFoundException);
        }
        catch (IOException iox)
        {
            LogManager.log(Level.SEVERE, unique_id, iox);
        }
        catch (MessagingException messagingException)
        {
            LogManager.log(Level.SEVERE, unique_id, messagingException);
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
                String name = ih.getName();


                boolean found = false;
                for (int i = 0; i < allowed_headers.size(); i++)
                {
                    String ah = allowed_headers.get(i);
                    if (ah.compareToIgnoreCase(name) == 0)
                    {
                        found = true;
                        break;
                    }
                }

                if (found)
                {
                    // STORE ALL HEADERS INTO INDEX DB, DO NOT ANALYZE, WE NEED ORIGINAL CONTENT FOR SEARCH
                    doc.add(new Field(ih.getName(), ih.getValue(), Field.Store.YES, Field.Index.NOT_ANALYZED));
                    LogManager.log(Level.FINEST, "Mail " + uid + " adding header <" + ih.getName() + "> Val <" + ih.getValue() + ">");
                }
                else
                {
                    LogManager.log(Level.FINEST, "Mail " + uid + " skipping header <" + ih.getName() + "> Val <" + ih.getValue() + ">");
                }                
            }
        }
    }

    public  void index_mp_content( DocumentWrapper doc, String uid, Multipart mp ) throws MessagingException, IOException
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

    public  void index_part_content( DocumentWrapper doc, String uid, Part p ) throws MessagingException, IOException
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

    protected void index_content( DocumentWrapper doc, String uid, Part p ) throws MessagingException
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
                if (filename == null)
                    filename = "";

                LogManager.log(Level.FINER, "Indexing attachment " + filename + " MT:<" + mimetype + "> CS:<" + charset + "> to doc "+ doc.get_uuid());

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
                        Reader textReader = extractor.getText(p.getInputStream(), doc, mimetype, charset);
                        if (textReader != null)
                        {
                            doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT, textReader));
                        }
                    }
                }
                if (filename != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT_NAME, filename, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
            else
            {
                Reader textReader = extractor.getText(p.getInputStream(), doc, mimetype, charset);
                if (textReader != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_BODY, textReader));
                    // WE NEED A NEW READER FOR TEXT DETECTION -> STREAM IS NOT ATOMIC
                    Reader detectReader = extractor.getText(p.getInputStream(), doc, mimetype, charset);
                    String[] languages = ((MimePart) p).getContentLanguage();
                    add_lang_field(languages, doc.doc, detectReader);
                }
            }
        }
        catch (Exception ee)
        {
            if (filename == null)
                filename = "";

            LogManager.log(Level.SEVERE, "Error in index_content for " + uid + " " + filename + " mime_type <" + mimetype + ">: ", ee);
            return;
        }

    }

    protected void extract_tgz_file( InputStream is, DocumentWrapper doc, Charset charset )
    {
        try
        {
            extract_tar_file(new GZIPInputStream(is), doc, charset);
        }
        catch (IOException io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_tgz_file " + doc.get_uuid(), io);
        }
    }

    protected void extract_tar_file( InputStream is, DocumentWrapper doc, Charset charset )
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
                Reader textReader = extractor.getText(gis, doc, extention, charset);
                if (textReader != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT, textReader));
                }
                if (name != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT_NAME, name, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
            gis.close();
        }
        catch (Exception io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_tar_file " + doc.get_uuid(), io);
        }
    }

    protected void extract_octet_stream( InputStream is, DocumentWrapper doc, String filename, Charset charset ) throws ExtractionException
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
                Reader textReader = extractor.getText(is, doc, extension, charset);
                if (textReader != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT, textReader));
                }
            }
            catch (Exception io)
            {
                LogManager.log(Level.SEVERE, "Error in extract_octet_stream: " + doc.get_uuid(), io);
            }
        }
    }

    protected void extract_gzip_file( InputStream is, DocumentWrapper doc, String filename, Charset charset )
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
                Reader textReader = extractor.getText(gis, doc, extension, charset);
                if (textReader != null)
                {
                    doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT, textReader));
                }
            }
        }
        catch (Exception io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_gzip_file " + doc.get_uuid(), io);
        }
    }

    protected void extract_zip_file( InputStream is, DocumentWrapper doc, Charset charset ) throws ExtractionException
    {
        ZipEntry entry;
        try
        {
            LocZipInputStream zis = new LocZipInputStream(is);
           
            while ((entry = zis.getNextEntry()) != null)
            {
                String name = entry.getName();
                int dot = name.lastIndexOf('.');
                if (dot == -1)
                {
                    continue;
                }

                LogManager.log(Level.FINER, "Indexing zip entry " + name + " + to " + doc.get_uuid());
                String extention = name.substring(dot + 1, name.length());

                ZipEntryInputStream zeis = new ZipEntryInputStream( zis, entry );


                try
                {
                    Reader textReader = extractor.getText(zeis, doc, extention, charset);
                    if (textReader != null)
                    {
                        doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT, textReader));
                    }
                    if (name != null)
                    {
                        doc.doc.add(new Field(CS_Constants.FLD_ATTACHMENT_NAME, name, Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
                catch (ExtractionException extractionException)
                {
                    LogManager.log(Level.WARNING, "Error while extracting text from zip_entry " + name , extractionException);
                }
            }
        }
        catch ( IllegalArgumentException wrong_zip_entry )
        {
            LogManager.log(Level.SEVERE, "Error in zip file " + doc.get_uuid(), wrong_zip_entry);
        }
        catch (IOException io)
        {
            LogManager.log(Level.SEVERE, "Error in extract_zip_file " + doc.get_uuid(), io);
        }
    }

    protected void add_lang_field( String[] languages, Document doc, Reader detectReader )
    {
        String lang = null;
        if (do_detect_lang && doc.get(CS_Constants.FLD_LANG) == null)
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
                doc.add(new Field(CS_Constants.FLD_LANG, lang, Field.Store.YES, Field.Index.NOT_ANALYZED));
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
                    long time = DiskSpaceHandler.get_time_from_uuid(uuid);
                    boolean encoded = DiskSpaceHandler.is_encoded_from_uuid( uuid );

                    DiskVault dv = m_ctx.get_vault_by_da_id(da_id);
                    DiskSpaceHandler index_dsh = dv.get_dsh(ds_id);
                    index_dsh = dv.open_dsh(index_dsh, 1024*1024);
                    
                    if (index_dsh != null)
                    {
                        RFCFileMail msg = new RFCFileMail(file, new Date(time), encoded);

                        // NO, DO RIGHT HERE
                        handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg, /*delete_after_index*/ true);
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

    void work_jobs()
    {
        synchronized (index_job_list)
        {
            if (index_job_list.size() == 0)
                return;
        }

        setStatusTxt(Main.Txt("Updating_index"));

        while (true)
        {
            IndexJobEntry ije = null;
            synchronized (index_job_list)
            {
                if (index_job_list.size() > 0)
                {
                    ije = index_job_list.remove(0);
                }
            }

            if (ije == null)
            {
                break;
            }

            // NOT LOCKED, OTHERS CAN ADD ENTRIES TO LIST
            ije.handle_index();
        }

        clrStatusTxt(Main.Txt("Updating_index"));
    }

    void do_idle()
    {
        long last_index_flush = 0;

        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);
            long now = System.currentTimeMillis();

            work_jobs();

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

    public void create_IndexJobEntry_task( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean delete_after_index ) throws IndexException
    {
        IndexJobEntry ije = new IndexJobEntry(this, m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index);

        File index_path = m_ctx.getTempFileHandler().get_index_buffer_mail_path();


        File index_msg = new File(index_path, uuid);
        if (index_msg.exists())
        {
            throw new IndexException("Index file exists already: " + index_msg.getAbsolutePath());
        }
        try
        {
            msg.move_to(index_msg);
        }
        catch (Exception exc)
        {
            throw new IndexException( exc.getMessage() );
        }

        synchronized (index_job_list)
        {
            index_job_list.add(ije);
        }
    }

    public void handle_IndexJobEntry( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean delete_after_index )
    {
        IndexJobEntry ije = new IndexJobEntry(this, m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index);
        ije.handle_index();
    }

    @Override
    public String get_task_status()
    {
        StringBuffer stb = new StringBuffer();


        synchronized (index_job_list)
        {
            for (int i = 0; i < index_job_list.size(); i++)
            {
                IndexJobEntry mbie = index_job_list.get(i);

                stb.append("IJEID");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.unique_id);
                stb.append("\n");
            }
        }
        return stb.toString();
    }

    static public boolean doc_field_exists( Document doc, String fld )
    {
        String val = doc.get(fld);
        if (val != null)
            return true;

        return false;
    }
    static int _doc_get_int( Document doc, String fld ) throws Exception
    {
        String val = doc.get(fld);
        if (val == null)
            throw new Exception( "field " + fld + " does not exist" );

        return Integer.parseInt(val);
    }
    static long _doc_get_long( Document doc, String fld, int radix ) throws Exception
    {
        String val = doc.get(fld);
        if (val == null)
            throw new Exception( "field " + fld + " does not exist" );

        return Long.parseLong(val, radix);
    }
    static public int doc_get_int( Document doc, String fld )
    {
        int ret = -1;

        try
        {
            ret = _doc_get_int(doc, fld);
        }
        catch (Exception exception)
        {
            LogManager.err_log("Cannot parse int field " + fld + " from index" , exception);
        }

        return ret;
    }
    static public boolean doc_get_bool( Document doc, String fld )
    {
        boolean ret = false;

        try
        {
            String val = doc.get(fld);
            if (val.charAt(0) == '1')
                ret = true;
        }
        catch (Exception exception)
        {
            LogManager.err_log("Cannot parse bool field " + fld + " from index" , exception);
        }

        return ret;
    }
    static public long doc_get_hex_long( Document doc, String fld )
    {
        long ret = -1;

        try
        {
            ret = _doc_get_long(doc, fld, 16);
        }
        catch (Exception exception)
        {
            LogManager.err_log("Cannot parse hex long field " + fld + " from index" , exception);
        }

        return ret;
    }
    public boolean handle_existing_mail_in_vault( DiskVault dv,  RFCGenericMail msg )
    {
        boolean ret = false;
        // GO THROUGH ALL DISKSPACES OF THIS VAULT


        // TODO: MAYBE WE SHOULD SPEED THIS UP WITH LOCAL DB?

        String hash = new String(Base64.encodeBase64(msg.get_hash()));

        for (int j = 0; j < dv.get_dsh_list().size(); j++)
        {
            DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
            if (dsh.is_index())
            {
                // START A SERACH TODO: DO THIS IN BACKGROUND
                try
                {
                    IndexReader reader = dsh.open_read_index();
                    IndexSearcher searcher = new IndexSearcher(reader);

                    Term term = new Term(CS_Constants.FLD_HASH, hash);
                    Query qry = new TermQuery(term);

                    TermsFilter filter = null;

                    // SSSSEEEEAAAARRRRCHHHHHHH
                    TopDocs tdocs = searcher.search(qry, filter, 1);
                    if (tdocs.totalHits > 0)
                    {
                        // FOUND SAME MAIL
                        Document doc = searcher.doc(0);

                        // UPDATE IF NECESSARY WITH  NEW BCC
                        ret = handle_bcc_and_update( dsh, doc, msg );

                        
                        break;
                    }
                }
                catch (VaultException vaultException)
                {
                }
                catch (IOException iOException)
                {
                }
            }
        }

        return ret;
    }

    void update_document( Document doc, DiskSpaceHandler index_dsh ) throws CorruptIndexException, IOException
    {
        IndexWriter writer = index_dsh.get_write_index();


        Term term = new Term( CS_Constants.FLD_UID_NAME, doc.get(CS_Constants.FLD_UID_NAME) );
        // SHOVE IT RIGHT OUT!!!

        synchronized( index_dsh.idx_lock )
        {
            writer.updateDocument(term, doc);
        }
    }


    boolean handle_bcc_and_update( DiskSpaceHandler index_dsh, Document doc, RFCGenericMail msg )
    {
        boolean needs_updated_index = false;
           // TODO: DO WE REALLY NEED THE SECOND ENTRY? THE HASH SAYS THE MESSAGE IS IDENTICAL, SO WHY BOTHER?
            // NOW WE CHECK IF ALL BCC ARE IN MESSAGE, IF NOT WE NEED A NEW INDEX
            if (msg.get_bcc_list().size() > 0)
            {
                Field[] bcc_fields = doc.getFields("BCC");
                for (int i = 0; i < msg.get_bcc_list().size(); i++)
                {
                    Address mail_bcc = msg.get_bcc_list().get(i);

                    for (int f = 0; f < bcc_fields.length; f++)
                    {
                        String doc_bcc = bcc_fields[f].stringValue();
                        if (!doc_bcc.contains( mail_bcc.toString() ))
                        {
                            needs_updated_index = true;
                            doc.add(new Field( "BCC", mail_bcc.toString(), Field.Store.YES, Field.Index.NOT_ANALYZED)  );
                        }
                    }
                }
            }

            String uuid = doc.get(CS_Constants.FLD_UID_NAME);
            int da_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DA);
            int ds_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DS);

            if (needs_updated_index)
            {
                try
                {
                    // IF WE CAN UPDATE, EVERY THING IS FINE, DEl MESSAGE
                    LogManager.log(Level.INFO, "Updating index for existing mail " + doc.get(CS_Constants.FLD_UID_NAME) );
                    update_document(doc, index_dsh);

                    // AND MARK AS "BEEN OFFICIALLY PIMPED"
                    return true;
                }
                catch (Exception ex)
                {
                    LogManager.log(Level.SEVERE, "Cannot update index for existing mail " + doc.get(CS_Constants.FLD_UID_NAME), ex);
                    // CONTINUE ANYWAY INDEX COULD BE READ ONLY
                }
            }
            else
            {
                LogManager.log(Level.INFO, "Skipping existing mail " + doc.get(CS_Constants.FLD_UID_NAME) + ",  exists already in da:" + da_id + " ds:" + ds_id );
                return true;
            }
            return false;
    }


}

