/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.importmail;

import com.sun.mail.imap.IMAPFolder;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import java.util.*;


import dimm.home.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.net.UnknownHostException;
import javax.mail.AuthenticationFailedException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.FolderNotFoundException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;





public class MailBoxImporter implements StatusHandler, WorkerParentChild
{

    public static final int WAIT_PERIOD_S = 60;
    public static final int IMAP_IDLE_PERIOD = 10;

    public static final int CONN_MODE_MASK = 0x000f;
    public static final int CONN_MODE_INSECURE = 0x0001;
    public static final int CONN_MODE_FALLBACK = 0x0002;
    public static final int CONN_MODE_TLS = 0x0003;
    public static final int CONN_MODE_SSL = 0x0004;
    public static final int FLAG_MASK = 0xfff0;
    public static final int FLAG_AUTH_CERT = 0x0010;
    public static final int FLAG_IMAP = 0x0020;
    public static final int FLAG_IMAP_IDLE = 0x0040;


    private final String DEFAULT_SSL_FACTORY = "dimm.home.auth.DefaultSSLSocketFactory";
    private final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

    private int conn_mode;
    private int flags;
    String ipAddress = "";
    Store store;
    Folder inboxFolder;


    ImapFetcher imfetcher;
    String get_mailbox_protokoll()
    {
        if ((flags & FLAG_IMAP) == FLAG_IMAP)
        {
            return "imap";
        }
        else
        {
            return "pop3";
        }
    }

    public MailBoxImporter( ImapFetcher _imfetcher )
    {
        imfetcher = _imfetcher;
        conn_mode = imfetcher.getFlags() & CONN_MODE_MASK;
        flags = imfetcher.getFlags() & FLAG_MASK;
    }


    public String get_ssl_socket_classname( int flags )
    {
        if ((flags & FLAG_AUTH_CERT) == FLAG_AUTH_CERT)
        {
            return SSL_FACTORY;
        }
        else
        {
            return DEFAULT_SSL_FACTORY;
        }
    }

