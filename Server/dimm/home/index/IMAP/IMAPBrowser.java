/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import SK.gnome.dwarf.ServiceException;
import SK.gnome.dwarf.config.XMLConfiguration;
import SK.gnome.dwarf.config.XMLConfigurationException;
import SK.gnome.dwarf.log.FileLogger;
import SK.gnome.dwarf.log.LogServer;
import SK.gnome.dwarf.mail.MailException;
import SK.gnome.dwarf.mail.auth.MailPermission;
import SK.gnome.dwarf.mail.mime.FileSource;
import SK.gnome.dwarf.mail.mime.MimePart;
import SK.gnome.dwarf.mail.mime.StreamMimePart;
import SK.gnome.dwarf.mail.store.ACLStore;
import SK.gnome.dwarf.mail.store.MailFlag;
import SK.gnome.dwarf.mail.store.MailFolder;
import SK.gnome.dwarf.mail.store.MailMessage;
import SK.gnome.dwarf.mail.store.MailStore;
import SK.gnome.dwarf.mail.store.event.FolderEvent;
import SK.gnome.dwarf.mail.store.event.MailEvent;
import SK.gnome.dwarf.mail.store.search.SearchKey;
import SK.gnome.dwarf.main.MainServer;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.io.InputStream;
import java.net.ServerSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class SKMailMessage extends MailMessage
{

    long uid;
    File dummy;
    static StreamMimePart mimepart;
    static int uid_offset = 0;
    static FileSource fs;

    public SKMailMessage( MailFolder f, long uid )
    {
        super(f);
        this.uid = uid + uid_offset++;
        dummy = new File("Z:\\Mailtest\\test.eml");

        if (fs == null)
        {
            try
            {
                fs = new FileSource(dummy, 1000 * 1024);
            }
            catch (IOException ex)
            {
                LogManager.err_log("Err opening FileSource", ex);
            }
        }

        try
        {
            mimepart = new StreamMimePart(fs);
        }
        catch (MailException mailException)
        {
            mailException.printStackTrace();
        }
    }

    public SKMailMessage( MailFolder f, String file, long uid )
    {
        super(f);
        this.uid = uid + uid_offset++;
        dummy = new File("Z:\\Mailtest\\" + file);

        FileSource _fs = null;
        try
        {
            _fs = new FileSource(dummy, 1000 * 1024);
        }
        catch (IOException ex)
        {
            LogManager.err_log("Err opening FileSource", ex);
        }


        try
        {
            mimepart = new StreamMimePart(_fs);

        }
        catch (Exception mailException)
        {
            mailException.printStackTrace();
        }
    }

    @Override
    public long getUID()
    {
        return uid;
    }

    @Override
    public int getNumber()
    {
        return uid_offset;

    }

    @Override
    public long getSize() throws MailException
    {

        return dummy.length();
    }

    @Override
    public long getInternalDate() throws MailException
    {
        return dummy.lastModified();
    }

    @Override
    public boolean isExpunged()
    {
        return false;
    }

    @Override
    public boolean isSet( MailFlag arg0 ) throws MailException
    {
        System.out.println("Is set " + arg0.toString());
        return false;
    }

    @Override
    public void setFlag( MailFlag arg0, boolean arg1 ) throws MailException
    {
        System.out.println("Set " + arg0.toString() + " " + (arg1 ? "true" : "false"));
    }

    @Override
    public InputStream getInputStream() throws IOException, MailException
    {
        return new FileInputStream(dummy);
    }

    @Override
    public MimePart getMimePart() throws MailException
    {
        return mimepart;
    }

    String get_subject()
    {
        try
        {
            String[] r = mimepart.getHeader("Subject");
            if (r != null && r.length > 0)
            {
                return r[0];

            }
        }
        catch (Exception mimeException)
        {
            mimeException.printStackTrace();
        }

        return "??";
    }
}

