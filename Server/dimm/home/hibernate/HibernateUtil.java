/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.hibernate;

import dimm.home.mailarchiv.Main;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.hibernate.EntityMode;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
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

    static
    {
        try
        {
            // Create the SessionFactory from standard (hibernate.cfg.xml) 
            // config file.
            AnnotationConfiguration conf = new AnnotationConfiguration().configure("dimm/home/hibernate/hibernate.cfg.xml");

            conf = conf.setProperty(Environment.FORMAT_SQL, "false");
            // ADFUST SETTINGS
            if (Main.get_debug_lvl() > 9)
            {
                conf = conf.setProperty(Environment.SHOW_SQL, "true");
            }

            Logger logger = Logger.getLogger("org.hibernate.SQL");
            logger.removeAllAppenders();

            PatternLayout layout = new PatternLayout("%-5p: %d{dd.MM.yyyy HH:mm:ss,SSS}: %m%n");
            FileAppender fileAppender = new FileAppender(layout, Main.LOG_PATH + Main.LOG_SQL, true);
            logger.addAppender(fileAppender);

            logger = Logger.getLogger("org.hibernate.cfg.annotations.Version");
            logger.addAppender(fileAppender);

            sessionFactory = conf.buildSessionFactory();

        }
        catch (Throwable ex)
        {
            // Log the exception. 
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory()
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
}
