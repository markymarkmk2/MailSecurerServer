/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

import home.shared.hibernate.Role;

/**
 *
 * @author mw
 */
public class RoleDAO extends GenericDAO
{
    public RoleDAO()
    {
        super (Role.class);
    }
}
