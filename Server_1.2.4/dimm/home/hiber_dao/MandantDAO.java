/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.hiber_dao;

import home.shared.hibernate.Mandant;
//import org.hibernate.Query;

/**
 *
 * @author mw
 */
public class MandantDAO extends GenericDAO {

    public MandantDAO() {
        super(Mandant.class);
    }
/*
    public Mandant authenticate(String username, String password) {
        Query query = session.createQuery("from User where username = ? and password = ?");
        
        query.setString(0, username);
        query.setString(1, password);

        return (User) query.uniqueResult();
    }*/
}