/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index;

import com.sun.mail.smtp.SMTPTransport;
import com.thoughtworks.xstream.XStream;
import dimm.home.auth.SMTPAuth;
import dimm.home.auth.SMTPUserContext;
import home.shared.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.CS_Constants.USERMODE;
import home.shared.filter.ExprEntry;
import home.shared.filter.FilterMatcher;
import home.shared.filter.GroupEntry;
import home.shared.filter.LogicEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ParallelMultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searchable;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author mw
 */
public class SearchCall
{

    MandantContext m_ctx;
    ArrayList<SearchResult> result;
    ArrayList<IndexSearcher> searcher_list;

    MandantContext get_ctx()
    {
        return m_ctx;
    }
    static final ArrayList<SearchCallEntry> call_list = new ArrayList<SearchCallEntry>();

    static SearchCallEntry get_sce( String sce_id )
    {
        int id = Integer.parseInt(sce_id.substring(2));  // scN
        for (int i = 0; i < call_list.size(); i++)
        {
            SearchCallEntry sce = call_list.get(i);
            if (sce.id == id)
            {
                return sce;
            }
        }
        return null;
    }

    public static String open_search_call( int ma_id, String mail, String field, String val, int n )
    {
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(ma_id);
        if (m_ctx == null)
        {
            return "1: invalid mandant";
        }
        SearchCall sc = new SearchCall(m_ctx);
        sc.search(mail, field, val, n);

        int id = 0;
        synchronized (call_list)
        {
            id = call_list.size();
            SearchCallEntry sce = new SearchCallEntry(sc, id);
            call_list.add(sce);
        }

        return "0: sc" + id + " N:" + sc.result.size();
    }

    public static String open_filtersearch_call( int ma_id, String compressed_filter, int n, String user, String pwd, USERMODE level, boolean with_imap )
    {
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(ma_id);
        if (m_ctx == null)
        {
            return "1: invalid mandant";
        }
        SearchCall sc = new SearchCall(m_ctx);
        try
        {
            sc.search_lucene(user, pwd, compressed_filter, n, level);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return "2: " + e.getMessage();
        }
        int id = 0;
        synchronized (call_list)
        {
            id = call_list.size();
            SearchCallEntry sce = new SearchCallEntry(sc, id);
            call_list.add(sce);
        }
        if (m_ctx.get_imap_server() != null)
        {
            m_ctx.get_imap_server().set_search_results( sc, user, pwd );
        }

        return "0: sc" + id + " N:" + sc.result.size();
    }

    public static String close_search_call( String id )
    {
        SearchCallEntry sce = get_sce(id);
        if (sce == null)
        {
            return "1: invalid sce";
        }
        call_list.remove(sce);

        sce.call.close();


        return "0: ok";
    }

    static ArrayList<String> retrieve_row( SearchCallEntry sce, ArrayList<String> field_list, int row )
    {
        SearchResult result = sce.call.result.get(row);
        try
        {
            ArrayList<String> row_list = new ArrayList<String>();
            Document doc = sce.call.get_doc(result.getSearcher(), result.getDoc_index());

            for (int j = 0; j < field_list.size(); j++)
            {
                String field = field_list.get(j);
                String val = doc.get(field);
                row_list.add(val);
            }
            return row_list;
        }
        catch (Exception iOException)
        {
            LogManager.err_log_fatal("Cannot retrieve results from index", iOException);
        }
        return null;
    }

    public static String retrieve_search_call( String id, ArrayList<String> field_list, int row )
    {
        SearchCallEntry sce = get_sce(id);
        if (sce == null)
        {
            return "1: invalid sce";
        }

        ArrayList<ArrayList<String>> result_list = new ArrayList<ArrayList<String>>();

        if (row == -1)
        {
            for (int i = 0; i < sce.call.result.size(); i++)
            {

                ArrayList<String> row_list = retrieve_row(sce, field_list, i);
                if (row_list != null)
                {
                    result_list.add(row_list);
                }
            }
        }
        else
        {
            ArrayList<String> row_list = retrieve_row(sce, field_list, row);
            if (row_list != null)
            {
                result_list.add(row_list);
            }
        }

        XStream xstream = new XStream();
        String res = xstream.toXML(result_list);

        return "0: " + res;
    }

