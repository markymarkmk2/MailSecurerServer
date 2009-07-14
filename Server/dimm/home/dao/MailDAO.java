/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.Mail;

/**
 *
 * @author mw
 */
public class MailDAO extends GenericDAO
{
    public MailDAO()
    {
        super (Mail.class);
    }
}