class SKMailFolder extends MailFolder
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

        new_mess = new SKMailMessage[0];
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
    /*
    @Override
    public int getMessageCount() throws MailException
    {
    if (mess == null)
    return 0;

    return mess.length;
    }*/
    /*
    @Override
    public int getFirstUnseen() throws MailException
    {
    return super.getFirstUnseen();
    }
     */

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

        return uid_validity;
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

            new_mess = list;
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
            retarr = super.search(key);
        }
        return retarr;
    }
    MailMessage[] mess;
    MailMessage[] new_mess;

    @Override
    public MailMessage[] getMessages() throws MailException
    {
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
        return new_mess;

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

/**
 *
 * @author mw
 */
public class IMAPBrowser implements WorkerParentChild
{

    ServerSocket sock;
    MandantContext m_ctx;
    String host;
    int port;
    final ArrayList<SKImapServer> srv_list;
    private boolean started;
    private boolean finished;

    public String getHost()
    {
        return host;
    }

    public MandantContext get_ctx()
    {
        return m_ctx;
    }

    public int getPort()
    {
        return port;
    }


    public IMAPBrowser( MandantContext m_ctx, String host, int port ) throws IOException
    {
        this.m_ctx = m_ctx;
        this.host = host;
        this.port = port;
        log_debug(Main.Txt("Opening_socket"));


        //   sock = new ServerSocket(port, 0, InetAddress.getByName(host));

        do_finish = false;
        srv_list = new ArrayList<SKImapServer>();

    }

    private void log_debug( String s )
    {
        LogManager.debug_msg(s);
    }

    private void log_debug( String s, Exception e )
    {
        LogManager.debug_msg(s, e);
    }
    boolean do_finish;

    @Override
    public void finish()
    {
        do_finish = true;
        try
        {
            if (sock != null)
                sock.close();
            sock = null;
            
            synchronized (srv_list)
            {
                for (int i = 0; i < srv_list.size(); i++)
                {
                    SKImapServer imapServer = srv_list.get(i);
                    imapServer.close();
                }
            }
        }
        catch (IOException ex)
        {
        }
    }

    @Override
    public void run_loop()
    {
        started = true;

        log_debug(Main.Txt("Going_to_accept"));
        //    Socket cl = sock.accept();


        try
        {
            XMLConfiguration.setVerbosity(false);

            // create new XMLConfiguration instance and read the given XML configuration data

            XMLConfiguration cfg = new XMLConfiguration("dwarf/main.xml");

            // create new MainServer instance according to the given XML configuration

            MainServer server = (MainServer) cfg.getService();
            List src = server.getServices();

 

            // initialize the server

            server.init(null);

            LogServer logServer = (LogServer)server.getService("Log Server");
            FileLogger fl = (FileLogger)logServer.getService("File Logger");
            fl.setLevels("error");

            // start the server

            server.start();

            SKImapServer is = (SKImapServer) server.getService("IMAP4 Server");
 

            try
            {
                ACLStore acl_store = is.getACLStore("joe");
                acl_store.addPermission("joe", new MailPermission("*", "lrs"));
                acl_store.addPermission("joe", new MailPermission("*.*", "lrs"));
            }
            catch (MailException mailException)
            {
                mailException.printStackTrace();
            }

        }
        catch (XMLConfigurationException xMLConfigurationException)
        {
            xMLConfigurationException.printStackTrace();
        }
        catch (ServiceException serviceException)
        {
            serviceException.printStackTrace();
        }

        /*
        MainServer mainServer = new MainServer("Main Server");
        mainServer.setLogFacility("server");

        SKImapServer srv = new SKImapServer(m_ctx, null, false);
        try
        {
        mainServer.addService(srv);
        mainServer.init(null);

        //            srv.init(null);
        //           srv.start();
        }
        catch (Exception serviceException)
        {
        serviceException.printStackTrace();
        }

        synchronized (srv_list)
        {
        srv_list.add(srv);
        }*/

        while (!do_finish)
        {
            try
            {
                LogicControl.sleep(1000);
            }
            catch (Exception e)
            {
                log_debug(Main.Txt("Unexpected_exception"), e);
            }
        }
        finished = true;
    }

    @Override
    public void idle_check()
    {
        synchronized (srv_list)
        {
            for (int i = 0; i < srv_list.size(); i++)
            {
                SKImapServer sr = srv_list.get(i);
                if (sr.isAlive())
                {
                    srv_list.remove(i);
                    i = -1;
                    continue;
                }
            }
        }
    }

    public int getInstanceCnt()
    {
        int r = 0;
        synchronized (srv_list)
        {
            r = srv_list.size();
        }
        return r;
    }

    @Override
    public boolean is_started()
    {
        return started;
    }

    @Override
    public boolean is_finished()
    {
        return finished;
    }

    @Override
    public Object get_db_object()
    {
        return m_ctx.getMandant();
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

}

