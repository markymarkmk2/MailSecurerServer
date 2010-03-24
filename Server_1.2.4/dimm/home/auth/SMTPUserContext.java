/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.auth;

import com.sun.mail.smtp.SMTPTransport;

/**
 *
 * @author mw
 */
public class SMTPUserContext
{

    public SMTPUserContext( SMTPTransport transport )
    {
        this.transport = transport;
    }

    public SMTPTransport get_transport()
    {
        return transport;
    }

    SMTPTransport transport;
}
