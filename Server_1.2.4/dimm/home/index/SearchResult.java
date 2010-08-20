/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import home.shared.hibernate.Role;
import java.io.IOException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.Searchable;
import org.apache.lucene.search.Searcher;

/**
 *
 * @author mw
 */
public class SearchResult
{
    private Searcher searcher;
    private int doc_index;
    private float score;
    private int da_id;
    private int ds_id;
    private String uuid;
    private long time;
    private long size;
    private String subject;
    private boolean has_attachment;
    private Role role_4eyes;


    public SearchResult( Searcher searcher, int doc_index, float score, int da_id, int ds_id, String uuid, long time, long size, String s, boolean has_attachment, Role role_4eyes )
    {
        this.searcher = searcher;
        this.doc_index = doc_index;
        this.score = score;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.uuid = uuid;
        this.time = time;
        this.size = size;
        subject = s;
        this.has_attachment = has_attachment;
        this.role_4eyes = role_4eyes;
    }

    /**
     * @return the searcher
     */
    public Searcher getSearcher()
    {
        return searcher;
    }

    /**
     * @return the doc_index
     */
    public int getDoc_index()
    {
        return doc_index;
    }

    /**
     * @return the score
     */
    public float getScore()
    {
        return score;
    }

    /**
     * @return the da_id
     */
    public int getDa_id()
    {
        return da_id;
    }

    /**
     * @return the ds_id
     */
    public int getDs_id()
    {
        return ds_id;
    }

    /**
     * @return the uuid
     */
    public String getUuid()
    {
        return uuid;
    }

    /**
     * @return the time
     */
    public long getTime()
    {
        return time;
    }

    /**
     * @return the size
     */
    public long getSize()
    {
        return size;
    }

    /**
     * @return the subject
     */
    public String getSubject()
    {
        return subject;
    }

    /**
     * @return the has_attachment
     */
    public boolean isHas_attachment()
    {
        return has_attachment;
    }
    public void close()
    {

        try
        {
            if (searcher != null)
            {
                if (searcher instanceof MultiSearcher)
                {
                    MultiSearcher pms = (MultiSearcher) searcher;
                    Searchable[] searchers = pms.getSearchables();
                    for (int i = 0; i < searchers.length; i++)
                    {
                        Searchable searchable = searchers[i];
                        if (searchable instanceof IndexSearcher)
                        {
                            IndexSearcher is = (IndexSearcher)searchable;
                            is.getIndexReader().close();
                        }
                        searchable.close();
                    }
                }
                searcher.close();
            }
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
        }
    }

    public Role get_role_4eyes()
    {
        return role_4eyes;
    }

}