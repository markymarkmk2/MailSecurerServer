/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.store.MailFolder;
import SK.gnome.dwarf.mail.store.MailMessage;
import SK.gnome.dwarf.mail.store.MailStore;
import SK.gnome.dwarf.mail.store.event.FolderEvent;
import SK.gnome.dwarf.mail.store.event.MailEvent;
import SK.gnome.dwarf.mail.store.search.SearchKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;

/**
 *
 * @author mw
 */
public class SKMailFolder extends MailFolder
{

    private boolean is_open;
    String name;
    int uid = 1;
    int uid_validity = 3;

    public SKMailFolder( String user, MailStore store, MailFolder parent, String name, int uid )
    {
        super(user, store, parent);
        this.name = name;

        this.uid = uid;

        search_messagges = new SKMailMessage[0];
    }

    @Override
    public String getName()
    {
        return getFullName();
    }

    @Override
    public String getFullName()
    {
        if (parent != null && parent.getFullName() != null && parent.getFullName().length() > 1)
        {
            return parent.getFullName() + getSeparator() + name;
        }
        else
        {
            return name;
        }
    }

    @Override
    public char getSeparator()
    {
        return '.';
    }

    @Override
    public boolean exists()
    {
        /* if (subfolders == null)
        return false;*/
        return true;
    }
    
    @Override
    public int getMessageCount() throws MailException
    {
        MailMessage[] ms = getMessages();
        return ms.length;
    }
    
    @Override
    public int getFirstUnseen() throws MailException
    {
        int fus =  super.getFirstUnseen();
        return fus;
    }
     

    @Override
    public void create() throws MailException
    {
        System.out.println("Creating box " + getName());
        subfolders = new MailFolder[0];
    }

    @Override
    public long getNextUID() throws MailException
    {
        MailMessage[] ms = getMessages();
        if (ms.length > 0)
        {
            return ms[0].getUID();
        }

        return 0;
    }

    @Override
    public long getUIDValidity() throws MailException
    {
        // CACHE BEI VERÄNDERUNG
        return IMAPBrowser.get_folder_validity(this);
        //return uid_validity;
    }

    @Override
    public void open( int arg0 ) throws MailException
    {
        is_open = true;
    }

    @Override
    public int getMode() throws MailException
    {
        return READ_ONLY;
    }

    @Override
    public boolean isOpen() throws MailException
    {
        return is_open;
    }

    @Override
    public void renameTo( String arg0 ) throws MailException
    {
        throw new MailException("Not supported yet.");
    }

    @Override
    public void delete() throws MailException
    {
        throw new MailException("Not supported yet.");
    }

    @Override
    public void close( boolean arg0 ) throws MailException
    {
        if (is_open)
        {
            is_open = false;
        }
        else
        {
            throw new MailException("not open");
        }
    }

    @Override
    public MailMessage[] search( SearchKey key ) throws MailException
    {
        if (this.getName().equals("Query"))
        {
            MailMessage[] list = new MailMessage[3];
            list[0] = new SKMailMessage(this, "test0.eml", 2000);
            list[1] = new SKMailMessage(this, "test1.eml", 2000);
            list[2] = new SKMailMessage(this, "test2.eml", 2000);

            search_messagges = list;
            uid_validity++;
        }
        MailMessage[] retarr;
        boolean b = false;
        if (b)
        {
            ArrayList<MailMessage> ret = new ArrayList<MailMessage>();
            MailFolder[] mf = build_folder_list("*");
            for (int i = 0; i < mf.length; i++)
            {
                MailFolder mailFolder = mf[i];
                MailMessage[] msgl = mailFolder.getMessages();
                for (int j = 0; j < msgl.length; j++)
                {
                    SKMailMessage msg = (SKMailMessage) msgl[j];
                    if (key.match(msg))
                    {
                        ret.add(msg);
                    }

                }

            }
            retarr = ret.toArray(new MailMessage[0]);
        }
        else
        {
            retarr = search_messagges; //super.search(key);
        }
        return retarr;
    }
    MailMessage[] mess;
    MailMessage[] search_messagges;

