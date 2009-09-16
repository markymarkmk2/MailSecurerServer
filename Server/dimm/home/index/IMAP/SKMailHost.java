/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import SK.gnome.dwarf.mail.MailHost;
import SK.gnome.dwarf.mail.store.MailStore;

/**
 *
 * @author mw
 */
public class SKMailHost extends MailHost
{
    SKMailStore ms;

    public SKMailHost(String name)
    {
        super(name);
        ms = new SKMailStore("internalstore");
       // this.hostId = "127.0.0.1";
    }


    @Override
    public MailStore getMailStore()
    {
        return ms;
    }


}
