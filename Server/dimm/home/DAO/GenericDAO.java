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

public abstract class GenericDAO
{

    protected Class persistedClass;
    protected Session session;

    public GenericDAO( Class persistedClass )
    {
        SessionFactory factory = HibernateUtil.getSessionFactory();
        this.session = factory.getCurrentSession();
        this.persistedClass = persistedClass;
    }

    public Object findById( int id )
    {
        Object object = (Object) session.get(persistedClass, id);
        return object;
    }

    public static boolean save( org.hibernate.Session session, Object object )
    {
        try
        {
            session.beginTransaction();
            session.save(object);
            session.getTransaction().commit();
            return true;

        }
        catch (Throwable e)
        {
            if (session.getTransaction().isActive())
            {
                session.getTransaction().rollback();
            }

            System.out.println(e.getMessage());
        }
        return false;
    }
    public boolean save( Object object )
    {
       return save( session, object );
    }

    public static boolean delete( Session session, Object object )
    {
        try
        {
            session.beginTransaction();
            session.delete(object);
            session.getTransaction().commit();
            return true;
        }
        catch (Throwable e)
        {
            if (session.getTransaction().isActive())
            {
                session.getTransaction().rollback();
            }

            System.out.println(e.getMessage());
        }
        return false;
    }
    public boolean delete( Object object )
    {
       return delete( session, object );
    }
}