    void connect() throws Exception
    {

        Properties props = new Properties();


        String server = imfetcher.getServer();
        String username = imfetcher.getUsername();
        String password = imfetcher.getPassword();
        int port = imfetcher.getPort();
        String protocol = get_mailbox_protokoll();

        if (conn_mode == CONN_MODE_INSECURE)
        {
        }
        else if (conn_mode == CONN_MODE_FALLBACK)
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "true");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(flags));
        }
        else if (conn_mode == CONN_MODE_TLS)
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
            props.put("mail." + protocol + ".startTLS.socketFactory.class", get_ssl_socket_classname(flags));
        }
        else if (conn_mode == CONN_MODE_SSL)
        {
            protocol = protocol + "s";            
            props.put("mail." + protocol + ".socketFactory.class", get_ssl_socket_classname(flags));
            props.put("mail." + protocol + ".socketFactory.port", port);
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
        }

        // DEFAULTTIMOUT IS 300 S
        props.put("mail." + protocol + ".connectiontimeout", 300 * 1000);
        props.put("mail." + protocol + ".timeout", 300 * 1000);

        connect(protocol, server, port, username, password, props);
    }

    private void connect( String protocol, String server, int port, String username, String password, Properties props ) throws Exception
    {
        Session session = Session.getInstance(props, null);

        if (LogManager.get_debug_lvl() > 2)
        {
            session.setDebug(true);
        }

        try
        {
            store = session.getStore(protocol);
        }
        catch (Exception nspe)
        {
            throw new Exception("Cannot create Store for protocol " + protocol, nspe);
        }

        try
        {
            store.connect(server, port, username, password);
        }
        catch (AuthenticationFailedException e)
        {
            status.set_status(StatusEntry.ERROR, "Error while connecting to mail server <" + server + ">: Authentication failed");
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt(), e);
        }
        catch (IllegalStateException ise)
        {
            status.set_status(StatusEntry.ERROR, "Mail server <" + server + "> is connected already");
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt(), ise);
        }
        catch (MessagingException me)
        {
            if (me.getMessage().contains("sun.security.validator.ValidatorException"))
            {
                status.set_status(StatusEntry.ERROR, "TLS Server Certificate could not be validated for mail server <" + server + ">");
                LogManager.err_log(status.get_status_txt());
                throw new Exception(status.get_status_txt(), me);
            }
            else if (conn_mode == CONN_MODE_FALLBACK && me.getMessage().contains("javax.net.ssl.SSLHandshakeException"))
            {
                status.set_status(StatusEntry.ERROR, "SSL Handshake failed, retrying regular connect");
                conn_mode = CONN_MODE_INSECURE;
            }
            else
            {
                status.set_status(StatusEntry.ERROR, "Could not connect to mail server <" + server + ">: " + me.getMessage());
                LogManager.err_log(status.get_status_txt());
                throw new Exception(status.get_status_txt(), me);
            }
        }
        try
        {
            inboxFolder = store.getDefaultFolder();
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "Could not resolve default folder on mail server <" + server + ">: " + e.getMessage());
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt(), e);
        }

        if (inboxFolder == null)
        {
            status.set_status(StatusEntry.ERROR, "Missing default folder on mail server <" + server + ">");
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt());
        }

        try
        {
            inboxFolder = inboxFolder.getFolder("INBOX");
            if (inboxFolder == null)
            {
                throw new Exception("the inbox folder does not exist.");
            }
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "Missing INBOX folder on mail server <" + server + ">");
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt(), e);
        }
        try
        {
            inboxFolder.open(Folder.READ_WRITE);
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "cannot open INBOX folder on mail server <" + server + ">");
            LogManager.err_log(status.get_status_txt());
            throw new Exception(status.get_status_txt(), e);
        }
        return;
    }
    boolean do_finish;

    @Override
    public void finish()
    {
        do_finish = true;
    }

    @Override
    public void run_loop()
    {
        try
        {
            java.net.InetAddress inetAdd = java.net.InetAddress.getByName(imfetcher.getServer());
            ipAddress = inetAdd.getHostAddress();
        }
        catch (UnknownHostException uhe)
        {
            status.set_status(StatusEntry.ERROR, "Cannot resolve IP of mail server <" + imfetcher.getServer() + ">");
            LogManager.err_log(status.get_status_txt());
        }

        do_finish = false;

        while (!do_finish)
        {

            status.set_status(StatusEntry.BUSY, "Connecting mail server <" + imfetcher.getServer() + ">");
            try
            {
                connect();
            }
            catch (Exception e)
            {
                if (e.getCause() instanceof AuthenticationFailedException)
                {
                    status.set_status(StatusEntry.ERROR, "Authentication with mail server <" + imfetcher.getServer() + "> failed");
                    LogManager.err_log(status.get_status_txt());
                }
                else
                {
                    /*                    String errormessage = "";
                    if (e.getCause() != null && e.getCause().getMessage() != null)
                    {
                    errormessage = e.getCause().getMessage();
                    }
                     * */
                    status.set_status(StatusEntry.ERROR, "Connecting mail server <" + imfetcher.getServer() + "> failed: " + e.getMessage());
                    LogManager.err_log(status.get_status_txt());
                }
                try
                {
                    disconnect();
                }
                catch (Exception ed)
                {
                }
                try
                {
                    Thread.sleep(WAIT_PERIOD_S * 1000);
                }
                catch (Exception e2)
                {
                }
                continue;
            }

            status.set_status(StatusEntry.BUSY, "Connected to mail server <" + imfetcher.getServer() + ">");

            if (((flags & FLAG_IMAP) == FLAG_IMAP) && ((flags & FLAG_IMAP_IDLE) == FLAG_IMAP_IDLE))
            {
                do_imap_idle();
            }
            else
            {
                poll_messages();
            }

            try
            {
                status.set_status(StatusEntry.BUSY, "Disconnecting from mail server <" + imfetcher.getServer() + ">");
                disconnect();
            }
            catch (Exception e)
            {
                status.set_status(StatusEntry.ERROR, "Disconnecting from mail server <" + imfetcher.getServer() + "> failed: " + e.getMessage());
                LogManager.err_log(status.get_status_txt());
            }
            if (((flags & FLAG_IMAP) == FLAG_IMAP) && ((flags & FLAG_IMAP_IDLE) == FLAG_IMAP_IDLE))
            {
                try
                {
                    Thread.sleep(IMAP_IDLE_PERIOD * 1000);
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    private void disconnect() throws Exception
    {
        // TODO: WAIT UNTIL BACKGROUND TASKS OF ARCHIVING MESSAGES ARE DONE
        try
        {
            if (inboxFolder != null)
            {
                inboxFolder.close(true);
            }
        }
        catch (Exception e)
        {
        }
        try
        {
            if (store != null)
            {
                store.close();
            }
        }
        catch (Exception e)
        {
        }

        inboxFolder = null;
        store = null;
    }

    private void do_imap_idle()
    {
        inboxFolder.addMessageCountListener(new MessageCountAdapter()
        {

            @Override
            public void messagesAdded( MessageCountEvent ev )
            {
                Message[] messages = null;
                try
                {
                    if (store.isConnected())
                    {
                        messages = ev.getMessages();
                    }
                    else
                    {
                        return;
                    }

                }
                catch (IllegalStateException ise)
                {
                    status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> is not connected");
                    LogManager.err_log(status.get_status_txt(), ise);
                    return;
                }
                catch (IndexOutOfBoundsException iobe)
                {
                    status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has invalid index");
                    LogManager.err_log(status.get_status_txt(), iobe);
                    return;
                }

                if (messages.length < 1)
                {
                    return;
                }
                archive_messages(messages);
            }
        });

        poll_messages();

        while (!do_finish && inboxFolder.isOpen())
        {
            try
            {
                ((IMAPFolder) inboxFolder).idle();
            }
            catch (FolderClosedException fce)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has no open inbox folder");
                LogManager.debug_msg(status.get_status_txt(), fce);
                break;
            }
            catch (java.lang.IllegalStateException se)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has illegal state");
                LogManager.debug_msg(status.get_status_txt(), se);
                break;
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed at idle call");
                LogManager.err_log(status.get_status_txt(), me);
                break;
            }
            catch (Exception e)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has exception");
                LogManager.err_log(status.get_status_txt(), e);
                break;
            }
        }
    }
    
    String get_subject(Message message)
    {
        String s = "";
        try
        {
            s = message.getSubject();
        }
        catch (MessagingException ex)
        {
        }

        return s;
    }
    protected void archive_message( Message message ) throws ArchiveMsgException
    {
         status.set_status(StatusEntry.BUSY, "Archiving message <" + get_subject(message) + "> from Mail server <" + imfetcher.getServer() + ">");

         Main.get_control().add_new_outmail(message, imfetcher.getMandant(), imfetcher.getDiskArchive(), false);
         
         set_msg_deleted( message );


    }

    void set_msg_deleted( Message message )
    {
        try
        {
            message.setFlag(Flags.Flag.DELETED, true);

            // TODO:  DO WE NEED THIS??
            // message.saveChanges();
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "Cannot delete message <" + get_subject(message) + "> on Mail server <" + imfetcher.getServer() + ">");
            LogManager.err_log(status.get_status_txt(), e);
        }
    }

    private boolean archive_messages( Message[] messages )
    {
        if (messages == null || messages.length == 0)
        {
            return true;
        }
        
        boolean complete = true;

        for (int i = 0; i < messages.length; i++)
        {
            Message message = messages[i];

            try
            {
                archive_message(message);
            }
            catch (ArchiveMsgException ex)
            {
                status.set_status(StatusEntry.ERROR, "Archive of message <" + get_subject(message) + "> failed on Mail server <" + imfetcher.getServer() + ">");
                LogManager.err_log(status.get_status_txt(), ex);
                complete = false;
                // TODO ERROR HANDLING : QUARANTINE, REPEAT ETC ETC ETC, HOLY SHIT!!!!
            }
        }

        if (((flags & FLAG_IMAP) == FLAG_IMAP) && messages != null && messages.length > 0)
        {
            IMAPFolder f = (IMAPFolder) inboxFolder;
            try
            {
                // GET RID OF DELETED IMAPMESSAGES
                f.expunge();
            }
            catch (FolderNotFoundException folder)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has no inbox folder");
                LogManager.err_log(status.get_status_txt(), folder);
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed to expunge deleted messages");
                LogManager.err_log(status.get_status_txt(), me);
            }
        }

        return complete;
    }

    private void poll_messages()
    {
        boolean complete = false;
        do
        {
            Message[] messages = null;
            try
            {
                if (store.isConnected())
                {
                    messages = inboxFolder.getMessages();
                }
                else
                {
                    return;
                }
            }
            catch (FolderClosedException fce)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has no open inbox folder");
                LogManager.debug_msg(status.get_status_txt(), fce);
                return;
            }
            catch (FolderNotFoundException folder)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has no inbox folder");
                LogManager.err_log(status.get_status_txt(), folder);
                return;
            }
            catch (IllegalStateException ise)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has illegal state");
                LogManager.debug_msg(status.get_status_txt(), ise);
                return;
            }
            catch (IndexOutOfBoundsException iobe)
            {
                    status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has invalid index");
                    LogManager.err_log(status.get_status_txt(), iobe);
                return;
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed at get_messages call");
                LogManager.err_log(status.get_status_txt(), me);
                return;
            }

            if (messages.length < 1)
            {
                break;
            }
            complete = archive_messages(messages);
        }
        while (!complete);
    }

    @Override
    public String get_status_txt()
    {
        return status.get_status_txt();
    }

    @Override
    public int get_status_code()
    {
        return status.get_status_code();
    }

    @Override
    public void idle_check()
    {
        // NOTHING
    }


}

