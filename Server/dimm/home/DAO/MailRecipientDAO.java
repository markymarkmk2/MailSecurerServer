/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import dimm.home.hibernate.MailRecipient;

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
