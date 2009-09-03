/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Exceptions;

import home.shared.hibernate.DiskSpace;
import dimm.home.mailarchiv.Utilities.LogManager;

/**
 *
 * @author mw
 */
public class VaultException extends Exception {

    /**
     * Creates a new instance of <code>VaultException</code> without detail message.
     */
    public VaultException()
    {
    }


    /**
     * Constructs an instance of <code>VaultException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public VaultException(String msg)
    {
        super(msg);
    }
    public VaultException(String msg, Exception exc)
    {
        super(msg);
        exc.printStackTrace();
        LogManager.err_log_fatal( "VaultException: " + msg + ": " + exc.getMessage());
    }
    public VaultException(DiskSpace ds, String msg )
    {
        super("DiskSpace <" + ds.getPath() + ">: " + msg);
        LogManager.err_log_fatal( "VaultException: " + this.getMessage());

    }
}
