/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.importmail;

import com.sun.mail.imap.IMAPFolder;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import java.io.IOException;
import java.util.*;


import home.shared.hibernate.ImapFetcher;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.StatusEntry;
import static dimm.home.mailarchiv.StatusHandler.status;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.TimingIntervall;
import dimm.home.mailarchiv.WorkerParentChild;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.mail.RFCFileMail;
import java.net.UnknownHostException;
import java.util.ArrayList;
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





public class MailBoxFetcher extends WorkerParentChild
{

    public static final int WAIT_PERIOD_S = 60;
    public static int IMAP_IDLE_TIME_S = 10;
    public static final int MSG_BULK_SIZE = 1000;  // FETCH 1000 MSG IN A ROW, TO SPEED UP DELETION 


    ArrayList<TimingIntervall> timing_list;

    private final String DEFAULT_SSL_FACTORY = "home.shared.Utilities.DefaultSSLSocketFactory";
    private final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

    
    private int flags;
    String ipAddress = "";
    Store store;
    Folder inboxFolder;


    ImapFetcher imfetcher;

    @Override
    public String get_name()
    {
        return "MailBoxFetcher";
    }

    public ImapFetcher get_imap_fetcher()
    {
        return imfetcher;
    }

    @Override
    public int get_mandant_id()
    {
        return imfetcher.getMandant().getId();
    }


    String get_mailbox_protokoll()
    {

        if ((flags & CS_Constants.IMF_POP3) == CS_Constants.IMF_POP3)
        {
            return "pop3";
        }
        else
        {
            return "imap";
        }
    }

    public static MailBoxFetcher mailbox_fetcher_factory( ImapFetcher fetcher )
    {
        if (fetcher.getType().compareTo(CS_Constants.IFETCHER_TYPE_ENVELOPE) == 0)
            return  new ImapEnvelopeFetcher( fetcher );

        return new MailBoxFetcher( fetcher );
    }

    
    protected MailBoxFetcher( ImapFetcher _imfetcher )
    {
        imfetcher = _imfetcher;
        flags = Integer.parseInt( imfetcher.getFlags() );
    }

    void eval_imap_timing()
    {
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(imfetcher.getMandant().getId());
        String timing_string = m_ctx.getPrefs().get_prop(MandantPreferences.IMAP_TIMING);
        
        timing_list = TimingIntervall.eval_timing(timing_string, LogManager.TYP_FETCHER);
    }

  

    int get_idle_timing()
    {
        if (timing_list == null || timing_list.isEmpty())
            return IMAP_IDLE_TIME_S;

        int found_idle_time = TimingIntervall.get_idle_time( timing_list, IMAP_IDLE_TIME_S);


        return found_idle_time;
    }


    boolean is_imap_allowed()
    {

        if (timing_list == null ||  timing_list.isEmpty())
            return true;

        return TimingIntervall.is_allowed_now( timing_list);

    }

    boolean test_flag( int test_flag )
    {
        return ((flags & test_flag) == test_flag);
    }

