package dimm.home.Httpd;

import dimm.home.Test.TestString;
import dimm.home.hibernate.HibernateUtil;
import java.beans.XMLEncoder;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import org.hibernate.HibernateException;

@WebService
public class MWWebService {

    /**
     * Web service operation
     */
    @WebMethod(operationName = "Minus")
    public int Minus( @WebParam(name = "m1")
    int m1, @WebParam(name = "m2")
    int m2 )
    {
        //TODO write your implementation code here:
        return m2 - m1;
    }
    @WebMethod(operationName = "Multiply")
    public int get_malnehmen( @WebParam(name = "m1")
    int m1, @WebParam(name = "m2")
    int m2 )
    {
        //TODO write your implementation code here:
        return m2 * m1;
    }
    @WebMethod(operationName = "StringConcat")
    public String Stringconcat( @WebParam(name = "m1")
    String m1, @WebParam(name = "m2") TestString m2 )
    {
        //TODO write your implementation code here:
        return m1 + m2.getS();
    }

    @WebMethod(operationName = "getQuery")
    public String getQuery( @WebParam(name = "qry") String qry )
    {
        org.hibernate.Query q = null;
        String ret = null;

        List l = null;
        try
        {
            org.hibernate.Session session = HibernateUtil.getSessionFactory().getCurrentSession();
            org.hibernate.Transaction tx = session.beginTransaction();
            q = session.createQuery(qry);
        }
        catch (Exception ex)
        {
            String err = "createQuery <" + qry + "> failed: " + ex.getMessage();
            System.out.println(err);
            ret = "1: " + err;
            return ret;
        }

        try
        {
            l = q.list();
        }
        catch (Exception ex)
        {
            String cause = "";
            if (ex.getCause() != null)
            {
                cause = ex.getCause().getMessage();
            }
            String err = "listQuery <" + qry + "> failed: " + ex.getMessage();
            System.out.println(err);
            ret = "2: " + err;
            return ret;
        }
        
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XMLEncoder enc = new XMLEncoder(bos);
            enc.writeObject(l);
            enc.close();

            ret = "0: " + bos.toString("UTF-8");
        }
        catch (Exception ex)
        {
            String err = "writeQuery <" + qry + "> failed: " + ex.getMessage();
            System.out.println(err);
            ret = "3: " + err;
            return ret;            
        }

        
        return ret;
    }
}