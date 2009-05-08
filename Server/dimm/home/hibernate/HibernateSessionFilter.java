/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.hibernate;

/**
 *
 * @author mw
 */

import java.io.*;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.hibernate.SessionFactory;
import org.hibernate.Session;

public class HibernateSessionFilter implements Filter {

    private FilterConfig filterConfig = null;

    public HibernateSessionFilter() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
        SessionFactory factory = HibernateUtil.getSessionFactory();
        Session session = factory.getCurrentSession();

        try {
            session.beginTransaction();
            chain.doFilter(request, response);
            session.getTransaction().commit();

        } catch (Throwable e) {
            if (session.getTransaction().isActive()) {
                session.getTransaction().rollback();
            }

            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
        if (filterConfig == null) {
            filterConfig.getServletContext().log("HibernateSessionFilter: Initializing filter failed");
        }
    }
}
