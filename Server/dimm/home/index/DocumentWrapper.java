/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import org.apache.lucene.document.Document;

/**
 *
 * @author mw
 */
public class DocumentWrapper
{
     Document doc;

    public DocumentWrapper( Document doc )
    {
        this.doc = doc;
    }
    public String get( String fld )
    {
        return doc.get(fld);
    }


}