    public static String retrieve_mail( String id, int row )
    {
        String ret = null;

        SearchCallEntry sce = get_sce(id);
        if (sce == null)
        {
            return "1: invalid sce";
        }
        SearchResult result = sce.call.result.get(row);
        if (result == null)
        {
            return "2: invalid result";
        }

        ret = sce.call.open_RMX_mail_stream(result);

        if (ret == null)
        {
            return "3: cannot open mail for result";
        }

        return ret;
    }

    public static String send_mail( String id, int[] rowi, String send_to )
    {
        SearchCallEntry sce = get_sce(id);
        if (sce == null)
        {
            return "1: invalid sce";
        }
        MandantContext m_ctx = sce.call.get_ctx();

        String host = m_ctx.getMandant().getSmtp_host();
        int port = m_ctx.getMandant().getSmtp_port();
        SMTPAuth smtp = new SMTPAuth(host, port, m_ctx.getMandant().getSmtp_flags());

        if (!smtp.connect())
        {
            return "2: " + Main.Txt("Cannot_connect_to_SMTP_host") + " " + host + ":" + port;
        }

        try
        {
            String user = m_ctx.getMandant().getSmtp_user();
            String pwd = m_ctx.getMandant().getSmtp_pwd();
            SMTPUserContext smtp_ctx = smtp.open_user(user, pwd, null);

            if (smtp_ctx == null)
            {
                return "3: " + Main.Txt("Cannot_authenticate_at_SMTP_host") + " " + host + ":" + port + " user " + user + " to " + send_to;
            }
            SMTPTransport transport = smtp_ctx.get_transport();

            String[] adr_list = send_to.split(",");
            Address[] addresses = new Address[adr_list.length];
            try
            {
                for (int i = 0; i < adr_list.length; i++)
                {
                    addresses[i] = new InternetAddress(adr_list[i], false);
                }
            }
            catch (AddressException addressException)
            {
                return "4: " + Main.Txt("Invalid_To_address") + ":" + send_to;
            }

            for (int i = 0; i < rowi.length; i++)
            {
                int j = rowi[i];

                SearchResult result = sce.call.result.get(rowi[i]);
                DiskSpaceHandler dsh = m_ctx.get_dsh(result.getDs_id());
                Vault vault = m_ctx.get_vault_for_ds_idx(result.getDs_id());
                try
                {
                    long time = DiskSpaceHandler.get_time_from_uuid(result.getUuid());

                    RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode());
                    InputStream is = mail.open_inputstream();
                    Properties props = new Properties();
                    props.put("mail.smtp.host", host);
                    Session session = Session.getDefaultInstance(props);
                    Message msg = new MimeMessage(session, is);

                    transport.sendMessage(msg, addresses);

                    is.close();

                }
                catch (VaultException vaultException)
                {
                    return "5: " + Main.Txt("Cannot_get_message_from_vault") + ":" + vaultException.getMessage();
                }
                catch (Exception messagingException)
                {
                    return "6: " + Main.Txt("Error_while_sending_message") + ":" + messagingException.getMessage();
                }
            }
        }
        finally
        {
            smtp.close_user_context();
            smtp.disconnect();
        }



