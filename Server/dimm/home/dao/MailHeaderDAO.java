/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.MailHeader;

/**
 *
 * @author mw
 */
public class MailHeaderDAO extends GenericDAO
{
    public MailHeaderDAO()
    {
        super (MailHeader.class);
    }
}