    @Override
    public MailMessage[] getMessages() throws MailException
    {
        if (search_messagges != null)
            return search_messagges;

        if (mess != null)
        {
            return mess;
        }

        if (get_level() == 5 || getName().equals("Query"))
        {
            MailMessage[] list = new MailMessage[1];
            list[0] = new SKMailMessage(this, 12345);

            mess = list;
            return list;
        }
        mess = new SKMailMessage[0];
        return mess;
    }

    @Override
    public MailMessage[] getNewMessages() throws MailException
    {
        return search_messagges;

    }

    @Override
    public void copyMessage( MailMessage arg0 ) throws IOException, MailException
    {
        throw new MailException("Not supported yet.");
    }

    @Override
    public void addMessage( long arg0, Set arg1, InputStream arg2 ) throws IOException, MailException
    {
        throw new MailException("Not supported yet.");
    }

    @Override
    public MailMessage[] expunge() throws MailException
    {
        throw new MailException("Not supported yet.");
    }

    @Override
    public MailFolder getFolder( String arg0 ) throws MailException
    {
        System.out.println("getFolder: " + arg0);
        MailFolder[] mf = build_folder_list(arg0);

        for (int i = 0; i < mf.length; i++)
        {
            MailFolder mailFolder = mf[i];
            if (mailFolder.getName().equals(arg0))
            {
                dispatchMailEvent(new FolderEvent(mailFolder, "joe", FolderEvent.OPENED));
                return mailFolder;
            }
        }

        return null;

    }

    private int get_level()
    {
        int level = 1;

        MailFolder p = parent;

        while (p != null)
        {
            level++;
            p = p.getParent();
        }
        return level;
    }
    MailFolder[] subfolders = null;

    MailFolder[] build_folder_list( String arg )
    {
        int level = get_level();

        if (subfolders != null)
        {
            return subfolders;
        }

        MailFolder[] ret = null;


        /*      if (arg0.indexOf(getSeparator()) >= 0)
        level++;

        if (level == 1)
        {
        ret = new MailFolder[2];
        ret[0] = new SKMailFolder(user, getMailStore(), this,  "INBOX", path );
        ret[1] = new SKMailFolder(user, getMailStore(), this, "Trash", path );
        }
         *
         */
        /*      if (level == 2)
        {
        try
        {
        return parent.listFolders(arg);
        }
        catch (MailException ex)
        {
        return new MailFolder[0];
        }
        }
         */
        if (level != 1)
        {
            return new MailFolder[0];
//            return subfolders;
        }
        try
        {
            int years = 2;
            int months = 12;
            int days = 31;


            ret = new MailFolder[years * months * days + years * months + years + 3];
            ret[0] = new SKMailFolder(user, getMailStore(), this, "INBOX", 0);
            ret[1] = new SKMailFolder(user, getMailStore(), this, "Trash", 1);
            ret[2] = new SKMailFolder(user, getMailStore(), this, "Query", 2);
            int idx = 3;
            for (int y = 0; y < years; y++)
            {
                SKMailFolder ymf = new SKMailFolder(user, getMailStore(), ret[0], Integer.toString(2006 + y), idx);
                ret[idx++] = ymf;
                for (int m = 0; m < months; m++)
                {
                    String mtxt;
                    if (m < 9)
                    {
                        mtxt = "0" + Integer.toString(m + 1);
                    }
                    else
                    {
                        mtxt = Integer.toString(m + 1);
                    }
                    SKMailFolder mmf = new SKMailFolder(user, getMailStore(), ymf, mtxt, idx);
                    ret[idx++] = mmf;

                    for (int d = 0; d < days; d++)
                    {
                        String dtxt;
                        if (d < 9)
                        {
                            dtxt = "0" + Integer.toString(d + 1);
                        }
                        else
                        {
                            dtxt = Integer.toString(d + 1);
                        }
                        SKMailFolder dmf = new SKMailFolder(user, getMailStore(), mmf, dtxt, idx);
                        ret[idx++] = dmf;
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        subfolders = ret;
        return ret;

    }

    @Override
    public MailFolder[] listFolders( String arg0 ) throws MailException
    {
        System.out.println("listFolders: " + arg0);
        return build_folder_list(arg0);
    }

    @Override
    public void handleMailEvent( MailEvent arg0 ) throws MailException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
