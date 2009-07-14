/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.MailRecipient;

/**
 *
 * @author mw
 */
public class MailRecipientDAO extends GenericDAO
{
    public MailRecipientDAO()
    {
        super (MailRecipient.class);
    }
}
