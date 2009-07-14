/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.MailRecipientLink;

/**
 *
 * @author mw
 */
public class MailRecpientLinkDAO extends GenericDAO
{
    public MailRecpientLinkDAO()
    {
        super (MailRecipientLink.class);
    }
}
