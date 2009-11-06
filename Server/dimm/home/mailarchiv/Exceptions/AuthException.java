/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Exceptions;

/**
 *
 * @author mw
 */
public class AuthException extends Exception
{
    public AuthException( String txt )
    {
        super(txt);
    }
    public AuthException( String txt,  Exception exc )
    {
        super(txt + ": " + exc.getMessage());
    }

}
