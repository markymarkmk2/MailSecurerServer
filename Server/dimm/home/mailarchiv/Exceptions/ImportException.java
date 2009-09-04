/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Exceptions;

/**
 *
 * @author mw
 */
public class ImportException extends Exception {

    /**
     * Creates a new instance of <code>ImportException</code> without detail message.
     */
    public ImportException() {
    }


    /**
     * Constructs an instance of <code>ImportException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public ImportException(String msg)
    {
        super(msg);
    }
}
