/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index;

import com.sun.mail.smtp.SMTPTransport;
import com.thoughtworks.xstream.XStream;
import dimm.home.auth.SMTPAuth;
import dimm.home.auth.SMTPUserContext;
import dimm.home.mailarchiv.AuditLog;
import dimm.home.mailarchiv.Exceptions.AuthException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import home.shared.SQL.UserSSOEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.CS_Constants.USERMODE;
import home.shared.SQL.OptCBEntry;
import home.shared.filter.ExprEntry;
import home.shared.filter.FilterMatcher;
import home.shared.filter.FilterValProvider;
import home.shared.filter.GroupEntry;
import home.shared.filter.LogicEntry;
import home.shared.hibernate.Role;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import org.apache.lucene.search.ScoreDocComparator;
import org.apache.lucene.search.Searchable;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortComparatorSource;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;


class HexLongComparator implements SortComparatorSource
{

    @Override
    public ScoreDocComparator newComparator(final IndexReader indexReader, final String str) throws IOException
    {
        return new ScoreDocComparator()
        {
            @Override
            public int compare(ScoreDoc scoreDoc1, ScoreDoc scoreDoc2)
            {
                try
                {
                    final Document doc1 = indexReader.document(scoreDoc1.doc);
                    final Document doc2 = indexReader.document(scoreDoc2.doc);
                    final String strVal1 = doc1.get(str);
                    final String strVal2 = doc2.get(str);

                    long l1 = 0;
                    if (strVal1 != null)
                        l1 = Long.parseLong(strVal1, 16);
                    long l2 = 0;
                    if (strVal2 != null)
                        l2 = Long.parseLong(strVal2, 16);


                   // System.out.println("cp:" + l1 + " " + l2);

                    if (l1 > l2)
                        return -1;
                    if (l2 > l1)
                        return 1;
                    return 0;
                } 
                catch (Exception e)
                {
                    LogManager.msg_index(LogManager.LVL_ERR, "Cannot read doc", e);
                }
                return 0;
            }

            @Override
            public Comparable sortValue(ScoreDoc scoreDoc)
            {
                try
                {
                    final Document doc1 = indexReader.document(scoreDoc.doc);
                    final String strVal1 = doc1.get(str);
                    long l1 = Long.parseLong(strVal1, 16);
                    //System.out.println("sv:" + scoreDoc.doc + " : " + l1);
                    return new Long(l1);
                }
                catch (IOException iOException)
                {
                }
                catch (NumberFormatException numberFormatException)
                {
                }
                return new Long(0);
            }

            @Override
            public int sortType()
            {
                return SortField.CUSTOM;
            }
        };
    }
}
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
/*
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
*/
    public static String open_filtersearch_call( int ma_id, String compressed_filter, int n, String user, String pwd, USERMODE level, boolean with_imap )
    {


        MandantContext m_ctx = Main.get_control().get_mandant_by_id(ma_id);
        if (m_ctx == null)
        {
            return "1: invalid mandant";
        }
        UserSSOEntry ssoc = m_ctx.get_from_sso_cache(user, pwd);
        if (ssoc == null)
        {
            try
            {
                if (!m_ctx.authenticate_user(user, pwd))
                {
                    return "2: login failed";
                }
            }
            catch (AuthException authException)
            {
                return "3: login failed: " + authException.getLocalizedMessage();
            }
        }
        ssoc = m_ctx.get_from_sso_cache(user, pwd);
        if (ssoc == null)
        {
            return "4: unauthorized";
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
            SearchCallEntry sce = new SearchCallEntry(ssoc, sc, id);
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

                // HANDLE SPECIAL CASE 4EYES-COL: WE SET Role-ID IF WE HAVE A 4-EYES ROLE MATCHING THIS MAIL
                if (field.compareTo( CS_Constants.VFLD_4EYES) == 0)
                {
                    SearchResult sr = sce.call.result.get(row);
                    Role r = sr.get_role_4eyes();
                    if (r != null)
                    {
                        row_list.add(Integer.toString(r.getId()));
                    }
                    else
                    {
                        row_list.add("");
                    }
                }
                else
                {
                    String val = doc.get(field);
                    row_list.add(val);
                }
            }
            return row_list;
        }
        catch (Exception iOException)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot retrieve results from index", iOException);
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


    public static String retrieve_mail(  String id, int row )
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

        ret = sce.call.open_RMX_mail_stream(sce.getSsoc(), result);

        if (ret == null)
        {
            return "3: cannot open mail for result";
        }

        AuditLog al = AuditLog.getInstance();
        ArrayList<String> args = new ArrayList<String>();
        args.add( "SUB:" );
        args.add(result.getSubject());
        args.add( "UUID:" );
        args.add(result.getUuid());

        al.call_function(sce.getSsoc(), "retrieve_mail", args, ret);



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

                    RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode(), dsh.get_fmode());
                    InputStream is = mail.open_inputstream();
                    Properties props = new Properties();
                    props.put("mail.smtp.host", host);
                    Session session = Session.getDefaultInstance(props);
                    Message msg = new MimeMessage(session, is);

                    transport.sendMessage(msg, addresses);

                    is.close();

                    // AUDIT LOG
                    AuditLog al = AuditLog.getInstance();
                    ArrayList<String> args = new ArrayList<String>();
                    args.add( "SUB:" );
                    args.add(result.getSubject());
                    args.add( "UUID:" );
                    args.add(result.getUuid());
                    args.add( "TO:" );
                    args.add(send_to);

                    al.call_function(sce.getSsoc(), "send_mail", args, "");
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

    private boolean can_view_all_mails( UserSSOEntry ssoc, USERMODE level)
    {
        if (level == USERMODE.UL_ADMIN)
            return true;
        if (level == USERMODE.UL_SYSADMIN)
            return true;

        if ( ssoc.is_auditor())
            return true;

        if ( ssoc.is_admin())
            return true;
            
        return false;
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
        UserSSOEntry ssoc = m_ctx.get_from_sso_cache(user, pwd);

        boolean view_all = can_view_all_mails(ssoc, level);

        if (!view_all)
        {
            ArrayList<String> mail_aliases = m_ctx.get_mailaliases(user, pwd);
            if (mail_aliases == null || mail_aliases.size() == 0)
            {
                throw new IllegalArgumentException(Main.Txt("No_mail_address_for_this_user"));
            }            

            filter = build_lucene_filter(mail_aliases);
        }

        

        Query qry = build_lucene_qry(logic_list, ana);
        LogManager.msg_index(LogManager.LVL_DEBUG,  "Qry is: " + qry.toString());

        run_lucene_searcher(dsh_list, qry, filter, n, level, ssoc);
    }

    public void search_lucene_qry_str( String user, String pwd, String lucene_qry, int n, USERMODE level ) throws IOException, IllegalArgumentException, ParseException
    {

        ArrayList<DiskSpaceHandler> dsh_list = create_dsh_list();
        if (dsh_list.size() == 0)
        {
            throw new IllegalArgumentException(Main.Txt("No_disk_spaces_for_search_found"));
        }
        Analyzer ana = dsh_list.get(0).create_read_analyzer();


        // BUILD USER FILTER
        TermsFilter filter = null;
        UserSSOEntry ssoc = m_ctx.get_from_sso_cache(user, pwd);
        
        boolean view_all = can_view_all_mails(ssoc, level);

        if (view_all)
        {
            ArrayList<String> mail_aliases = m_ctx.get_mailaliases(user, pwd);
            if (mail_aliases == null || mail_aliases.size() == 0)
            {
                throw new IllegalArgumentException(Main.Txt("No_mail_address_for_this_user"));
            }
            //           filter = null;

            filter = build_lucene_filter(mail_aliases);
        }

        

        Query qry = build_lucene_qry(lucene_qry, ana);
        LogManager.msg_index(LogManager.LVL_DEBUG,  "Qry is: " + lucene_qry);

        run_lucene_searcher(dsh_list, qry, filter, n, level, ssoc);
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
                ArrayList<String> mail_headers = m_ctx.get_index_manager().get_email_headers();
                for (int m = 0; m < mail_headers.size(); m++)
                {
                    String field_name = mail_headers.get(m);
                    filter.addTerm(new Term(field_name, mail_adress));
                }
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
                return "*" + entry.getValue() + "*";
            case CONTAINS:
                return entry.getValue();
            case REGEXP:
                return entry.getValue();
        }
        return "???";
    }

    static String get_lucene_txt( MandantContext m_ctx, ExprEntry e )
    {
        String name = e.getName();
        String[] flist = null;
        StringBuffer sb = new StringBuffer();

        // HANDLE VIRTUAL FIELDS
        if (name.compareTo( CS_Constants.VFLD_MAIL) == 0)
        {
            flist = CS_Constants.VFLD_MAIL_FIELDS;
        }
        else if (name.compareTo( CS_Constants.VFLD_TXT) == 0)
        {
            flist = CS_Constants.VFLD_TXT_FIELDS;
        }
        else if (name.compareTo( CS_Constants.VFLD_ALL) == 0)
        {
            flist = CS_Constants.VFLD_ALL_FIELDS;
        }
        if (flist != null)
        {
            if (e.isNeg())
                sb.append("NOT ");
            sb.append("(");
            for (int i = 0; i < flist.length; i++)
            {
                if ( i > 0)
                    sb.append(" OR ");

                String field = flist[i];
                String val_txt = get_op_val_txt(e);

                 sb.append( field );
                 sb.append(":");
                 sb.append(val_txt);
            }
            sb.append(")");
        }
        else
        {
            String val_txt = get_op_val_txt(e);

            if (e.isNeg())
                sb.append("NOT ");
            sb.append( name );
            sb.append(":");
            sb.append(val_txt);
        }
        return sb.toString();
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

        LogManager.msg_index(LogManager.LVL_DEBUG,  "QueryParser: " + sb.toString());

        QueryParser parser = new QueryParser("FLDN_BODY", ana);
        parser.setAllowLeadingWildcard(true);

        String qry_str = sb.toString();
        if (qry_str.length() == 0)
        {
            return new MatchAllDocsQuery();
        }
        
        Query qry = parser.parse(qry_str);

        return qry;
    }
    Query build_lucene_qry( String qry_str, Analyzer ana ) throws ParseException
    {
        QueryParser parser = new QueryParser("FLDN_BODY", ana);
        parser.setAllowLeadingWildcard(true);

        if (qry_str.length() == 0)
        {
            return new MatchAllDocsQuery();
        }

        Query qry = parser.parse(qry_str);

        return qry;
    }

    ArrayList<String> get_mail_addresses( Document doc )
    {
            // BUILD MAILADRESSLIST
        ArrayList<String> mail_fields = m_ctx.get_index_manager().get_email_headers();
        ArrayList<String> mail_addr_list = new ArrayList<String>();
        for (int m = 0; m < mail_fields.size(); m++)
        {
            String field_name = mail_fields.get(m);
            String mail_address = doc.get(field_name);

            if (mail_address != null && mail_address.trim().length() > 0)
            {
                int br1 = mail_address.indexOf('<');
                int br2 = mail_address.indexOf('>');
                if (br1 >= 0 && br2 > br1)
                {
                    mail_address = mail_address.substring(br1 + 1, br2);
                }

                mail_addr_list.add( mail_address);
            }
        }
        return mail_addr_list;
    }


    void run_lucene_searcher( ArrayList<DiskSpaceHandler> dsh_list, Query qry, Filter filter, int n, USERMODE level, UserSSOEntry ssoc  ) throws IOException
    {
        // RESET LISTS
        result = new ArrayList<SearchResult>();
        searcher_list = new ArrayList<IndexSearcher>();

        boolean view_all = can_view_all_mails(ssoc, level);

        long start_time = System.currentTimeMillis();

        // FIRST PASS, OPEN INDEX READERS
        for (int i = 0; i < dsh_list.size(); i++)
        {
            DiskSpaceHandler dsh = dsh_list.get(i);
            if (dsh.islock_for_rebuild())
            {
                LogManager.msg_index(LogManager.LVL_DEBUG, "Skipping index " + dsh.getDs().getPath() + " during rebuild");
                continue;
            }
            IndexReader reader = null;
            try
            {

                reader = dsh.create_read_index();
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher_list.add(searcher);
            }
            catch (VaultException vaultException)
            {
                LogManager.msg_index(LogManager.LVL_ERR, "Cannot open index " + dsh.getDs().getPath(), vaultException);
            }
            catch (IndexException exc)
            {
                LogManager.msg_index(LogManager.LVL_ERR, "Cannot read index " + dsh.getDs().getPath(), exc);
            }
        }

        // BUILD SEARCHABLE ARRAY
        Searchable[] search_arr = new Searchable[dsh_list.size()];
        for (int i = 0; i < searcher_list.size(); i++)
        {
            search_arr[i] = searcher_list.get(i);
        }

        
        HexLongComparator hlc = new HexLongComparator();
        Sort sort = new Sort(new SortField(CS_Constants.FLD_DATE, hlc));



        // PARALLEL SEARCH
        ParallelMultiSearcher pms = new ParallelMultiSearcher(search_arr);

        // SSSSEEEEAAAARRRRCHHHHHHH


        TopDocs tdocs = pms.search(qry, filter, n, sort);


        long diff = System.currentTimeMillis() - start_time;

        LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_INDEX, "Search took " + diff + "ms");
        
        ScoreDoc[] sdocs = tdocs.scoreDocs;
        for (int k = sdocs.length - 1; k >= 0; k--)
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

            // IF WE ARE LOOKING INTO OTHER USERS MAIL, WE HAVE TO CHECK FOR 4-EYES-ROLE
            Role role = null;
            if (view_all)
            {
                // GET MAILADRESSLIST
                ArrayList<String> mail_addr_list = get_mail_addresses( doc );

                // DETECT ROLE WITH 4 EYES
                role = check_for_4eyes( mail_addr_list, ssoc );
            }

            SearchResult rs = new SearchResult(pms, doc_idx, score, da_id, ds_id, uuid, time, size, subject, has_attachment, role);
            result.add(rs);

            // STOP AFTER N RESULTS
            if (result.size() >= n)
            {
                break;
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

    private String open_RMX_mail_stream( UserSSOEntry ssoc, SearchResult result )
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
            RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode(), dsh.get_fmode());
            InputStream is = mail.open_inputstream();
            String ret = m_ctx.get_tcp_call_connect().RMX_OpenInStream( ssoc, is, result.getSize());
            return ret;
        }
        catch (IOException ex)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot open mail stream", ex);
        }
        catch (VaultException ex)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot open mail stream", ex);
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

            RFCGenericMail mail = dsh.get_mail_from_time(time, dsh.get_enc_mode(), dsh.get_fmode());


            return mail;
        }
        catch (VaultException ex)
        {
            LogManager.msg_index(LogManager.LVL_ERR, "Cannot open mail stream", ex);
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

    class _4EyesCacheEntry
    {
        public Role role;
        public Boolean _4_eyes;

        _4EyesCacheEntry()
        {
            _4_eyes = new Boolean(false);
        }
    }
    HashMap<String,_4EyesCacheEntry> _4eyes_cache = new HashMap<String, _4EyesCacheEntry>();
    private static boolean  use_4e_cache = false;

    private Role check_for_4eyes( ArrayList<String> mail_addr_list, UserSSOEntry ssoc )
    {
        // IF WE FIND AN EMAIL FROM THE CURRENT USER IN THE MAIL, WE ARE ALLOWED TO SEE IT ALWAYS
        ArrayList<String> user_mail_addr_list = ssoc.getMail_list();
        if (user_mail_addr_list != null)
        {
            for (int i = 0; i < mail_addr_list.size(); i++)
            {
                String addr = mail_addr_list.get(i);

                for (int j = 0; j < user_mail_addr_list.size(); j++)
                {
                    // FOUND MAIL ADDRESS IN USER ADRESSLIST -> WE ARE ALLOWED TO VIEW W/O 4-E-ROLE-LOGIN
                    String user_addr = user_mail_addr_list.get(j);
                    if (user_addr.compareToIgnoreCase(addr) == 0)
                    {
                        return null;
                    }
                }
            }
        }

        // NOW CHECK THE ROLES FOR A 4-EYES ROLE
        for (int i = 0; i < mail_addr_list.size(); i++)
        {
            String addr = mail_addr_list.get(i);

            _4EyesCacheEntry entry;
            // LOOK IN CACHE FIRST
            if (use_4e_cache)
            {
                entry = _4eyes_cache.get(addr);
                if (entry != null)
                {
                    if (entry._4_eyes.booleanValue())
                        return entry.role;

                    return null;
                }
            }

            entry = new _4EyesCacheEntry();


            // PRUEFE FÜR ALLE ROLLEN DIESES MANDANTEN
            for (Iterator<Role> it = m_ctx.getMandant().getRoles().iterator(); it.hasNext();)
            {
                Role role = it.next();

                if (is_member_of( role, mail_addr_list ))
                {
                    entry.role = role;
                    if (m_ctx.role_has_option(role, OptCBEntry._4EYES))
                    {
                        // STOP ON FIRST 4E-Role
                        entry._4_eyes = new Boolean( true );
                        break;
                    }

                }
            }
            if (use_4e_cache)
            {
                _4eyes_cache.put(addr, entry);
            }
            if (entry._4_eyes.booleanValue())
                return entry.role;
        }
        
        return null;
    }

    private boolean is_member_of( Role role, ArrayList<String> mail_list )
    {
        // CREATE FILTER VALUE PROVIDER
        SCFilterProvider f_provider = new SCFilterProvider(mail_list );

        // GET FILTER STR AND PARSE TO ARRAYLIST
        String compressed_list_str = role.getAccountmatch();
        ArrayList<LogicEntry> logic_list = FilterMatcher.get_filter_list( compressed_list_str, true );
        if (logic_list == null)
        {
            LogManager.msg_index(LogManager.LVL_ERR, Main.Txt("Invalid_role_filter"));
            return false;
        }

        // CREATE FILTER AND EVAL FINALLY
        FilterMatcher matcher = new FilterMatcher( logic_list , f_provider);
        boolean ret = matcher.eval();

        return ret;
    }

}
class SCFilterProvider implements FilterValProvider
{
    ArrayList<String> mail_list;

    SCFilterProvider( ArrayList<String> mail_list )
    {
        this.mail_list = mail_list;
    }

    @Override
    public ArrayList<String> get_val_vor_name( String name )
    {
        ArrayList<String> list = null;
        if (name.toLowerCase().compareTo("email") == 0)
        {
            list = mail_list;
        }
        if (name.toLowerCase().compareTo("domain") == 0)
        {
            list = new ArrayList<String>();
            for (int i = 0; i < mail_list.size(); i++)
            {
                String mail = mail_list.get(i);
                int idx = mail.indexOf('@');
                if (idx > 0 && idx < mail.length() - 1)
                    list.add(mail.substring(idx + 1));
            }
        }
        return list;
    }

}
