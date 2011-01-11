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
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.filter.FilterMatcher;
import home.shared.filter.FilterValProvider;
import home.shared.filter.GroupEntry;
import home.shared.filter.LogicEntry;
import home.shared.hibernate.AccountConnector;
import home.shared.hibernate.MailHeaderVariable;
import home.shared.mail.RFCMailAddress;
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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import home.shared.zip.LocZipInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
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
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;

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




/**
 *
 * @author mw
 */
public class IndexManager extends WorkerParent
{

    public static boolean no_single_instance()
    {
        return false;
    }
    public static boolean index_buffer_test = false;

    protected Charset utf8_charset = Charset.forName("UTF-8");
    ArrayList<MailHeaderVariable> header_list;
    final ArrayList<IndexJobEntry> index_job_list;
    //boolean do_index_body = false;
    boolean do_index_attachments = false;
    boolean do_detect_lang = false;
    Map<String, String> analyzerMap;
    Extractor extractor;
    MandantContext m_ctx;
    private BackgroundWorker idle_worker;
    ArrayList<String> email_headers;
    ArrayList<String> allowed_headers;
    ArrayList<String> allowed_domains;
    GroupEntry acct_exclude_list;

    private ThreadPoolExecutor index_run_thread_pool;
    private ThreadPoolExecutor index_run_single_thread_pool;
    private ThreadPoolExecutor index_load_thread_pool;
    private static final long MAX_INDEX_THREADS = 8l;

    public static final String X_IGNORE_TO = "X-MS-IgnoreTo";



    
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

        email_headers = new ArrayList<String>();
        allowed_headers = new ArrayList<String>();

        email_headers.add(CS_Constants.FLD_FROM);
        email_headers.add(CS_Constants.FLD_TO);
        email_headers.add(CS_Constants.FLD_CC);
        email_headers.add(CS_Constants.FLD_BCC);
        email_headers.add(CS_Constants.FLD_DELIVEREDTO);

        build_domain_and_excl_list();

        build_header_lists();

        int index_threads = (int)Main.get_long_prop(GeneralPreferences.INDEX_MAIL_THREADS, MAX_INDEX_THREADS);

