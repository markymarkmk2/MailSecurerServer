/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.DiskSpace;


/**
 *
 * @author mw
 */
public class DiskSpaceDAO extends GenericDAO
{
    public DiskSpaceDAO()
    {
        super (DiskSpace.class);
    }
}
