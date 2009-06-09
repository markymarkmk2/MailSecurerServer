/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mail;

import java.io.File;

/**
 *
 * @author mw
 */
public class RFCFileMail
{
    File msg;

    public RFCFileMail( File _msg )
    {
        msg = _msg;
    }

    public File getFile()
    {
        return msg;
    }

}
