/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import SK.gnome.dwarf.ServiceException;
import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.MailHost;
import SK.gnome.dwarf.mail.auth.MailPermission;
import SK.gnome.dwarf.mail.store.ACLStore;
import SK.gnome.dwarf.tcpip.TCPListener;

/**
 *
 * @author mw
 */
public class SKImapServer extends SK.gnome.dwarf.mail.imap.IMAPServer
{

    TCPListener listener;

    public SKImapServer(String name)
    {
        super(name);

        //hosts = new HashMap<String,MailHost>();
    /*    SKMailHost host = new SKMailHost( "host1") ;
        try
        {
            host.init(this);
            addService(host);
        }
        catch (ServiceException serviceException)
        {
            serviceException.printStackTrace();
        }*/
/*
        hosts.put( "", host );
        hosts.put( host.getHostId(), host );*/


        // TCP listener
/*
        listener = new TCPListener("IMAP Listener");
        listener.setPort(143);

        try
        {
            this.addService(listener);
        }
        catch (ServiceException serviceException)
        {
            serviceException.printStackTrace();
        }*/
    }

    void close()
    {
        try
        {
            this.stop();
        }
        catch (ServiceException ex)
        {
            ex.printStackTrace();
        }
    }

    boolean isAlive()
    {

        return true;
    }

    @Override
    public MailHost getMailHost( String id )
    {
        return super.getMailHost(id);
    }

    @Override
    public MailHost getMailHostByUser( String user ) throws MailException
    {
        return super.getMailHostByUser(user);
    }


}