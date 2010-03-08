/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.dao;

import dimm.home.DAO.GenericDAO;
import home.shared.hibernate.Backup;


/**
 *
 * @author mw
 */
public class BackupDAO extends GenericDAO
{
    public BackupDAO()
    {
        super (Backup.class);
    }
}
