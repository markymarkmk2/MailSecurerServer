/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.DAO;


/**
 *
 * @author mw
 */
import dimm.home.hibernate.HibernateUtil;
import org.hibernate.SessionFactory;
import org.hibernate.Session;

public abstract class GenericDAO
{

    protected Class persistedClass;
    //protected Session session;

    public GenericDAO( Class persistedClass )
    {
        //SessionFactory factory = HibernateUtil.getSessionFactory();
        //this.session = factory.getCurrentSession();
        this.persistedClass = persistedClass;
    }

    public Object findById( int id )
    {
        Session session = HibernateUtil.open_session();
        Object object = (Object) session.get(persistedClass, id);
        HibernateUtil.close_session(session);
        return object;
    }
    public static boolean save_new( org.hibernate.Session session, Object object )
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

    public boolean save_new( Object object )
    {
        Session session = HibernateUtil.open_session();
        boolean ret = save_new( session, object );
        HibernateUtil.close_session(session);
        return ret;
    }
    public static boolean update( org.hibernate.Session session, Object object )
    {
        try
        {
            session.beginTransaction();
            session.update(object);
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
    public boolean update( Object object )
    {
        Session session = HibernateUtil.open_session();
        boolean ret = update( session, object );
        HibernateUtil.close_session(session);
        return ret;
    }

    public static boolean refresh( org.hibernate.Session session, Object object )
    {
        try
        {
            session.beginTransaction();
            session.refresh(object);
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
    public boolean refresh( Object object )
    {
        Session session = HibernateUtil.open_session();
        boolean ret = refresh( session, object );
        HibernateUtil.close_session(session);
        return ret;
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
        Session session = HibernateUtil.open_session();
        boolean ret = delete( session, object );
        HibernateUtil.close_session(session);
        return ret;
    }
}