/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.Commands.AbstractCommand;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.TopDocs;





class SearchResult
{
    Searcher searcher;
    int doc_index;
    float score;
    int da_id;
    int ds_id;
    String uuid;
    long time;

    public SearchResult( Searcher searcher, int doc_index, float score, int da_id, int ds_id, String uuid, long time )
    {
        this.searcher = searcher;
        this.doc_index = doc_index;
        this.score = score;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.uuid = uuid;
        this.time = time;
    }
}



class SearchCallEntry
{
    SearchCall call;
    int id;

    public SearchCallEntry( SearchCall call, int id )
    {
        this.call = call;
        this.id = id;
    }

    String get_id()
    {
        return "sc" + id;
    }
}


class SearchCommand extends AbstractCommand
{
    SearchCommand()
    {
        super("SearchMail");
    }

    @Override
    public boolean do_command( String data )
    {
        String opt = get_opts( data );

        ParseToken pt = new ParseToken(opt);

        long m_id = pt.GetLongValue("MA:");
        String type = pt.GetString("TY:");
        long size = pt.GetLongValue("SI:");


        throw new UnsupportedOperationException("Not supported yet.");
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


    static final ArrayList<SearchCallEntry> call_list = new ArrayList<SearchCallEntry>();
    public static ArrayList<AbstractCommand> command_list = new ArrayList<AbstractCommand>();
    static
    {
        command_list.add( new SearchCommand());
    }

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

    static String open_search_call( int ma_id, String mail, String field, String val, int n )
    {
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(ma_id);
        if (m_ctx == null)
        {
            return "1: invalid mandant";
        }
        SearchCall sc = new SearchCall( m_ctx );
        sc.search(mail, field, val, n);

        int id = 0;
        synchronized ( call_list )
        {
            id = call_list.size();
            SearchCallEntry sce = new SearchCallEntry( sc, id );
            call_list.add(sce);
        }

        return "0: sc" + id + " N:" + sc.result.size();
    }
    static String close_search_call( String id )
    {
        SearchCallEntry sce = get_sce( id );
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
            Document doc = sce.call.get_doc(result.searcher, result.doc_index);

            for (int j = 0; j < field_list.size(); j++)
            {
                String field = field_list.get(j);
                String val = doc.get(field);
                row_list.add(val);
            }
            return row_list;
        }
        catch (IOException iOException)
        {
            LogManager.err_log_fatal("Cannot retrieve results from index", iOException);
        }
        return null;
    }
    
    static String retrieve_search_call( String id, ArrayList<String> field_list, int row )
    {
        SearchCallEntry sce = get_sce( id );
        if (sce == null)
        {
            return "1: invalid sce";
        }

        ArrayList<ArrayList<String>> result_list = new ArrayList<ArrayList<String>>();

        if (row == -1)
        {
            for (int i = 0; i < sce.call.result.size(); i++)
            {

                ArrayList<String> row_list = retrieve_row( sce, field_list, i );
                if (row_list != null)
                    result_list.add( row_list );
            }
        }

        XStream xstream = new XStream();
        String res = xstream.toXML(result_list);

        return "0: " + res;
    }


    public SearchCall( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
        result = new ArrayList<SearchResult>();
    }

    static DiskSpaceHandler last_dsh;
    DiskSpaceHandler get_dsh( int ds_idx )
    {
        if (last_dsh != null && last_dsh.getDs().getId() == ds_idx)
            return last_dsh;

        for (int i = 0; i < m_ctx.getVaultArray().size(); i++)
        {
            Vault vault = m_ctx.getVaultArray().get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault) vault;
                dv.get_dsh_list();
                for (int j = 0; j < dv.get_dsh_list().size(); j++)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
                    if (dsh.getDs().getId() == ds_idx)
                        return dsh;
                }
            }
        }
        return null;
    }

    Document get_doc( Searcher searcher, int doc_index ) throws CorruptIndexException, IOException
    {
        return searcher.doc(doc_index);
    }

    void search( String mail_adress, String fld, String val, int n )
    {
        // GO THROUGH ALL VAULTS
        for (int i = 0; i < m_ctx.getVaultArray().size(); i++)
        {
            Vault vault = m_ctx.getVaultArray().get(i);
            if (vault instanceof DiskVault)
            {
                // GO THROUGH ALL DISKSPACES OF EACH VAULT
                DiskVault dv = (DiskVault) vault;
                dv.get_dsh_list();
                for (int j = 0; j < dv.get_dsh_list().size(); j++)
                {
                    DiskSpaceHandler dsh = dv.get_dsh_list().get(j);
                    if (dsh.is_index())
                    {
                        // START A SERACH TODO: DO THIS IN BACKGROUND
                        try
                        {
                            IndexReader reader = dsh.open_read_index();
                            IndexSearcher searcher = new IndexSearcher( reader );

                            FilterIndexReader fir = new FilterIndexReader( reader );

                            try
                            {
                                Term term = new Term( fld, val );
                                TermQuery qry = new TermQuery( term );

                                TermsFilter filter = null;
                                
                                if (mail_adress != null)
                                {
                                    new TermsFilter();
                                    filter.addTerm(new Term( "To", mail_adress));
                                    filter.addTerm(new Term( "From", mail_adress));
                                    filter.addTerm(new Term( "CC", mail_adress));
                                }

                                Sort sort = new Sort(IndexManager.FLD_TM);

                                // SSSSEEEEAAAARRRRCHHHHHHH
                                TopDocs tdocs = searcher.search( qry, filter, n );

                                ScoreDoc[] sdocs = tdocs.scoreDocs;
                                for (int k = 0; k < sdocs.length; k++)
                                {
                                    ScoreDoc scoreDoc = sdocs[k];

                                    int doc_idx = scoreDoc.doc;
                                    float score = scoreDoc.score;

                                    Document doc = fir.document(doc_idx);

                                    int da_id = doc_get_int( doc, IndexManager.FLD_DA );
                                    int ds_id = doc_get_int( doc, IndexManager.FLD_DS );
                                    
                                    String uuid = doc.get(IndexManager.FLD_UID_NAME);
                                    long time = doc_get_hex_long( doc, IndexManager.FLD_TM ); // HEX!!!!!

                                    SearchResult rs = new SearchResult( searcher, doc_idx, score, da_id, ds_id, uuid, time );
                                    result.add(rs);
                                }
                            }
                            catch (IOException exception)
                            {
                                LogManager.err_log("Cannot read term doc list from index" , exception);
                            }
                        }
                        catch (VaultException vaultException)
                        {
                            LogManager.err_log("Cannot open index " + dsh.getDs().getPath() , vaultException);
                        }
                    }
                }
            }
        }
    }
    void close()
    {
        result.clear();
    }
    
    int _doc_get_int( Document doc, String fld ) throws Exception
    {
        String val = doc.get(fld);
        return Integer.parseInt(val);
    }
    long _doc_get_long( Document doc, String fld, int radix ) throws Exception
    {
        String val = doc.get(fld);
        return Long.parseLong(val, radix);
    }
    int doc_get_int( Document doc, String fld )
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
    long doc_get_hex_long( Document doc, String fld )
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


}
