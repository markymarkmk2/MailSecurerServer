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
     String uuid;

    public DocumentWrapper( Document doc, String uuid )
    {
        this.doc = doc;
        this.uuid = uuid;
    }
    public String get( String fld )
    {
        return doc.get(fld);
    }
    public String get_uuid()
    {
        return uuid;
    }


}