/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Exceptions;

/**
 *
 * @author mw
 */
public class ArchiveMsgException extends Exception {

    /**
     * Creates a new instance of <code>NewException</code> without detail message.
     */
    public ArchiveMsgException() {
    }


    /**
     * Constructs an instance of <code>NewException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public ArchiveMsgException(String msg) {
        super(msg);
    }
}