        index_run_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "IndexMail" , index_threads, 10 );

        index_run_single_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "IndexSequentialMail" , 1, 10 );

        index_load_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "LoadMail" , 1, 10 );

        is_started = false;
    }

    
    public ArrayList<String>get_email_headers()
    {
        return email_headers;
    }

    private void build_header_lists()
    {
        Set<MailHeaderVariable> mhv_set = m_ctx.getMandant().getMailHeaderVariable();
        for (Iterator<MailHeaderVariable> it = mhv_set.iterator(); it.hasNext();)
        {
            MailHeaderVariable mhv = it.next();
            // IS EMAIL HEADER?
            if ((mhv.getFlags() & CS_Constants.MHV_CONTAINS_EMAIL) == CS_Constants.MHV_CONTAINS_EMAIL)
                email_headers.add( mhv.getVarName() );
            else
                allowed_headers.add( mhv.getVarName() );
        }
    }

    private void build_domain_and_excl_list()
    {
        acct_exclude_list = null;
        allowed_domains = new ArrayList<String>();

        // FILL LIST OF SUPPORTED DOMAINS AND EVAL EXISTING EXCLUDELISTS
        ArrayList<LogicEntry> list = new ArrayList<LogicEntry>();
        for (AccountConnector acct : m_ctx.getMandant().getAccountConnectors())
        {
            String[] domain_list = acct.getDomainlist().split(CS_Constants.TEXTLIST_DELIM);
            for (int i = 0; i < domain_list.length; i++)
            {
                allowed_domains.add(domain_list[i].toLowerCase().trim());
            }

            // EVAL EXCLUDE EXPRESSIONS AND ADD AS OR-ENTRY TO LIST
            String excl_data = acct.getExcludefilter();
            if (excl_data != null && excl_data.length() > 0)
            {
                GroupEntry ge = new GroupEntry( FilterMatcher.get_filter_list(excl_data, /*compr*/ true) );
                ge.setPrevious_is_or(true);
                list.add(ge);
            }
        }
        if (list.size() > 0)
        {
            acct_exclude_list = new GroupEntry(list);
        }
    }

    public ArrayList<String> getAllowed_domains()
    {
        return allowed_domains;
    }

    // OPEN AND IF NOT EXISTS, CREATE, SO OPEN ALWAYS SUCCEEDS
    public IndexWriter open_index( String path, String language, boolean can_create ) throws IOException
    {
        FSDirectory dir = FSDirectory.open(new File(path), new SimpleFSLockFactory());

        Analyzer analyzer = create_analyzer(language, true);

        // CHECK IF INDEX EXISTS
        boolean create = false;
        if (can_create)
        {
            create = !IndexReader.indexExists(dir);
            if (create)
            {
                LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_INDEX, Main.Txt("Creating_new_index_in") + " " + path);
            }
        }
        
        if (IndexWriter.isLocked(dir))
        {
            LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_INDEX, Main.Txt("Unlocking_already_locked_IndexWriter"));
            IndexWriter.unlock(dir);
        }

        IndexWriter writer = new IndexWriter(dir, analyzer, create, new IndexWriter.MaxFieldLength(100000));
        writer.setRAMBufferSizeMB(32);
        writer.setMergeFactor(16);

        //
        //writer.setMaxMergeDocs(32);  // THIS KEEPS AMOUNT OF FILES HIGH ???!?
        //writer.setMaxFieldLength(100000);
        //writer.setUseCompoundFile(false);

        return writer;
    }

    public static IndexReader open_read_index( String path ) throws IOException
    {
        FSDirectory dir = FSDirectory.open(new File(path), new SimpleFSLockFactory());

        IndexReader reader = IndexReader.open(dir, /*rd_only*/ true);

        return reader;
    }

    // CREATE (AND DELETE OLD STUFF!!!!) SHOULD ONLY BE USED BY REINDEX OR MANUAL FUNCS
    public IndexWriter create_index( String path, String language ) throws IOException
    {
        FSDirectory dir = FSDirectory.open(new File(path), new SimpleFSLockFactory());

        Analyzer analyzer = create_analyzer(language, true);


        if (IndexWriter.isLocked(dir))
        {
            LogManager.msg(LogManager.LVL_WARN, LogManager.TYP_INDEX, "Unlocking already locked IndexWriter");
            IndexWriter.unlock(dir);
        }

        // AND CREATE IF NOT
        IndexWriter writer = new IndexWriter(dir, analyzer, true, new IndexWriter.MaxFieldLength(100000));
        writer.setMergeScheduler(new org.apache.lucene.index.SerialMergeScheduler());
        writer.setRAMBufferSizeMB(32);
        writer.setMergeFactor(16);
        //writer.setMaxMergeDocs(32);
        //writer.setMaxFieldLength(Integer.MAX_VALUE);
        //writer.setUseCompoundFile(false);
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
            analyzer = new StandardAnalyzer(Version.LUCENE_24);
        }
        PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(analyzer);


        if (do_index)
        {
            wrapper.addAnalyzer(CS_Constants.FLD_TO, new EmailAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_FROM, new EmailAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_CC, new EmailAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_BCC, new EmailAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_DELIVEREDTO, new EmailAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_ATTACHMENT_NAME, new FileNameAnalyzer());
        }
        else
        {
            wrapper.addAnalyzer(CS_Constants.FLD_TO, new WhitespaceAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_FROM, new WhitespaceAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_CC, new WhitespaceAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_BCC, new WhitespaceAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_DELIVEREDTO, new WhitespaceAnalyzer());
            wrapper.addAnalyzer(CS_Constants.FLD_ATTACHMENT_NAME, new FileNameAnalyzer());
        }
        return wrapper;
    }

    String to_field( int i )
    {
        return Integer.toString(i);
    }

    String to_hex_field( long l )
    {
        return Long.toString(l, 16);
    }

    public RFCMimeMail load_mail_file( RFCGenericMail mail_file ) throws MessagingException, IOException
    {
        RFCMimeMail mime_msg = new RFCMimeMail();
        mime_msg.parse(mail_file);

        return mime_msg;
    }

    private boolean check_exclude_list( AccountConnector acct, final RFCMimeMail mime_msg )
    {
        // THEN TEST FOR EXCLUDES
        if (acct_exclude_list == null)
        {
            return false;
        }


        final ArrayList<RFCMailAddress> mail_list = mime_msg.getEmail_list();

        // FIRST CHECK FOR CORRECT DOMAIN
        
        boolean matches_domain = false;
        for (Iterator<RFCMailAddress> it = mail_list.iterator(); it.hasNext();)
        {
            RFCMailAddress rFCMailAddress = it.next();
            String domain = rFCMailAddress.get_domain();
            if (allowed_domains.contains(domain))
            {
                matches_domain = true;
                break;
            }
        }
        if (!matches_domain)
        {
            String sub = "";
            try
            {
                sub = mime_msg.getMsg().getSubject();
            }
            catch (MessagingException messagingException)
            {
            }
            LogManager.msg_index(LogManager.LVL_WARN, Main.Txt("No_valid_mail_domain_found_for_mail") + " "+ sub);

            // THIW WAS ALLOWED UNTIL 1.4.4, SO WE STAY COMPATIBLE BUT WE CAN SWITCH IT OFF
            if (!Main.get_bool_prop(GeneralPreferences.ALLOW_UNKNOWN_DOMAIN_MAIL, true) && !Main.get_bool_prop(GeneralPreferences.ALLOW_INKNOWN_DOMAIN_MAIL, true))
            {
                LogManager.msg_index(LogManager.LVL_WARN,Main.Txt("Skipping_mail") + " " + sub );
                return true;
            }

            return false;
        }


        // BUILD A VAL PROVIDER FOR THIS MESSAGE
        FilterValProvider acct_excl_provider = new FilterValProvider()
        {

            @Override
            public ArrayList<String> get_val_vor_name( String name )
            {
                ArrayList<String> list = new ArrayList<String>();
                if (name.compareTo(CS_Constants.ACCT_FF_MAIL) == 0)
                {
                    // BUILD A LIST OF ALL MAIL-ADDRESSES
                    for (int i = 0; i < mail_list.size(); i++)
                    {
                        RFCMailAddress mla = mail_list.get(i);
                        list.add(mla.get_mail());
                    }
                }
                else if (name.compareTo(CS_Constants.ACCT_FF_MAILHEADER) == 0)
                {
                    // BUILD A LIST OF ALL MAILHEADERS EXCEPT SUBJECT AND MAIL
                    try
                    {
                        Enumeration en = mime_msg.getMsg().getAllHeaders();
                        while (en.hasMoreElements())
                        {
                            Object h = en.nextElement();
                            if (h instanceof Header)
                            {
                                Header hdr = (Header) h;
                                if (hdr.getName().compareToIgnoreCase("Subject") == 0)
                                {
                                    continue;
                                }
                                if (hdr.getName().compareToIgnoreCase("To") == 0)
                                {
                                    continue;
                                }
                                if (hdr.getName().compareToIgnoreCase("From") == 0)
                                {
                                    continue;
                                }
                                if (hdr.getName().compareToIgnoreCase("CC") == 0)
                                {
                                    continue;
                                }

                                list.add(hdr.getValue());
                            }
                        }
                    }
                    catch (Exception exc)
                    {
                        LogManager.msg_index(LogManager.LVL_WARN, "Error building FilterValProvider for mail: " + exc.getLocalizedMessage());
                    }

                }
                else if (name.compareTo(CS_Constants.ACCT_FF_SUBJECT) == 0)
                {
                    try
                    {
                        list.add(mime_msg.getMsg().getSubject());
                    }
                    catch (Exception exc)
                    {
                        LogManager.msg_index(LogManager.LVL_WARN, "Error building FilterValProvider for mail: " + exc.getLocalizedMessage());
                    }
                }
                return list;
            }
        };

        // NOW FOR THE SIMPLE TASK: IS THIS MAIL ON THE EXCLUDELIST?
        FilterMatcher fm = new FilterMatcher(acct_exclude_list.getChildren(), acct_excl_provider);
        if (fm.eval())
        {
            return true;
        }

        return false;
    }

    // RETURN FALSE IF MAIL SHOULD BE DELETED
    public boolean index_mail_file( MandantContext m_ctx, String unique_id, int da_id, int ds_id, 
            RFCGenericMail mail_file, RFCMimeMail mime_msg, DocumentWrapper docw,  boolean delete_after_index, boolean skip_account_filter ) throws MessagingException, IOException, IndexException
    {
        String subject = mime_msg.getMsg().getSubject();
        if (mime_msg.getEmail_list().isEmpty())
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Found bogus mail (no from / to / cc) " + unique_id + ": skipping");
            return false;
        }


        // CHECK FOR ACCOUNTFILTER

        AccountConnector acct_match = null;
        for (AccountConnector acct : m_ctx.getMandant().getAccountConnectors())
        {
            if (!check_exclude_list(acct, mime_msg))
            {
                acct_match = acct;
                break;
            }
        }

        // IF WE DO NOT FIND A MATCHING ACCOUNT, WE DELETE MAIL
        if (acct_match == null && !skip_account_filter)
        {
            LogManager.msg_index(LogManager.LVL_DEBUG, "Skipping unmatching mail " + unique_id);
            
            if (delete_after_index)
            {
                delete_mail_before_index(m_ctx, unique_id, da_id, ds_id);
            }
            return false;
        }

        if (!skip_account_filter)
        {
            ArrayList<String> domain_list = m_ctx.get_index_manager().getAllowed_domains();
            boolean exceeded = Main.get_control().get_license_checker().is_license_exceeded(m_ctx, domain_list, mime_msg.getEmail_list());
            if (exceeded)
            {
                if (delete_after_index)
                {
                    delete_mail_before_index(m_ctx, unique_id, da_id, ds_id);
                }
                return false;
            }
        }



        Document doc = docw.doc;

        doc.add(new Field(CS_Constants.FLD_UID_NAME, unique_id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_MA, to_field(m_ctx.getMandant().getId()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_DA, to_field(da_id), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_DS, to_field(ds_id), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_SIZE, to_hex_field(mail_file.get_length()), Field.Store.YES, Field.Index.NOT_ANALYZED));

        // SUBJECT IS STORED AND ANALYZED

        if (subject == null)
        {
            subject = "";
        }
        doc.add(new Field(CS_Constants.FLD_SUBJECT, subject, Field.Store.YES, Field.Index.ANALYZED));

        // LONGS AS HEX
        Date d = mime_msg.getMsg().getReceivedDate();
        if (d == null)
        {
            d = mime_msg.getMsg().getSentDate();
        }
        if (d == null)
        {
            d = mail_file.getDate();
            LogManager.msg_index(LogManager.LVL_WARN,  "Mail " + unique_id + " has no Sent- or ReceivedDate <" + subject + ">");
        }

        doc.add(new Field(CS_Constants.FLD_DATE, to_hex_field(d.getTime()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(CS_Constants.FLD_TM, to_hex_field(mail_file.getDate().getTime()), Field.Store.YES, Field.Index.NOT_ANALYZED));


        Message msg = mime_msg.getMsg();
        try
        {
            index_headers(doc, unique_id, msg.getAllHeaders());

            Object content = null;

            try
            {
                content = msg.getContent();
            }
            catch (UnsupportedEncodingException exc)
            {
                InputStream is = msg.getDataHandler().getDataSource().getInputStream();
                if (is != null)
                {
                    try
                    {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buff = new byte[CS_Constants.STREAM_BUFFER_LEN];
                        while (true)
                        {
                            int len = is.read(buff);
                            if (len < 0)
                            {
                                break;

                            }
                            baos.write(buff, 0, len);

                        }
                        is.close();
                        baos.close();
                        String cs = "utf-8";
                        Field f = doc.getField(CS_Constants.FLD_CHARSET);
                        if (f != null)
                        {
                            cs = f.stringValue().toLowerCase();
                            if (cs.contains("utf") && cs.contains("8"))
                            {
                                cs = "utf-8";
                            }
                            if (cs.contains("utf") && cs.contains("16"))
                            {
                                cs = "utf-16";
                            }
                            if (cs.contains("iso"))
                            {
                                cs = "ISO-8859-1";
                            }
                        }
                        if (!Charset.isSupported(cs))
                        {
                            cs = "ISO-8859-1";
                        }
                        content = baos.toString(cs);
                    }
                    catch (Exception iOException)
                    {
                        LogManager.msg_index(LogManager.LVL_ERR, "Cannot detect characterset for message object " + unique_id + ": " + exc.getMessage());
                    }
                }
            }
            if (content == null)
            {
                LogManager.msg_index(LogManager.LVL_ERR, "Cannot index message object " + unique_id + ": Message has no content");
                // DO NOT RETURN FALSE, WE SAVE ANYWAY, MAYBE WE CAN INDEX IT IN A LATER REVISION
            }
            else if (content instanceof Multipart)
            {
                Multipart mp = (Multipart) content;

                index_mp_content(docw, unique_id, mp);
            }
            else if (content instanceof Part)
            {
                Part p = (Part) content;
                index_part_content(docw, unique_id, p);
            }
            else if (content instanceof String)
            {
                StringReader reader = new StringReader(content.toString());
                doc.add(new Field(CS_Constants.FLD_BODY, reader));
            }
            else if (content instanceof InputStream)
            {
                Reader reader = new InputStreamReader((InputStream) content);
                doc.add(new Field(CS_Constants.FLD_BODY, reader));
            }
            else
            {
                LogManager.msg_index(LogManager.LVL_ERR, "Cannot index message object " + unique_id + ": " + content.getClass().getName());
                // DO NOT RETURN FALSE, WE SAVE ANYWAY, MAYBE WE CAN INDEX IT IN A LATER REVISION
            }

            if (doc.getField(CS_Constants.FLD_ATTACHMENT_NAME) != null)
            {
                doc.add(new Field(CS_Constants.FLD_HAS_ATTACHMENT, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
            // ADD BCC TO MAIL IF NOT ALREADY DONE SO
            if (mail_file.get_bcc_list().size() > 0)
            {
                Field[] bcc_in_mail = doc.getFields(CS_Constants.FLD_BCC);

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
                    {
                        doc.add(new Field(CS_Constants.FLD_BCC, bcc.toString(), Field.Store.YES, Field.Index.ANALYZED));
                    }
                }
            }
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            LogManager.msg_index(LogManager.LVL_ERR,  unique_id, fileNotFoundException);
        }
        catch (IOException iox)
        {
            LogManager.msg_index(LogManager.LVL_ERR,  unique_id, iox);
        }
        catch (MessagingException messagingException)
        {
            LogManager.msg_index(LogManager.LVL_ERR,  unique_id, messagingException);
        }
        return true;
    }

    private String get_charset_from_content_type( String ct )
    {

        if (ct != null)
        {
            ct = ct.toLowerCase();
            int idx = ct.indexOf("charset");
            if (idx >= 0)
            {
                idx += 8;
                if (ct.length() > idx)
                {
                    while (" =\t".indexOf(ct.charAt(idx)) >= 0)
                    {
                        idx++;
                        if (idx >= ct.length())
                        {
                            idx = 0;
                            break;
                        }
                    }

                    String cs = ct.substring(idx);

                    String[] parts = cs.split("[; ,\t]");
                    if (parts.length > 0)
                    {
                        String ret = parts[0];
                        StringTokenizer st = new StringTokenizer(ret, "\"\'[](){}");
                        if (st.hasMoreElements())
                        {
                            return st.nextToken();
                        }
                        else
                        {
                            return ret;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String get_charset( Part p )
    {
        try
        {
            Enumeration mail_header_list = p.getAllHeaders();

            while (mail_header_list.hasMoreElements())
            {
                Object h = mail_header_list.nextElement();
                if (h instanceof Header)
                {
                    Header ih = (Header) h;
                    String name = ih.getName();
                    if (name.equalsIgnoreCase(CS_Constants.FLD_CONTENT_TYPE))
                    {
                        return get_charset_from_content_type(ih.getValue());
                    }
                }
            }
        }
        catch (MessagingException messagingException)
        {
        }
        return null;

    }

    void add_email_field( Document doc, String name, String val )
    {
        StringTokenizer st = new StringTokenizer(val, ", <>\"\t\r\n;");
        while (st.hasMoreTokens())
        {
            String tok = st.nextToken();
            if (tok.indexOf('@') > 0)
            {
                doc.add(new Field(name, tok, Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
        }
    }

    protected void index_headers( Document doc, String uid, Enumeration mail_header_list )
    {
        boolean x_ignore_to = false;
        ArrayList<String> x_envelope_list = new ArrayList<String>();

        while (mail_header_list.hasMoreElements())
        {
            Object h = mail_header_list.nextElement();
            if (h instanceof Header)
            {
                Header ih = (Header) h;
                String name = ih.getName();
                String value = ih.getValue();

                if (name.compareToIgnoreCase(X_IGNORE_TO) == 0)
                {
                    if (value.trim().compareTo("1") == 0 || value.trim().compareToIgnoreCase("true") == 0)
                        x_ignore_to = true;
                }

                boolean found_eh = false;
                boolean found_ah = false;

                // LOOK FOR EMAILHEADERS IN OUR LIST (From To CC BCC)
                String header_field_name = null;
                for (int i = 0; i < email_headers.size(); i++)
                {
                    String eh = email_headers.get(i);
                    if (eh.compareToIgnoreCase(name) == 0)
                    {
                        // SKIP EMPTY "TO"
                        if (eh.equals(CS_Constants.FLD_TO) && value.indexOf("undisclosed") > -1 && value.indexOf("recepients") > -1)
                        {
                            continue;
                        }

                        header_field_name = eh;
                        found_eh = true;
                        break;
                    }
                }

                // LOOK FOR USER FIELDNAME, THEY ARE CASE INSENSITIVE
                if (!found_eh)
                {
                    for (int i = 0; i < allowed_headers.size(); i++)
                    {
                        String ah = allowed_headers.get(i);
                        if (ah.compareToIgnoreCase(name) == 0)
                        {
                            header_field_name = ah;
                            found_ah = true;
                            break;
                        }
                    }
                }

                // SPECIAL: STORE ENVELOPE-TO AND X-ENVELOPE-TO AS TO
                if (name.compareToIgnoreCase(CS_Constants.FLD_ENVELOPE_TO) == 0 || name.compareToIgnoreCase("X-" + CS_Constants.FLD_ENVELOPE_TO) == 0)
                {
                    x_envelope_list.add(value);
                }
                // WE WANT TO ANALYZE , STORE OR BOTH
                // WE NEED 2 FIELDS FOR ONE HEADER, ONE WITH EMAIL ANALYZER, ONE WITH REGULAR ANALYZER
                // WE ADD CHARSET
                else if (name.compareToIgnoreCase(CS_Constants.FLD_CONTENT_TYPE) == 0)
                {
                    String cs = get_charset_from_content_type(value);
                    if (cs != null)
                    {
                        LogManager.msg_index(LogManager.LVL_VERBOSE,  "Mail " + uid + " detected charset " + cs);
                        doc.add(new Field(CS_Constants.FLD_CHARSET, cs, Field.Store.YES, Field.Index.NOT_ANALYZED));
                    }
                }
                else if (found_ah || found_eh)
                {
                    // STORE ALL HEADERS INTO INDEX DB, WE DO INDEX BECAUSE WE SEARCH FOR TOKENS
                    doc.add(new Field(header_field_name, value, Field.Store.YES, Field.Index.ANALYZED));
                    LogManager.msg_index(LogManager.LVL_VERBOSE,  "Mail " + uid + " adding header <" + header_field_name + "> Val <" + value + ">");

                    // THE EMAILFIELDS ARE INDEXED WITH THE STANDARDANALYZER TOO
                    if (found_eh)
                    {
                        doc.add(new Field(header_field_name + "REG", value, Field.Store.NO, Field.Index.ANALYZED));
                    }
                }
                else
                {
                    LogManager.msg_index(LogManager.LVL_VERBOSE,  "Mail " + uid + " skipping header <" + name + "> Val <" + value + ">");
                }
            }
        }

        // IF WE DETECTED SPECIAL BCC IGNORE HEADER WE REMOVE ALL DIRECT ADRESSES
        if (x_ignore_to)
        {
            if  (x_envelope_list.isEmpty())
            {
                LogManager.msg_index(LogManager.LVL_WARN, "Detected mail " + uid + " with " + X_IGNORE_TO + " but without Envelope-To, not deleting To");
            }
            else
            {
                doc.removeFields(CS_Constants.FLD_TO);
                doc.removeFields(CS_Constants.FLD_TO + "REG");
                doc.removeFields(CS_Constants.FLD_CC);
                doc.removeFields(CS_Constants.FLD_CC + "REG");
            }
        }

        // ADD THE COLLECTED ENVELOPES
        for (int i = 0; i < x_envelope_list.size(); i++)
        {
            String mailadr = x_envelope_list.get(i);
            doc.add(new Field(CS_Constants.FLD_TO, mailadr, Field.Store.YES, Field.Index.ANALYZED));
            LogManager.msg_index(LogManager.LVL_VERBOSE,  "Mail " + uid + " adding header Envelope Val <" + mailadr + ">");

            // THE EMAILFIELDS ARE INDEXED WITH THE STANDARDANALYZER TOO
            doc.add(new Field(CS_Constants.FLD_TO + "REG", mailadr, Field.Store.NO, Field.Index.ANALYZED));
        }
    }

    public void index_mp_content( DocumentWrapper doc, String uid, Multipart mp ) throws MessagingException, IOException
    {
        try
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
        catch (Exception messagingException)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in index_mp_content for " + uid + ": " + messagingException.getMessage());
        }
    }

    public void index_part_content( DocumentWrapper doc, String uid, Part p ) throws MessagingException, IOException
    {
        try
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
        catch (Exception messagingException)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in index_part_content for " + uid + ": " + messagingException.getMessage());
        }

    }

    protected void index_content( DocumentWrapper doc, String uid, Part p ) throws MessagingException
    {
        String disposition = p.getDisposition();
        String mimetype = normalize_mimetype(p.getContentType());
        String filename = p.getFileName();

        Charset charset = null;

        // GET CHARSET FROM PART OR DOC OR DEFAULT
        String cs = cs = get_charset(p);
        String doc_cs = doc.get(CS_Constants.FLD_CHARSET);
        if (cs == null)
        {
            cs = doc_cs;
        }
        else
        {
            if (doc_cs == null)
            {
                // WE DETECTED CS BUT DOC HAS NOT ALREADY, WAS MISSING IN HEADER
                doc.doc.add(new Field(CS_Constants.FLD_CHARSET, cs, Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
        }
        if (cs != null)
        {
            try
            {
                charset = Charset.forName(cs.toUpperCase());
            }
            catch (Exception e)
            {
                SortedMap<String, Charset> map = Charset.availableCharsets();
                LogManager.msg_index(LogManager.LVL_WARN,  "Cannot retrieve characterset" + " " + cs.toUpperCase() + ": " + e.getLocalizedMessage());
            }
        }

        if (charset == null)
        {
            charset = utf8_charset;
        }

        MyMimetype mt = new MyMimetype(mimetype);

        try
        {
            // IS THIS PART AN ATTACHMENT ?
            if (filename != null || (disposition != null && disposition.compareToIgnoreCase(Part.ATTACHMENT) == 0))
            {
                if (filename == null)
                {
                    filename = "";
                }

                LogManager.msg_index(LogManager.LVL_DEBUG,  "Indexing attachment " + filename + " MT:<" + mimetype + "> CS:<" + charset + "> to doc " + doc.get_uuid());

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
                    if (doc.get(CS_Constants.FLD_LANG) == null)
                    {
                        Reader detectReader = extractor.getText(p.getInputStream(), doc, mimetype, charset);
                        String[] languages = ((MimePart) p).getContentLanguage();
                        if (languages != null && languages.length > 0)
                        {
                            add_lang_field(languages, doc.doc, detectReader);
                        }
                    }
                }
            }
        }
        catch (Exception ee)
        {
            if (filename == null)
            {
                filename = "";
            }

            LogManager.msg_index(LogManager.LVL_WARN,  "Error in index_content for " + uid + " " + filename + " mime_type <" + mimetype + ">: " + ee.getMessage());
            return;
        }

    }

    protected void extract_tgz_file( InputStream is, DocumentWrapper doc, Charset charset )
    {
        try
        {
            extract_tar_file(new GZIPInputStream(is), doc, charset);
        }
        catch (Exception io)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in extract_tgz_file " + doc.get_uuid() + " " + io.getLocalizedMessage());
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
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in extract_tar_file " + doc.get_uuid() + " " + io.getMessage());
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
                LogManager.msg_index(LogManager.LVL_WARN,  "Error in extract_octet_stream: " + doc.get_uuid() + " " + io.getMessage());
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
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in extract_gzip_file " + doc.get_uuid() + " " + io.getMessage());
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

                LogManager.msg_index(LogManager.LVL_DEBUG,  "Indexing zip entry " + name + " + to " + doc.get_uuid());
                String extention = name.substring(dot + 1, name.length());

                ZipEntryInputStream zeis = new ZipEntryInputStream(zis, entry);


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
                    LogManager.msg_index(LogManager.LVL_WARN,  "Error while extracting text from zip_entry " + name + " " + extractionException.getMessage());
                }
            }
        }
        catch (IllegalArgumentException wrong_zip_entry)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in zip file " + doc.get_uuid() + " " + wrong_zip_entry.getMessage());
        }
        catch (Exception io)
        {
            LogManager.msg_index(LogManager.LVL_WARN,  "Error in extract_zip_file " + doc.get_uuid() + " " + io.getMessage());
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
                LogManager.msg_index(LogManager.LVL_WARN,  "Error while detecting language: ", e);
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



    void clean_up_index_buffer()
    {
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
                    boolean encoded = DiskSpaceHandler.is_encoded_from_uuid(uuid);

                    DiskVault dv = m_ctx.get_vault_by_da_id(da_id);
                    if (dv == null)
                    {
                        LogManager.msg_index(LogManager.LVL_ERR, "Found mail without diskvault");
                        continue;
                    }
                    DiskSpaceHandler index_dsh = dv.get_dsh(ds_id);
                    if (index_dsh == null)
                    {
                        LogManager.msg_index(LogManager.LVL_ERR, "Found mail without diskspace");
                        continue;
                    }
                    index_dsh = dv.open_dsh(index_dsh, 1024 * 1024);

                    if (index_dsh != null)
                    {
                        RFCFileMail msg = new RFCFileMail(file, new Date(time), encoded);

                        // TRY INDEX IN FOREGROUND
                        if (!handle_IndexJobEntry(m_ctx, uuid, da_id, ds_id, index_dsh, msg,
                                        /*delete_after_index*/ true, /*parallel_index*/ false, /*skip_account_match*/ false))
                        {
                            // IF THIS FAILES, WE DEL MSG -> NOT IN INDEX
                            // THIS SHOULD ONLY HAPPEN ON HEAVILY BROKEN FILES
                            LogManager.msg_index(LogManager.LVL_WARN, "Removing broken index mail " + uuid);
                            msg.delete();
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Error while cleaning up index hold buffer", e);
            LogManager.printStackTrace(e);
        }

    }
    @Override
    public boolean start_run_loop()
    {
        LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_INDEX,  "Starting Indexmanager");



        clean_up_index_buffer();


        if (!is_started)
        {

            idle_worker = new BackgroundWorker(getName() + ".Idle")
            {

                @Override
                public Object construct()
                {
                    do_idle();

                    return null;
                }
            };

            idle_worker.start();
            is_started = true;
        }

        this.setStatusTxt(ST_IDLE);
        this.setGoodState(true);
        return true;
    }

    void work_jobs()
    {
        synchronized (index_job_list)
        {
            if (index_job_list.isEmpty())
            {
                return;
            }
        }

        setStatusTxt(Main.Txt("Updating_index"));

        int max_jobs_in_a_row = 1000;

        while (!isShutdown() && max_jobs_in_a_row > 0)
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
            max_jobs_in_a_row--;
        }

        clrStatusTxt(Main.Txt("Updating_index"));
    }

    void do_idle()
    {
        long last_index_flush = 0;
        

        while (!isShutdown())
        {
            if (index_buffer_test)
            {
                clean_up_index_buffer();
            }
            else
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
        
        finished = true;
    }

    public String get_status_txt()
    {
        //StringBuffer stb = new StringBuffer();

        return "";
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

    public void create_IndexJobEntry_task( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, RFCGenericMail msg, boolean delete_after_index, boolean skip_account_match ) throws IndexException
    {
        IndexJobEntry ije = new IndexJobEntry(this, m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index, skip_account_match);

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
            throw new IndexException(exc.getMessage());
        }

        synchronized (index_job_list)
        {
            index_job_list.add(ije);
        }
    }

    public boolean handle_IndexJobEntry( MandantContext m_ctx, String uuid, int da_id, int ds_id, DiskSpaceHandler index_dsh, 
            RFCGenericMail msg, boolean delete_after_index, boolean parallel_index, boolean skip_account_match )
    {
        IndexJobEntry ije = new IndexJobEntry(this, m_ctx, uuid, da_id, ds_id, index_dsh, msg, delete_after_index, skip_account_match);
        return ije.handle_index(parallel_index);
    }


    static public boolean doc_field_exists( Document doc, String fld )
    {
        String val = doc.get(fld);
        if (val != null)
        {
            return true;
        }

        return false;
    }

    static int _doc_get_int( Document doc, String fld ) throws Exception
    {
        String val = doc.get(fld);
        if (val == null)
        {
            throw new Exception("field " + fld + " does not exist");
        }

        return Integer.parseInt(val);
    }

    static long _doc_get_long( Document doc, String fld, int radix ) throws Exception
    {
        String val = doc.get(fld);
        if (val == null)
        {
            throw new Exception("field " + fld + " does not exist");
        }

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
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot parse int field " + fld + " from index", exception);
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
            {
                ret = true;
            }
        }
        catch (Exception exception)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot parse bool field " + fld + " from index", exception);
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
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot parse hex long field " + fld + " from index", exception);
        }

        return ret;
    }


    static void update_document( Document doc, DiskSpaceHandler index_dsh ) throws CorruptIndexException, IOException, VaultException, IndexException
    {
        IndexWriter writer = index_dsh.get_write_index();
        if (writer == null)
        {
            writer = index_dsh.open_write_index();
        }

        Term term = new Term(CS_Constants.FLD_UID_NAME, doc.get(CS_Constants.FLD_UID_NAME));
        
        // SHOVE IT RIGHT OUT!!!
        synchronized (index_dsh.idx_lock)
        {
            writer.updateDocument(term, doc);
        }
    }

    public static boolean handle_bcc_and_update( DiskSpaceHandler index_dsh, Document doc, RFCGenericMail msg )
    {
        boolean needs_updated_index = false;
        // TODO: DO WE REALLY NEED THE SECOND ENTRY? THE HASH SAYS THE MESSAGE IS IDENTICAL, SO WHY BOTHER?
        // NOW WE CHECK IF ALL BCC ARE IN MESSAGE, IF NOT WE NEED A NEW INDEX
        if (msg.get_bcc_list().size() > 0)
        {
            Field[] bcc_fields = doc.getFields(CS_Constants.FLD_BCC);
            for (int i = 0; i < msg.get_bcc_list().size(); i++)
            {
                Address mail_bcc = msg.get_bcc_list().get(i);

                for (int f = 0; f < bcc_fields.length; f++)
                {
                    String doc_bcc = bcc_fields[f].stringValue();
                    if (!doc_bcc.contains(mail_bcc.toString()))
                    {
                        needs_updated_index = true;
                        doc.add(new Field(CS_Constants.FLD_BCC, mail_bcc.toString(), Field.Store.YES, Field.Index.ANALYZED));
                    }
                }
            }
        }

        int da_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DA);
        int ds_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DS);

        if (needs_updated_index)
        {
            try
            {
                // IF WE CAN UPDATE, EVERY THING IS FINE, DEl MESSAGE
                LogManager.msg_index(LogManager.LVL_INFO,  "Updating index for existing mail " + doc.get(CS_Constants.FLD_UID_NAME));
                update_document(doc, index_dsh);

                // AND MARK AS "BEEN OFFICIALLY PIMPED"
                return true;
            }
            catch (Exception ex)
            {
                LogManager.msg_index(LogManager.LVL_ERR,  "Cannot update index for existing mail " + doc.get(CS_Constants.FLD_UID_NAME), ex);
                // CONTINUE ANYWAY INDEX COULD BE READ ONLY
                }
        }
        else
        {
            LogManager.msg_index(LogManager.LVL_INFO,  "Skipping existing mail " + doc.get(CS_Constants.FLD_UID_NAME) + ",  exists already in da:" + da_id + " ds:" + ds_id);
            return true;
        }
        return false;
    }


    public void create_hash_searcher_list() throws VaultException, IOException, IndexException
    {
        ArrayList<Vault> vault_array = m_ctx.getVaultArray();
        for (int i = 0; i < vault_array.size(); i++)
        {
            Vault vault = vault_array.get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault) vault;
                for (int j = 0; j < dv.get_dsh_list().size(); j++)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
                    if (dsh.is_disabled())
                    {
                        continue;
                    }
                    dsh.create_hash_checker();
                }
            }
        }
    }

    private void delete_mail_before_index( MandantContext m_ctx, String unique_id, int da_id, int ds_id )
    {
        DiskSpaceHandler dsh = m_ctx.get_dsh(ds_id);
        try
        {
            long date = DiskSpaceHandler.get_time_from_uuid(unique_id);

            dsh.delete_mail(date, dsh.get_enc_mode(), dsh.get_fmode());
        }
        catch (VaultException vaultException)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot delete mail " + unique_id + ": ", vaultException);
        }
    }


/**
 * Creates an index called 'index' in a temporary directory.
 * The number of documents to add to this index, the mergeFactor and
 * the maxMergeDocs must be specified on the command line
 * in that order - this class expects to be called correctly.
 *
 * Note: before running this for the first time, manually create the
 * directory called 'index' in your temporary directory.
 */
    public static void main(String[] args) throws Exception
    {
        int docsInIndex  = Integer.parseInt(args[0]);

        // create an index called 'index' in a temporary directory
        File indexDir =  new File("m:/tmp/lucenetest");
        FSDirectory dir = FSDirectory.open(indexDir);

        Analyzer    analyzer = new StopAnalyzer(Version.LUCENE_24);
        IndexWriter writer   = new IndexWriter(dir, analyzer, true, new IndexWriter.MaxFieldLength(100000) );

        IndexReader reader = IndexReader.open(dir, /*rd_only*/ true);

        // set variables that affect speed of indexing
        if (args.length > 1)
            writer.setMergeFactor( Integer.parseInt(args[1]) );
        if (args.length > 2)
            writer.setMaxMergeDocs( Integer.parseInt(args[2]) );

        long startTime = System.currentTimeMillis();
        MessageDigest md = null;
        md = MessageDigest.getInstance("SHA-256");

        boolean with_idx = true;

        long last_now = System.currentTimeMillis();
        int last_docs = 0;
        for (int i = 0; i < docsInIndex; i++)
        {
            long now = System.currentTimeMillis();
            String data = "Bibamus, moriendum est e  dkfjlkfd  slkdjf lskd rotke erojt e öotrj liejrtopiejrot erijtoerti operutoieurto iueroi t" + i;
            Document doc = new Document();
            String str = new String( Base64.encodeBase64(md.digest(data.getBytes())));
            doc.add(new Field("fieldname1", str + now, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("fieldname2", str + now + 1, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("fieldname3", str + now + 2, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("fieldname4", str + now + 3, Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field("fieldname5", str + now + 4, Field.Store.YES, Field.Index.ANALYZED));
            if (with_idx)
            {
                 IndexReader newReader = reader.reopen();
                 if (newReader != reader)
                 {
                        System.out.println("Reopen detected");
                        reader.close();
                 }
                 reader = newReader;

                int docs = reader.numDocs();
                if (docs != last_docs)
                {
                    System.out.println("Docs in index: " +  docs + "/" + i);
                    last_docs = docs;
                }
            }
            writer.addDocument(doc);
            
            if (i % 1000 == 0)
            {
                long diff = now - last_now;
                if (diff == 0)
                    diff = 1;
                last_now = now;
                System.out.println("Speed: " + Double.toString(1000.0*1000.0 / diff)+ " 1/s");
                writer.commit();
            }
        }

        reader.close();

        long closeTime = System.currentTimeMillis();
        writer.close();
        long stopTime = System.currentTimeMillis();
        System.out.println("Close time: " + (stopTime - closeTime) + " ms");
        System.out.println("Total time: " + (stopTime - startTime) + " ms");
    }
/*
    public int get_index_thread_pool_entries()
    {
        return index_run_thread_pool..get_busy_threads();
    }
*/

    Future<IndexJobEntry> execute_run( final IndexJobEntry aThis ) throws InterruptedException
    {
        //index_run_thread_pool.execute(aThis);
       // System.out.println("Exec Queue cap    :" + index_run_thread_pool.getQueue().remainingCapacity());
       // System.out.println("Exec Thread active:" + index_run_thread_pool.getActiveCount() );
        Future<IndexJobEntry> result = index_run_thread_pool.submit(new Callable<IndexJobEntry>()
        {

            @Override
            public IndexJobEntry call() throws Exception
            {
                 aThis.index_mail_parallel();
                 return aThis;
            }
        });
        return result;

    }
    Future<IndexJobEntry> execute_single_run( final IndexJobEntry aThis ) throws InterruptedException
    {
        //index_run_single_thread_pool.execute(aThis);
        Future<IndexJobEntry> result = index_run_single_thread_pool.submit(new Callable<IndexJobEntry>()
        {

            @Override
            public IndexJobEntry call() throws Exception
            {
                 aThis.index_mail_sequential();
                 return aThis;
            }
        });
        return result;

    }
    Future<IndexJobEntry> execute_load( final IndexJobEntry aThis ) throws InterruptedException
    {
        Future<IndexJobEntry> result = index_load_thread_pool.submit(new Callable<IndexJobEntry>()
        {

            @Override
            public IndexJobEntry call() throws Exception
            {
                 aThis.load_mail_file(false);
                 return aThis;
            }
        });
        return result;
    }


    public boolean index_thread_pool_finished()
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void restart_index_thread_pool()
    {
        LogManager.msg_index(LogManager.LVL_INFO, "Clearing index buffer");

        boolean pool_finished = false;
        while (true && index_run_thread_pool != null)
        {
            if (m_ctx.getThreadWatcher().shutdown_thread_pool(index_run_thread_pool, 1000))
            {
                pool_finished = true;
                break;
            }
            
            if (isShutdown())
                break;
        }
        if (pool_finished)
            LogManager.msg_index(LogManager.LVL_INFO, "Index buffer was cleared");
        else
            LogManager.msg_index(LogManager.LVL_ERR, "Index buffer was not cleared");


        int index_threads = (int)Main.get_long_prop(GeneralPreferences.INDEX_MAIL_THREADS, MAX_INDEX_THREADS);
        index_run_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "IndexMail" , index_threads, 10 );
        
    }
    public void abort_and_restart_index_thread_pool()
    {
        LogManager.msg_index(LogManager.LVL_INFO, "Aborting index buffer");

        boolean pool_finished = false;
        while (true && index_run_thread_pool != null)
        {
            if (m_ctx.getThreadWatcher().abort_thread_pool(index_run_thread_pool))
            {
                pool_finished = true;
                break;
            }

            if (isShutdown())
                break;
        }
        if (pool_finished)
            LogManager.msg_index(LogManager.LVL_INFO, "Index buffer was aborted");
        else
            LogManager.msg_index(LogManager.LVL_ERR, "Index buffer was not aborted");


        int index_threads = (int)Main.get_long_prop(GeneralPreferences.INDEX_MAIL_THREADS, MAX_INDEX_THREADS);
        index_run_thread_pool = m_ctx.getThreadWatcher().create_blocking_thread_pool( "IndexMail" , index_threads, 10 );
    }


    @Override
    public String get_task_status()
    {
        StringBuilder stb = new StringBuilder();

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

    @Override
    public String get_task_status( int ma_id )
    {
        StringBuilder stb = new StringBuilder();

        synchronized (index_job_list)
        {
            for (int i = 0; i < index_job_list.size(); i++)
            {
                IndexJobEntry mbie = index_job_list.get(i);
                if (mbie.get_ma_id() > 0 && mbie.get_ma_id() != ma_id)
                    continue;

                stb.append("IJEID");
                stb.append(i);
                stb.append(":");
                stb.append(mbie.unique_id);
                stb.append("\n");
            }
        }
        return stb.toString();
    }
}

