/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mail;

import java.io.InputStream;

/**
 *
 * @author mw
 */
public class RFCMailStream
{
    InputStream mail_stream;
    String handler_source;

    public RFCMailStream(InputStream _mail_stream, String _handler_source)
    {
        mail_stream = _mail_stream;
        handler_source = _handler_source;
    }
    public InputStream getInputStream()
    {
        return mail_stream;
    }
    public String getHandlerSource()
    {
        return handler_source;
    }

}
