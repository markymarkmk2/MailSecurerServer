package dimm.home.Httpd;

import dimm.home.Test.TestString;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;

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
}