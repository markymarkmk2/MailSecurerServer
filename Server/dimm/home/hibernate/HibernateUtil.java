/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.hibernate;

import dimm.home.mailarchiv.Main;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;

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
            if (Main.get_debug_lvl() > 5)
            {
                conf = conf.setProperty(Environment.SHOW_SQL, "true");
            }

            Logger logger = Logger.getLogger("org.hibernate.SQL");
            logger.removeAllAppenders();

            PatternLayout layout = new PatternLayout("%-5p: %d{dd.MM.yyyy HH:mm:ss,SSS}: %m%n");
            FileAppender fileAppender = new FileAppender( layout, Main.LOG_PATH + Main.LOG_SQL, true );            
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
}
