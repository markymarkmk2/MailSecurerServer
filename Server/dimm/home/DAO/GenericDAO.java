/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.DAO;

/**
 *
 * @author mw
 */
import dimm.home.hibernate.*;
import org.hibernate.SessionFactory;
import org.hibernate.Session;

public abstract class GenericDAO {
    protected Class persistedClass;
    protected Session session;

    public GenericDAO(Class persistedClass) {
        SessionFactory factory = HibernateUtil.getSessionFactory();
        this.session = factory.getCurrentSession();
        this.persistedClass = persistedClass;
    }

    public Object findById(int id) {
        Object object = (Object) session.get(persistedClass, id);
        return object;
    }

    public void save(Object object) {
        try {
            session.beginTransaction();
            session.save(object);
            session.getTransaction().commit();

        } catch (Throwable e) {
            if (session.getTransaction().isActive()) {
                session.getTransaction().rollback();
            }

            System.out.println(e.getMessage());
        }
    }
}