/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.hibernate;

import home.shared.Utilities.ParseToken;
import home.shared.Utilities.ZipUtilities;

/**
 *
 * @author mw
 */
public class HParseToken extends ParseToken
{
    public HParseToken( String _str)
    {
        super( _str );
    }

    // WRAPS THE HIBERNATE DATA STRUCTURES TO NON-HIBERNATE COMPATIBLE CLASSES
    public static String BuildCompressedString( Object o )
    {
        HXStream xs = new HXStream();
        String xml = xs.toXML(o);
        String cxml = ZipUtilities.compress(xml);
        return cxml;
    }

}
