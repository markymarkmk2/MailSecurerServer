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
import dimm.home.mailarchiv.MandantContext;
import home.shared.hibernate.MailHeaderVariable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

/**
 *
 * @author mw
 */
public class IndexManager
{
    protected Charset utf8_charset = Charset.forName("UTF-8");
    ArrayList<MailHeaderVariable> header_list;

    boolean do_index_body = false;
    boolean do_index_attachments = false;
    boolean do_detect_lang = false;
    
    public static final String FLD_ATTACHMENT = "FLDN_ATTACHMENT";
    public static final String FLD_ATTACHMENT_NAME = "FLDN_ATTNAME";

    Map<String,String> analyzerMap;

    Extractor extractor;
    MandantContext m_ctx;

    
    public IndexManager( MandantContext _m_ctx, ArrayList<MailHeaderVariable> _header_list )
    {
        m_ctx = _m_ctx;
        header_list = _header_list;

        extractor = new Extractor( m_ctx);

        analyzerMap = new LinkedHashMap<String,String>();
        analyzerMap.put("en","org.apache.lucene.analysis.StandardAnanlyzer");
        analyzerMap.put("pt","org.apache.lucene.analysis.br.BrazilianAnalyzer");
        analyzerMap.put("zh","org.apache.lucene.analysis.cn.ChineseAnalyzer");
        analyzerMap.put("cs","org.apache.lucene.analysis.cz.CzechAnalyzer");
        analyzerMap.put("de","org.apache.lucene.analysis.de.GermanAnalyzer");
        analyzerMap.put("el","org.apache.lucene.analysis.el.GreekAnalyzer");
        analyzerMap.put("fr","org.apache.lucene.analysis.fr.FrenchAnalyzer");
        analyzerMap.put("nl","org.apache.lucene.analysis.nl.DutchAnalyzer");
        analyzerMap.put("ru","org.apache.lucene.analysis.ru.RussianAnalyzer");
        analyzerMap.put("ja","org.apache.lucene.analysis.cjk.CJKAnalyzer");
        analyzerMap.put("ko","org.apache.lucene.analysis.cjk.CJKAnalyzer");
        analyzerMap.put("th","org.apache.lucene.analysis.th.ThaiAnalyzer");
        analyzerMap.put("tr","org.apache.lucene.analysis.tr.TurkishAnalyzer");
    }

   
    public IndexWriter open_index( String path, String language, boolean do_index ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);

        Analyzer analyzer = create_analyzer( language, do_index );
        IndexWriter writer = new IndexWriter(dir, analyzer, false, IndexWriter.MaxFieldLength.UNLIMITED /*new IndexWriter.MaxFieldLength(50000)*/);

        return writer;
    }
    public IndexWriter create_index( String path, String language  ) throws IOException
    {
        FSDirectory dir = FSDirectory.getDirectory(path);

        Analyzer analyzer = create_analyzer( language, true );
        IndexWriter writer = new IndexWriter(dir, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED /*new IndexWriter.MaxFieldLength(50000)*/);
        return writer;
    }
    public void close_index(IndexWriter writer ) throws IOException
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
                className = (String) analyzerMap.get("en");

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

    public void index_mail_file( RFCFileMail mail_file, Document doc ) throws MessagingException, IOException, IndexException
    {
        RFCMimeMail mime_msg = new RFCMimeMail(null);
        mime_msg.parse(mail_file);

        String unique_id = mail_file.get_unique_id();

        Message msg = mime_msg.getMsg();
        try
        {
            Enumeration mail_header_list = msg.getAllHeaders();

            while (mail_header_list.hasMoreElements())
            {
                Object h = mail_header_list.nextElement();
                if (h instanceof Header)
                {
                    Header ih = (Header) h;
                    System.out.println("N: " + ih.getName() + " V: " + ih.getValue());
                }
            }

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
        }
        catch (IOException iox)
        {
        }
        catch (MessagingException messagingException)
        {
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
        String mimetype = getNormalizedMimeType(p.getContentType());
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
                            /* try { textReader.close(); } catch (Exception e) {
                            logger.debug("failed to close extraction stream()");
                            }*/
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
                    doc.add(new Field("body", textReader));
                    Reader detectReader = extractor.getText(p.getInputStream(), mimetype, charset);
                    String[] languages = ((MimePart) p).getContentLanguage();
                    addLanguage(languages, doc, detectReader);
                    //detectReader.close();
		              /*Reader test = Extractor.getText(getInputStreamFromPart((MimePart)p),mimetype,tempFiles);
                    BufferedReader b = new BufferedReader(test);
                    String line = b.readLine();
                    while (line != null) {
                    System.out.println("(" + line + ")");
                    line = b.readLine();
                    }   */

                }
            }
        }
        catch (Exception ee)
        {
//            logger.debug("failed to decode message part content (mimetype unsupported?) {mimetype='" + mimetype + "'}", ee);
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
            //           logger.error(io.getMessage(), io);
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
                    /*try { textReader.close(); } catch (Exception e) {
                    logger.debug("failed to close extraction stream()");
                    }*/
                }
                if (name != null)
                {
                    doc.add(new Field(FLD_ATTACHMENT_NAME, name, Field.Store.NO, Field.Index.ANALYZED));
                }
            }
            //gis.close();
        }
        catch (Exception io)
        {
//            logger.error(io.getMessage(), io);
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
                    /*try { textReader.close(); } catch (Exception e) {
                    logger.debug("failed to close extraction stream()");
                    }*/
                }
            }
            catch (Exception io)
            {
//                logger.error("failed to extract message: " + io.getMessage(), io);
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
                /*try { textReader.close(); } catch (Exception e) {
                logger.debug("failed to close extraction stream()");
                }*/
            }
        }
        catch (Exception io)
        {
            //           logger.error("failed to extract gzipped message: " + io.getMessage(), io);
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
            //zis.close();
        }
        catch (IOException io)
        {
//            logger.error(io.getMessage(), io);
        }
    }

    protected void addLanguage( String[] languages, Document doc, Reader detectReader )
    {
        String lang = null;
        if (do_detect_lang && doc.get("lang") == null)
        {
            try
            {
                if (languages != null && languages.length > 0)
                {
                    lang = languages[0].trim().toLowerCase(Locale.ENGLISH);
//                    logger.debug("detected language from the email header. {language='" + lang + "'}");
                }
            }
            catch (Exception e)
            {
//                logger.debug("exception occurred while detecting indexing language.", e);
                return;
            }
            if (lang != null)
            {
                doc.add(new Field("lang", lang, Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
        }
    }

    protected String getNormalizedMimeType( String mimeType )
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
}
