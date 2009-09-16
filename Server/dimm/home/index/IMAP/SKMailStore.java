/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.store.FileMailStore;
import SK.gnome.dwarf.mail.store.MailFolder;
import SK.gnome.dwarf.mail.store.event.MailEventListener;
import java.io.File;
import java.util.ArrayList;

/**
 *
 * @author mw
 */
//public class SKMailStore extends GenericFileStore implements SK.gnome.dwarf.mail.store.MailStore
public class SKMailStore extends FileMailStore
{

    ArrayList<MailEventListener> event_list;
    File start_dir;
    

    File get_start_dir()
    {
        return start_dir;
    }

    public SKMailStore(String name)
    {
        super(name);
        setBackupObjects( false);
        setLetterSubdirs( false );
        setAutoCreate( true );
        
        event_list = new ArrayList<MailEventListener>();
        start_dir = new File("mailroot");
        if (!start_dir.exists())
        {
            start_dir.mkdirs();
        }
        setUserBaseDir( start_dir );

        
    }

    @Override
    public void create( String arg0 ) throws MailException
    {
        File user_dir = new File(start_dir, arg0);
        if (!user_dir.exists())
        {
            user_dir.mkdirs();
        }
    }

    @Override
    public boolean exists( String arg0 ) throws MailException
    {
        File user_dir = new File(start_dir, arg0);
        return user_dir.exists();
    }

    @Override
    public void remove( String arg0 ) throws MailException
    {
        File user_dir = new File(start_dir, arg0);
        user_dir.delete();
    }

    @Override
    public MailFolder getDefaultFolder( String arg0 ) throws MailException
    {
        //MailFolder f = super.getDefaultFolder(arg0);
        //return f;
        return new SKMailFolder(arg0, this, null, ".",  -1);
    }

    @Override
    public String[] listUsers() throws MailException
    {
        return start_dir.list();
    }

    @Override
    public void addMailEventListener( MailEventListener arg0 )
    {
        event_list.add(arg0);
    }

    @Override
    public void removeMailEventListener( MailEventListener arg0 )
    {
        event_list.remove(arg0);
    }

 /*   @Override
    public void dispatchMailEvent( MailEvent arg0 )
    {
        for (int i = 0; i < event_list.size(); i++)
        {
            MailEventListener mailEventListener = event_list.get(i);
            try
            {
                mailEventListener.handleMailEvent(arg0);
            }
            catch (MailException mailException)
            {
                mailException.printStackTrace();

            }
        }
    }*/


}

