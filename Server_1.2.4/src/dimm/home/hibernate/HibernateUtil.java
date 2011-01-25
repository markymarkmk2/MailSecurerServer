/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.hibernate;

import dimm.home.mailarchiv.Utilities.CompressingDailyRollingFileAppender;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.hibernate.EntityMode;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.Type;

/**
 * Hibernate Utility class with a convenient method to get Session Factory object.
 *
 * @author mw
 */
public class HibernateUtil
{

    private static final SessionFactory sessionFactory;
    //private static final Semaphore db_sema;
    private static AnnotationConfiguration conf;
    private static final ReentrantReadWriteLock rwl;


    static
    {
        try
        {
            rwl = new ReentrantReadWriteLock();
           // db_sema = new Semaphore(1);

            // Create the SessionFactory from standard (hibernate.cfg.xml) 
            // config file.
            conf = new AnnotationConfiguration().configure("dimm/home/hibernate/hibernate.cfg.xml");

            conf = conf.setProperty(Environment.FORMAT_SQL, "false");
            // ADFUST SETTINGS
            if (LogManager.has_lvl(LogManager.TYP_SYSTEM, LogManager.LVL_VERBOSE))
            {
                conf = conf.setProperty(Environment.SHOW_SQL, "true");
            }

            Logger logger = Logger.getLogger("org.hibernate.SQL");
            logger.removeAllAppenders();

            PatternLayout layout = new PatternLayout("%-5p: %d{dd.MM.yyyy HH:mm:ss,SSS}: %m%n");
            CompressingDailyRollingFileAppender fileAppender = new CompressingDailyRollingFileAppender(layout, LogManager.LOG_PATH + "sql.log", LogManager.WEEKLY_ROLL);
            fileAppender.setMaxNumberOfDays("365");
            fileAppender.setKeepClosed(true);
            logger.addAppender(fileAppender);

            logger = Logger.getLogger("org.hibernate.cfg.annotations.Version");
            logger.addAppender(fileAppender);


            sessionFactory = conf.buildSessionFactory();
            Session sess = sessionFactory.getCurrentSession();
            sess.close();



        }
        catch (Throwable ex)
        {
            // Log the exception. 
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static SessionFactory getSessionFactory()
    {
        return sessionFactory;
    }

    private static boolean isSkipClass( Class clazz )
    {
        return false;
    }

    static ArrayList<Object> stack_list;

    public static void forceLoad( Object entity ) throws HibernateException
    {
        stack_list = new ArrayList<Object>();
        _forceLoad(entity);
        stack_list.clear();
        stack_list = null;

    }

    private static void _forceLoad( Object entity ) throws HibernateException
    {
        if (entity == null)
        {
            return;
        }

        if (isSkipClass(entity.getClass()))
        {
            return;
        }


        ClassMetadata classMetadata = getSessionFactory().getClassMetadata(
                entity.getClass());

        if (classMetadata == null)
        {
            return;
        }

        if (stack_list.contains(entity))
            return;

        stack_list.add(entity);

        Hibernate.initialize(entity);

        for (int i = 0, n = classMetadata.getPropertyNames().length; i < n; i++)
        {
            String propertyName = classMetadata.getPropertyNames()[i];
            Type type = classMetadata.getPropertyType(propertyName);

            if (type.isEntityType())
            {
                Object subEntity = classMetadata.getPropertyValue(entity, propertyName, EntityMode.POJO);
                _forceLoad(subEntity);

            }
            if (type.isCollectionType())
            {
                Collection collection = (Collection) classMetadata.getPropertyValue(entity, propertyName, EntityMode.POJO);
                if (collection != null && collection.size() > 0)
                {
                    for (Object collectionItem : collection)
                    {
                        _forceLoad(collectionItem);
                    }
                }
            }
        }
        stack_list.remove(entity);

    }
    public static boolean shutdown_db()
    {
        rwl.writeLock().lock();
        try
        {
            DriverManager.getConnection("jdbc:derby:MailArchiv;shutdown=true");
        }
        catch (SQLException sQLException)
        {
        }
        return true;
    }
    public static void reopen_db()
    {
        rwl.writeLock().unlock();

        String connect_str = conf.getProperty("hibernate.connection.url");
        String user = conf.getProperty("hibernate.connection.username");
        String pwd = conf.getProperty("hibernate.connection.password");

        try
        {
            Connection conn = DriverManager.getConnection(connect_str, user, pwd);
            conn.close();
        }
        catch (SQLException sQLException)
        {
        }
    }

    public static void close_session( Session sess )
    {
        if (sess == null)
            return;
        
        Connection conn = sess.disconnect();

        try
        {
            if (conn != null)
                conn.close();
        }
        catch (SQLException sQLException)
        {
        }
        sess.close();
        rwl.readLock().unlock();

    }
    public static Session open_session(  )
    {
        rwl.readLock().lock();
        // Session session = HibernateUtil.getSessionFactory().openSession();

        String connect_str = conf.getProperty("hibernate.connection.url");
        String user = conf.getProperty("hibernate.connection.username");
        String pwd = conf.getProperty("hibernate.connection.password");

        Session sess = null;

        try
        {
            Connection conn = DriverManager.getConnection(connect_str, user, pwd);
            sess = sessionFactory.openSession(conn);

        }
        catch (SQLException sQLException)
        {
        }

        return sess;

    }

}
