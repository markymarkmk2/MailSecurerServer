/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.vault.DiskSpaceHandler;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;





class SearchResult
{
    int doc_index;
    int da_id;
    int ds_id;
    String uuid;
    long time;

    public SearchResult( int doc_index, int da_id, int ds_id, String uuid, long time )
    {
        this.doc_index = doc_index;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.uuid = uuid;
        this.time = time;
    }


}
/**
 *
 * @author mw
 */
public class IndexSearcher
{
    MandantContext m_ctx;
    ArrayList<SearchResult> result;

    public IndexSearcher( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
        result = new ArrayList<SearchResult>();
    }


    void search( String fld, String val )
    {
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
                    if (dsh.is_index())
                    {
                        try
                        {
                            IndexReader reader = dsh.open_read_index();

                            FilterIndexReader fir = new FilterIndexReader( reader );

                            try
                            {
                                Term term = new Term( fld, val );
                                TermDocs td = fir.termDocs(term);

                                while (td.next())
                                {
                                    int doc_idx = td.doc();
                                    Document doc = fir.document(doc_idx);

                                    int da_id = 0;
                                    int ds_id = 0;
                                    String uuid = doc.get(IndexManager.FLD_UID_NAME);
                                    long time = 0; // HEX!!!!!

                          /*              public static final String FLD_MA = "FLDN_MA";
    public static final String FLD_DA = "FLDN_DA";
    public static final String FLD_DS = "FLDN_DS";
    public static final String FLD_TM = "FLDN_TM";*/

                                    SearchResult rs = new SearchResult(doc_idx, da_id, ds_id, uuid, time );

                                }
                            }
                            catch (IOException iOException)
                            {
                            }
                        }
                        catch (VaultException vaultException)
                        {
                        }
                    }
                }
            }
        }
    }


}