    public String get_ssl_socket_classname( int flags )
    {
        if (test_flag(CS_Constants.IMF_HAS_TLS_CERT))
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

        
        if (test_flag(CS_Constants.IMF_USE_TLS_IF_AVAIL))
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "true");
        }
        else if (test_flag(CS_Constants.IMF_USE_TLS_FORCE))
        {
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".socketFactory.fallback", "false");
        }
        else if (test_flag(CS_Constants.IMF_USE_SSL))
        {
            protocol = protocol + "s";
            props.put("mail." + protocol + ".socketFactory.port", port);
        }
        else
        {
            // DO NOT ALLOW TLS 
            props.put("mail." + protocol + ".starttls.enable", "false");
        }

        if (test_flag(CS_Constants.IMF_HAS_TLS_CERT))
        {
            props.put("mail." + protocol + ".socketFactory.class", get_ssl_socket_classname(flags));
            String ca_cert_file = System.getProperty("javax.net.ssl.trustStore");
            props.put("javax.net.ssl.trustStore", ca_cert_file);
        }


        // DEFAULTTIMOUT IS 300 S
        // FAILS ON IMAP LOGIN
        props.put("mail." + protocol + ".connectiontimeout", 10 * 1000);
        props.put("mail." + protocol + ".timeout", 300 * 1000);

        props.put( "mail.debug", "false");
        if (LogManager.has_lvl(LogManager.TYP_FETCHER, LogManager.LVL_VERBOSE))
            props.put( "mail.debug", "true");

        connect(protocol, server, port, username, password, props);
    }

    private void connect( String protocol, String server, int port, String username, String password, Properties props ) throws Exception
    {
        Session session = Session.getDefaultInstance(props, null);

        if (LogManager.has_lvl(LogManager.TYP_FETCHER, LogManager.LVL_VERBOSE))
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
            status.set_status(StatusEntry.ERROR, "Authentication failed: " + e.getMessage());
            throw new Exception(status.get_status_txt(), e);
        }
        catch (IllegalStateException ise)
        {
            status.set_status(StatusEntry.ERROR, "Is connected already");
            throw new Exception(status.get_status_txt(), ise);
        }
        catch (MessagingException me)
        {
            status.set_status(StatusEntry.ERROR, "Connect failed for " + protocol + "://" + server + ":" + port + " Usr:" + username + " PWDHash:" + password.hashCode());
            LogManager.msg_fetcher(LogManager.LVL_DEBUG, "Store Props: " + props.toString() );
           // LogManager.err_log("SystmProps where " + System.getProperties().toString() );
            if (me.getMessage().contains("sun.security.validator.ValidatorException"))
            {
                status.set_status(StatusEntry.ERROR, "TLS Server Certificate could not be validated for mail server <" + server + ">");
                throw new Exception(status.get_status_txt(), me);
            }
            else if (test_flag(CS_Constants.ACCT_USE_TLS_IF_AVAIL) && me.getMessage().contains("javax.net.ssl.SSLHandshakeException"))
            {
                status.set_status(StatusEntry.ERROR, "SSL Handshake failed, retrying regular connect");
                flags &= ~CS_Constants.ACCT_USE_TLS_IF_AVAIL;
            }
            else
            {
                status.set_status(StatusEntry.ERROR, "Could not connect to mail server: " + me.getMessage());                
                throw new Exception(status.get_status_txt(), me);
            }
        }
        try
        {
            inboxFolder = store.getDefaultFolder();
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "Could not resolve default folder: " + e.getMessage());
            throw new Exception(status.get_status_txt(), e);
        }

        if (inboxFolder == null)
        {
            status.set_status(StatusEntry.ERROR, "Missing default folder");
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
            status.set_status(StatusEntry.ERROR, "Missing INBOX folder");
            throw new Exception(status.get_status_txt(), e);
        }
        try
        {
            inboxFolder.open(Folder.READ_WRITE);
        }
        catch (Exception e)
        {
            status.set_status(StatusEntry.ERROR, "cannot open INBOX folder: " + e.getMessage());
            throw new Exception(status.get_status_txt(), e);
        }
        return;
    }

    @Override
    public void run_loop()
    {
        started = true;
        try
        {
            java.net.InetAddress inetAdd = java.net.InetAddress.getByName(imfetcher.getServer());
            ipAddress = inetAdd.getHostAddress();
        }
        catch (UnknownHostException uhe)
        {
            status.set_status(StatusEntry.ERROR, "Cannot resolve IP of mail server <" + imfetcher.getServer() + ">");
            LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt());
        }

        do_finish = false;
        MandantContext m_ctx = Main.get_control().get_mandant_by_id(imfetcher.getMandant().getId());
        long da_id = imfetcher.getDiskArchive().getId();
        Vault vault = m_ctx.get_vault_by_da_id(da_id);

        // READ PREFERENCES
        eval_imap_timing();


        while (!do_finish)
        {
            

            if (!is_imap_allowed())
            {
                continue;
            }

            int imap_idle_time_s = get_idle_timing();


            if (!vault.has_sufficient_space() || m_ctx.no_tmp_space_left())
            {
                status.set_status(StatusEntry.ERROR, Main.Txt("Not_enough_space_in_archive_to_process") );

                sleep_seconds(imap_idle_time_s);
                continue;
            }
            if (vault.is_in_rebuild())
            {
                status.set_status(StatusEntry.SLEEPING, Main.Txt("Rebuild_is_pending") );

                sleep_seconds(imap_idle_time_s);
                continue;
            }



            status.set_status(StatusEntry.BUSY, "Connecting mail server <" + imfetcher.getServer() + ">");
            try
            {
                connect();
            }
            catch (Exception e)
            {
                if (e.getCause() instanceof AuthenticationFailedException)
                {
                    status.set_status(StatusEntry.ERROR, "Authentication with mail server <" + imfetcher.getServer() + "> failed: " + e.getMessage());
                    LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt());
                }
                else
                {
                    status.set_status(StatusEntry.ERROR, "Connecting mail server <" + imfetcher.getServer() + "> failed: " + e.getMessage());
                    LogManager.msg_fetcher(LogManager.LVL_WARN, status.get_status_txt());
                }
                try
                {
                    disconnect();
                }
                catch (Exception ed)
                {
                }

                sleep_seconds(WAIT_PERIOD_S);
               
                continue;
            }

            status.set_status(StatusEntry.BUSY, "Connected to mail server <" + imfetcher.getServer() + ">");

            if (test_flag(CS_Constants.IMF_USE_IDLE))
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
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt());
            }

            // PAUSE TODO: PUT INTO PARAMETER
            sleep_seconds(imap_idle_time_s);
            
        }
        finished = true;
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

                if (!is_imap_allowed())
                {
                    status.set_status(StatusEntry.SLEEPING, "Skipping message, fetch not allowed now");
                    LogManager.msg_fetcher(LogManager.LVL_DEBUG, status.get_status_txt());
                    return;
                }

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
                    LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), ise);
                    return;
                }
                catch (IndexOutOfBoundsException iobe)
                {
                    status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has invalid index");
                    LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), iobe);
                    return;
                }

                if (messages.length < 1)
                {
                    return;
                }
                if (messages.length > MSG_BULK_SIZE)
                {
                    Message[] tmp = new Message[MSG_BULK_SIZE];
                    System.arraycopy(messages, 0, tmp, 0, tmp.length);
                    messages = tmp;
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
                LogManager.msg_fetcher(LogManager.LVL_DEBUG, status.get_status_txt(), fce);
                break;
            }
            catch (java.lang.IllegalStateException se)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has illegal state");
                LogManager.msg_fetcher(LogManager.LVL_DEBUG, status.get_status_txt(), se);
                break;
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed at idle call");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), me);
                break;
            }
            catch (Exception e)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has exception");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), e);
                break;
            }


            if (!is_imap_allowed())
                break;
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
    protected void archive_message( Message message ) throws ArchiveMsgException, VaultException, IndexException
    {
        RFCFileMail mail = null;
        try
        {
           
            String txt = "Archiving message <" + get_subject(message) + "> from Mail server <" + imfetcher.getServer() + ">";
            LogManager.msg_fetcher(LogManager.LVL_DEBUG, txt);
                    
            status.set_status(StatusEntry.BUSY, txt);
            
            mail = Main.get_control().create_import_filemail_from_eml(imfetcher.getMandant(), message, "imf", imfetcher.getDiskArchive());

            Main.get_control().add_rfc_file_mail(mail, imfetcher.getMandant(), imfetcher.getDiskArchive(), /*bg*/ true, /*del_after*/ true);
           
            set_msg_deleted(message);
        }
        catch (ArchiveMsgException ex)
        {
            status.set_status(StatusEntry.ERROR, "Cannot archive message from <" + imfetcher.getServer() + ">");
            LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), ex);
            if (mail != null)
            {
                try
                {
                    Main.get_control().move_to_quarantine(mail, imfetcher.getMandant());
                }
                catch (IOException iOException)
                {
                    LogManager.msg_fetcher(LogManager.LVL_ERR, "Cannot move mail to quarantine", iOException);
                }
            }
        }
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
            LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), e);
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
            catch (Exception ex)
            {
                status.set_status(StatusEntry.ERROR, "Archive of message <" + get_subject(message) + "> failed on Mail server <" + imfetcher.getServer() + ">");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), ex);
                complete = false;
                // TODO ERROR HANDLING : REPEAT ETC ETC ETC, HOLY SHIT!!!!
            }
        }

        // DELETE MESSAGES FROM FOLDER WITH EXPUNGE
        if (!test_flag(CS_Constants.IMF_POP3) && messages != null && messages.length > 0)
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
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), folder);
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed to expunge deleted messages");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), me);
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
                int cnt = inboxFolder.getMessageCount();
                if (cnt > MSG_BULK_SIZE)
                {
                    messages = inboxFolder.getMessages(1, MSG_BULK_SIZE);
                }
                else
                {
                    messages = inboxFolder.getMessages();
                }
            }
            catch (FolderClosedException fce)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has no open inbox folder");
                LogManager.msg_fetcher(LogManager.LVL_DEBUG, status.get_status_txt(), fce);
                return;
            }
            catch (FolderNotFoundException folder)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has no inbox folder");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), folder);
                return;
            }
            catch (IllegalStateException ise)
            {
                status.set_status(StatusEntry.BUSY, "Mail server <" + imfetcher.getServer() + "> has illegal state");
                LogManager.msg_fetcher(LogManager.LVL_DEBUG, status.get_status_txt(), ise);
                return;
            }
            catch (IndexOutOfBoundsException iobe)
            {
                    status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> has invalid index");
                    LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), iobe);
                return;
            }
            catch (MessagingException me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> failed at get_messages call");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), me);
                return;
            }
            catch (Exception me)
            {
                status.set_status(StatusEntry.ERROR, "Mail server <" + imfetcher.getServer() + "> exception at get_messages call");
                LogManager.msg_fetcher(LogManager.LVL_ERR, status.get_status_txt(), me);
                return;
            }

            if (messages == null || messages.length < 1)
            {
                break;
            }
            
            if (messages.length > MSG_BULK_SIZE)
            {
                Message[] tmp = new Message[MSG_BULK_SIZE];
                System.arraycopy(messages, 0, tmp, 0, tmp.length);
                messages = tmp;
            }

            status.set_status(StatusEntry.BUSY, Main.Txt("Fetching_new_messages_from_server") + " " + imfetcher.getServer());
            complete = archive_messages(messages);
        }
        while (!complete && !do_finish);
    }


    @Override
    public void idle_check()
    {
        // NOTHING
    }

    @Override
    public Object get_db_object()
    {
        return imfetcher;
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }


    public static void main( String[] argv )
    {

        String a = "80(12-13) 120(16-17) whgqjhwe()()(";
        ArrayList<TimingIntervall> list = TimingIntervall.eval_timing(a, LogManager.TYP_FETCHER);

        for (int i = 0; i < list.size(); i++)
        {
            TimingIntervall timingIntervall = list.get(i);
            System.out.println("TI: " + timingIntervall.toString() + " CY: " + TimingIntervall.get_idle_time(list, 99) + " VL: " + (TimingIntervall.is_allowed_now(list)?"1":"0"));


        }


    }


}

