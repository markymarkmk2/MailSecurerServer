/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Exceptions;

/**
 *
 * @author mw
 */
public class ExtractionException extends Exception {

    /**
     * Creates a new instance of <code>ExtractionException</code> without detail message.
     */
    public ExtractionException() {
    }


    /**
     * Constructs an instance of <code>ExtractionException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public ExtractionException(String msg)
    {
        super(msg);
    }
    public ExtractionException(String msg, Exception ex)
    {
        super(msg);
        //ex.printStackTrace();
    }
}