        return "0: ok";
    }

    public SearchCall( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
        result = new ArrayList<SearchResult>();
        searcher_list = new ArrayList<IndexSearcher>();
    }
    static DiskSpaceHandler last_dsh;

    Document get_doc( Searcher searcher, int doc_index ) throws CorruptIndexException, IOException
    {
        return searcher.doc(doc_index);
    }

    public void search_lucene( String user, String pwd, String compressed_filter, int n, USERMODE level ) throws IOException, IllegalArgumentException, ParseException
    {
        ArrayList<LogicEntry> logic_list = FilterMatcher.get_filter_list(compressed_filter, true);
        search_lucene(user, pwd, logic_list, n, level);
    }

    public void search_lucene( String user, String pwd, ArrayList<LogicEntry> logic_list, int n, USERMODE level ) throws IOException, IllegalArgumentException, ParseException
    {

        ArrayList<DiskSpaceHandler> dsh_list = create_dsh_list();
        if (dsh_list.size() == 0)
        {
            throw new IllegalArgumentException(Main.Txt("No_disk_spaces_for_search_found"));
        }
        Analyzer ana = dsh_list.get(0).create_read_analyzer();


        // BUILD USER FILTER
        TermsFilter filter = null;
        if (level != USERMODE.UL_ADMIN && level != USERMODE.UL_SYSADMIN)
        {
            ArrayList<String> mail_aliases = m_ctx.get_mailaliases(user, pwd);
            if (mail_aliases == null || mail_aliases.size() == 0)
            {
                throw new IllegalArgumentException(Main.Txt("No_mail_address_for_this_user"));
            }
            //           filter = null;

            filter = build_lucene_filter(mail_aliases);
        }

        Query qry = build_lucene_qry(logic_list, ana);
        LogManager.debug_msg(2, "Qry is: " + qry.toString());

        run_lucene_searcher(dsh_list, qry, filter, n);
    }

    ArrayList<DiskSpaceHandler> create_dsh_list()
    {
        ArrayList<DiskSpaceHandler> dsh_list = new ArrayList<DiskSpaceHandler>();

        for (int v_idx = m_ctx.getVaultArray().size() - 1; v_idx >= 0; v_idx--)
        {
            Vault vault = m_ctx.getVaultArray().get(v_idx);
            if (vault instanceof DiskVault)
            {
                // GO THROUGH ALL DISKSPACES OF EACH VAULT
                DiskVault dv = (DiskVault) vault;

                for (int ds_idx = dv.get_dsh_list().size() - 1; ds_idx >= 0; ds_idx--)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(ds_idx);
                    if (dsh.is_disabled())
                    {
                        continue;
                    }

                    if (dsh.is_index())
                    {
                        dsh_list.add(dsh);
                    }
                }
            }
        }
        return dsh_list;
    }

    TermsFilter build_lucene_filter( ArrayList<String> mail_aliases )
    {
        TermsFilter filter = null;

        filter = new TermsFilter();
        for (int i = 0; i < mail_aliases.size(); i++)
        {
            String mail_adress = mail_aliases.get(i);
            if (mail_adress.length() > 0)
            {

                filter.addTerm(new Term(CS_Constants.FLD_TO, mail_adress));
                filter.addTerm(new Term(CS_Constants.FLD_FROM, mail_adress));
                filter.addTerm(new Term(CS_Constants.FLD_CC, mail_adress));
                filter.addTerm(new Term(CS_Constants.FLD_BCC, mail_adress));
                filter.addTerm(new Term(CS_Constants.FLD_DELIVEREDTO, mail_adress));
            }
        }
        return filter;
    }

    static void add_level_txt( StringBuffer sb, int level, String txt )
    {
        sb.append(" ");
        sb.append(txt);
        sb.append(" ");
    }

    static String get_op_val_txt( ExprEntry entry )
    {
        switch (entry.getOperation())
        {
            case BEGINS_WITH:
                return entry.getValue() + "*";
            case ENDS_WITH:
                return "*" + entry.getValue();
            case CONTAINS_SUBSTR:
                return "[*]" + entry.getValue() + "[*]";
            case CONTAINS:
                return entry.getValue();
            case REGEXP:
                return entry.getValue();
        }
        return "???";
    }

    static String get_lucene_txt( MandantContext m_ctx, ExprEntry e )
    {
        String val_txt = get_op_val_txt(e);
        String name = e.getName();

        return (e.isNeg() ? "NOT " : "") + name + ":" + val_txt;
    }

    public static void gather_lucene_qry_text( MandantContext m_ctx, StringBuffer sb, ArrayList<LogicEntry> list, int level )
    {
        for (int i = 0; i < list.size(); i++)
        {
            LogicEntry logicEntry = list.get(i);

            if (i > 0)
            {
                if (logicEntry.isPrevious_is_or())
                {
                    add_level_txt(sb, level, "OR");
                }
                else
                {
                    add_level_txt(sb, level, "AND");
                }
            }

            if (logicEntry instanceof GroupEntry)
            {
                if (logicEntry.isNeg())
                {
                    add_level_txt(sb, level, "NOT" + " (");
                }
                else
                {
                    add_level_txt(sb, level, "(");
                }

                gather_lucene_qry_text(m_ctx, sb, ((GroupEntry) logicEntry).getChildren(), level + 1);

                add_level_txt(sb, level, ")");
            }
            if (logicEntry instanceof ExprEntry)
            {
                ExprEntry expe = (ExprEntry) logicEntry;
                String txt = get_lucene_txt( m_ctx, expe);
                add_level_txt(sb, level, txt);
            }
        }
    }

    Query build_lucene_qry( ArrayList<LogicEntry> logic_list, Analyzer ana ) throws ParseException
    {
        StringBuffer sb = new StringBuffer();

        gather_lucene_qry_text( m_ctx, sb, logic_list, 0);

        LogManager.debug_msg(2, "QueryParser: " + sb.toString());

        QueryParser parser = new QueryParser("FLDN_BODY", ana);
        parser.setAllowLeadingWildcard(true);

        Query qry = parser.parse(sb.toString());

        return qry;
    }

    void run_lucene_searcher( ArrayList<DiskSpaceHandler> dsh_list, Query qry, Filter filter, int n ) throws IOException
    {
        // RESET LISTS
        result = new ArrayList<SearchResult>();
        searcher_list = new ArrayList<IndexSearcher>();

        // FIRST PASS, OPEN INDEX READERS
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            IndexReader reader = null;
            try
            {
                reader = dsh.create_read_index();
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher_list.add(searcher);
            }
            catch (VaultException vaultException)
            {
                LogManager.err_log("Cannot open index " + dsh.getDs().getPath(), vaultException);
            }
        }

        // BUILD SEARCHABLE ARRAY
        Searchable[] search_arr = new Searchable[dsh_list.size()];
        for (int i = 0; i < searcher_list.size(); i++)
        {
            search_arr[i] = searcher_list.get(i);
        }

        // SORT BY DATE REVERSE
        Sort sort = new Sort(CS_Constants.FLD_TM, /*rev*/ true);

        // PARALLEL SEARCH
        ParallelMultiSearcher pms = new ParallelMultiSearcher(search_arr);

        // SSSSEEEEAAAARRRRCHHHHHHH

        TopDocs tdocs = pms.search(qry, filter, n, sort);


        ScoreDoc[] sdocs = tdocs.scoreDocs;
        for (int k = 0; k < sdocs.length; k++)
        {
            ScoreDoc scoreDoc = sdocs[k];

            int doc_idx = scoreDoc.doc;
            float score = scoreDoc.score;

            Document doc = pms.doc(doc_idx);

            int da_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DA);
            int ds_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DS);
            long size = IndexManager.doc_get_hex_long(doc, CS_Constants.FLD_SIZE);

            String uuid = doc.get(CS_Constants.FLD_UID_NAME);
            long time = IndexManager.doc_get_hex_long(doc, CS_Constants.FLD_DATE); // HEX!!!!!
            String subject = doc.get(CS_Constants.FLD_SUBJECT);

            boolean has_attachment = false;
            if (IndexManager.doc_field_exists(doc, CS_Constants.FLD_HAS_ATTACHMENT))
            {
                has_attachment = IndexManager.doc_get_bool(doc, CS_Constants.FLD_HAS_ATTACHMENT);
            }

            SearchResult rs = new SearchResult(pms, doc_idx, score, da_id, ds_id, uuid, time, size, subject, has_attachment);
            result.add(rs);

            // STOP AFTER N RESULTS
            if (result.size() >= n)
            {
                break;
            }
        }
    }

    void search( String mail_adress, String fld, String val, int n )
    {
        // GO THROUGH ALL VAULTS
        for (int v_idx = m_ctx.getVaultArray().size() - 1; v_idx >= 0; v_idx--)
        {
            Vault vault = m_ctx.getVaultArray().get(v_idx);
            if (vault instanceof DiskVault)
            {
                // GO THROUGH ALL DISKSPACES OF EACH VAULT
                DiskVault dv = (DiskVault) vault;

                for (int ds_idx = dv.get_dsh_list().size() - 1; ds_idx >= 0; ds_idx--)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(ds_idx);
                    if (dsh.is_index())
                    {
                        if (dsh.is_disabled())
                        {
                            continue;
                        }

                        // START A SERACH TODO: DO THIS IN BACKGROUND
                        try
                        {
                            IndexReader reader = dsh.create_read_index();
                            IndexSearcher searcher = new IndexSearcher(reader);

                            FilterIndexReader fir = new FilterIndexReader(reader);

                            try
                            {
                                Query qry = null;
                                if (val.length() > 0)
                                {
                                    Analyzer anal = dsh.create_read_analyzer();
                                    QueryParser qp = new QueryParser(fld, anal);
                                    qry = qp.parse(val);
                                }
                                else
                                {
                                    qry = new MatchAllDocsQuery();
                                }

                                TermsFilter filter = null;

                                if (mail_adress != null && mail_adress.length() > 0)
                                {
                                    filter = new TermsFilter();
                                    filter.addTerm(new Term(CS_Constants.FLD_TO, mail_adress));
                                    filter.addTerm(new Term(CS_Constants.FLD_FROM, mail_adress));
                                    filter.addTerm(new Term(CS_Constants.FLD_CC, mail_adress));
                                    filter.addTerm(new Term(CS_Constants.FLD_BCC, mail_adress));
                                    filter.addTerm(new Term(CS_Constants.FLD_DELIVEREDTO, mail_adress));
                                }

                                Sort sort = new Sort(CS_Constants.FLD_TM);

                                // SSSSEEEEAAAARRRRCHHHHHHH
                                TopDocs tdocs = searcher.search(qry, filter, n);

                                ScoreDoc[] sdocs = tdocs.scoreDocs;
                                for (int k = 0; k < sdocs.length; k++)
                                {
                                    ScoreDoc scoreDoc = sdocs[k];

                                    int doc_idx = scoreDoc.doc;
                                    float score = scoreDoc.score;

                                    Document doc = fir.document(doc_idx);

                                    int da_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DA);
                                    int ds_id = IndexManager.doc_get_int(doc, CS_Constants.FLD_DS);
                                    long size = IndexManager.doc_get_hex_long(doc, CS_Constants.FLD_SIZE);

                                    String uuid = doc.get(CS_Constants.FLD_UID_NAME);
                                    long time = IndexManager.doc_get_hex_long(doc, CS_Constants.FLD_DATE); // HEX!!!!!
                                    String subject = doc.get(CS_Constants.FLD_SUBJECT);

                                    boolean has_attachment = false;
                                    if (IndexManager.doc_field_exists(doc, CS_Constants.FLD_HAS_ATTACHMENT))
                                    {
                                        has_attachment = IndexManager.doc_get_bool(doc, CS_Constants.FLD_HAS_ATTACHMENT);
                                    }

                                    SearchResult rs = new SearchResult(searcher, doc_idx, score, da_id, ds_id, uuid, time, size, subject, has_attachment);
                                    result.add(rs);

                                    // STOP AFTER N INDEX
                                    if (result.size() >= n)
                                    {
                                        break;
                                    }
                                }
                            }
                            catch (ParseException ex)
                            {
                                LogManager.err_log("Cannot parse query", ex);
                            }
                            catch (IOException exception)
                            {
                                LogManager.err_log("Cannot read term doc list from index", exception);
                            }
                        }
                        catch (VaultException vaultException)
                        {
                            LogManager.err_log("Cannot open index " + dsh.getDs().getPath(), vaultException);
                        }
                    }
                }
            }
        }
    }

    void close()
    {
        result.clear();

        // CLOSE INDEX READERS AND SEARCHERS AGAIN
        for (int i = 0; i < searcher_list.size(); i++)
        {
            try
            {
                searcher_list.get(i).getIndexReader().close();
                searcher_list.get(i).close();
            }
            catch (IOException iOException)
            {
            }
        }
        searcher_list.clear();

    }

    private String open_RMX_mail_stream( SearchResult result )
    {


        try
        {
            DiskSpaceHandler dsh = m_ctx.get_dsh(result.getDs_id());
            Vault vault = m_ctx.get_vault_for_ds_idx(result.getDs_id());
            long time = DiskSpaceHandler.get_time_from_uuid(result.getUuid());
            if (dsh == null)
            {
                throw new VaultException(Main.Txt("Cannot_access_diskspace_with_id") + ": " + result.getDs_id());
            }
            if (vault == null)
            {
                throw new VaultException(Main.Txt("Cannot_access_diskvault_with_id") + ": " + result.getDa_id());
            }
            RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode());
            InputStream is = mail.open_inputstream();
            String ret = m_ctx.get_tcp_call_connect().RMX_OpenInStream(is, result.getSize());
            return ret;
        }
        catch (IOException ex)
        {
            LogManager.err_log("Cannot open mail stream", ex);
        }
        catch (VaultException ex)
        {
            LogManager.err_log("Cannot open mail stream", ex);
        }

        return "1: cannot open";
    }

    public RFCGenericMail get_generic_mail_from_res( int idx )
    {
        SearchResult res_entry = this.result.get(idx);
        try
        {
            DiskSpaceHandler dsh = m_ctx.get_dsh(res_entry.getDs_id());
            long time = DiskSpaceHandler.get_time_from_uuid(res_entry.getUuid());

            RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode());


            return mail;
        }
        catch (VaultException ex)
        {
            LogManager.err_log("Cannot open mail stream", ex);
            return null;
        }
    }

    public SearchResult get_res( int idx )
    {
        SearchResult res_entry = this.result.get(idx);
        return res_entry;
    }

    public int get_result_cnt()
    {
        return result.size();
    }
}
