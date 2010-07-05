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
        LogManager.msg_vault( LogManager.LVL_ERR, "VaultException: " + msg + ": ", exc);
    }
    public VaultException(DiskSpace ds, String msg, Exception exc )
    {
        super("DiskSpace <" + ds.getPath() + ">: " + msg);
        exc.printStackTrace();
        LogManager.msg_vault( LogManager.LVL_ERR, "VaultException: " + this.getMessage() + ": ", exc);

    }
    public VaultException(DiskSpace ds, String msg)
    {
        super("DiskSpace <" + ds.getPath() + ">: " + msg);
       
        LogManager.msg_vault( LogManager.LVL_ERR, "VaultException: " + this.getMessage() + ": " );

    }
    public VaultException(DiskSpace ds, Exception exc )
    {
        super("DiskSpace <" + ds.getPath() + ">: " + exc.getMessage());
        exc.printStackTrace();
        LogManager.msg_vault( LogManager.LVL_ERR, "VaultException: " + this.getMessage() + ": ");

    }
}
