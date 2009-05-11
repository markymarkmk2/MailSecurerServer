/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mail.RFCMail;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
public interface ImportMail
{

    ArrayList<RFCMail> get_mail();
    boolean exists_mails();
    void notify_control();

}
