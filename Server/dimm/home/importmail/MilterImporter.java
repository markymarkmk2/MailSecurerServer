/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import com.sendmail.jilter.JilterEOMActions;
import com.sendmail.jilter.JilterStatus;

import com.sendmail.jilter.JilterHandler;

import com.sendmail.jilter.JilterHandlerAdapter;
import com.sendmail.jilter.JilterProcessor;
import home.shared.hibernate.Milter;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import dimm.home.vault.Vault;
import home.shared.CS_Constants;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCMimeMail;
import java.io.File;
import java.io.IOException;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;





/**
 *
 * @author mw
 */


class JilterServerRunnable implements Runnable
{

    private SocketChannel socket = null;
    private JilterProcessor processor = null;
    private boolean is_finished;

    /**
     * Constructor.
     *
     * @param socket the incoming socket from the MTA.
     * @param handler the handler containing callbacks for the milter protocol.
     */

    public JilterServerRunnable(SocketChannel socket, JilterHandler handler)
        throws IOException
    {
        this.socket = socket;
        this.socket.configureBlocking(true);
        this.processor = new JilterProcessor(handler);
        is_finished = false;
    }

    @Override
    public void run()
    {
        ByteBuffer dataBuffer = ByteBuffer.allocateDirect(CS_Constants.STREAM_BUFFER_LEN);

        try
        {
            while (this.processor.process(this.socket, (ByteBuffer) dataBuffer.flip()))
            {
                dataBuffer.compact();
                LogManager.debug(Main.Txt("Going_to_read"));
                if (this.socket.read(dataBuffer) == -1)
                {
                    LogManager.debug(Main.Txt("socket_reports_EOF,_exiting_read_loop"));
                    break;
                }
                LogManager.debug(Main.Txt("Back_from_read"));
            }
        }
        catch (IOException e)
        {
            LogManager.debug(Main.Txt("Unexpected_exception,_connection_will_be_closed"), e);
        }
        finally
        {
            LogManager.debug(Main.Txt("Closing_processor"));
            this.processor.close();
            LogManager.debug(Main.Txt("Processor_closed"));
            try
            {
                LogManager.debug(Main.Txt("Closing_socket"));
                this.socket.close();
                LogManager.debug(Main.Txt("Socket_closed"));
            }
            catch (IOException e)
            {
                LogManager.debug(Main.Txt("Unexpected_exception"), e);
            }
        }
        is_finished = true;
    }

    boolean is_finished()
    {
        return is_finished;
    }
}
class MailImportJilterHandler extends JilterHandlerAdapter
{
    ArrayList<String> esmtp_from_args;
    ArrayList<String> esmtp_rcpt_args;
    String sender;
    ArrayList<String>rcpt_list;
    ArrayList<String>header_list;
    StringBuffer header_sb;
    String connect_host;
   

    //File tmp_file;
    OutputStream os;
    RFCFileMail file_mail;
    byte[] tmp_buffer;
    MilterImporter handler;


    public MailImportJilterHandler(MilterImporter handler)
    {
        this.handler = handler;

        initialize();
    }
    void initialize()
    {
        esmtp_from_args = new ArrayList<String>();
        esmtp_rcpt_args = new ArrayList<String>();
        rcpt_list = new ArrayList<String>();
        header_sb = new StringBuffer();
        
        sender = null;
        connect_host = "localhost";
        tmp_buffer = new byte[CS_Constants.STREAM_BUFFER_LEN];
    }

    @Override
    public int getSupportedProcesses()
    {
        return PROCESS_CONNECT | PROCESS_BODY | PROCESS_ENVFROM | PROCESS_ENVRCPT | PROCESS_HEADER;
    }

    @Override
    public JilterStatus connect( String hostname, InetAddress hostaddr, Properties properties )
    {
        if (os != null)
        {
            LogManager.log(Level.SEVERE, Main.Txt("removing_aborted_milter_import"));
            try
            {
                os.close();
                file_mail.delete();
            }
            catch (Exception iOException)
            {
            }
        }

	if (hostaddr != null)
        {
            connect_host = hostaddr.toString();
	} 
        else if (connect_host!=null)
        {
            connect_host = hostname;
	}
        esmtp_from_args.clear();
        esmtp_rcpt_args.clear();

        MandantContext m_ctx = Main.get_control().get_m_context(handler.get_milter().getMandant());

        try
        {
            Vault vault = m_ctx.get_vault_by_da_id(handler.get_milter().getDiskArchive().getId());
            
            // CHECK FOR TEMP AND VAULT SPACE
            if (!vault.has_sufficient_space() || m_ctx.no_tmp_space_left())
            {
                if (m_ctx.wait_on_no_space())
                {
                    handler.set_status( StatusEntry.WAITING, Main.Txt("No_space_left_for_mail_from_milter") );
                    LogManager.log(Level.SEVERE, handler.get_status_txt() );

                    while (!vault.has_sufficient_space() || m_ctx.no_tmp_space_left())
                    {
                        handler.sleep_seconds(10);
                        if (handler.is_finished())
                            break;
                    }
                }
            }
            
            // IF WE AT LEAST HAVE TEMP SPACE, WE ACCEPT MAIL, THIS CAN HAPPEN ON handler->is_finished
            if (!m_ctx.no_tmp_space_left())
            {
                File tmp_file = m_ctx.getTempFileHandler().create_temp_file(/*SUBDIR*/"", "dump", "tmp");
                file_mail = new  RFCFileMail(tmp_file, RFCFileMail.dflt_encoded);
                os = file_mail.open_outputstream();
            }
            else
            {
                handler.set_status( StatusEntry.ERROR, Main.Txt("No_space_left_for_mail_from_milter,_skipping_mail") );
                LogManager.log(Level.SEVERE, handler.get_status_txt() );
                return JilterStatus.SMFIS_TEMPFAIL;
            }
        }
        catch (Exception archiveMsgException)
        {
            LogManager.log(Level.SEVERE, Main.Txt("cannot_create_temp_file"), archiveMsgException);
            return JilterStatus.SMFIS_TEMPFAIL;
        }
	return JilterStatus.SMFIS_CONTINUE;
    }

    @Override
    public JilterStatus envfrom( String[] argv, Properties properties )
    {
        if (argv.length == 0)
            return super.envfrom(argv, properties);

        sender = argv[0];

        if (argv.length > 1)
        {
            for (int i = 1; i < argv.length; i++)
            {
                esmtp_from_args.add( argv[i] );
            }
        }
        return JilterStatus.SMFIS_CONTINUE;
    }

    @Override
    public JilterStatus envrcpt( String[] argv, Properties properties )
    {
        if (argv.length == 0)
            return super.envrcpt(argv, properties);

        rcpt_list.add( argv[0] );
        if (argv.length > 1)
        {
            for (int i = 1; i < argv.length; i++)
            {
                esmtp_rcpt_args.add( argv[i] );
            }
        }

        return JilterStatus.SMFIS_CONTINUE;
    }

    @Override
    public JilterStatus eom( JilterEOMActions eomActions, Properties properties )
    {
        try
        {
            if (os == null)
            {
                // TODO: ARCHIVE FAILED
                LogManager.log(Level.SEVERE, "Got out-of-bound eom, discarding mail");
                return JilterStatus.SMFIS_CONTINUE;
            }
            os.close();
            os = null;
                        
            RFCMimeMail mime_mail = new RFCMimeMail();
            mime_mail.parse(file_mail);

            add_bcc_recpients( mime_mail.getMsg() );

            // CHECK FOR SPACE AND ARCHIVE
            Milter milter = handler.get_milter();
            MandantContext m_ctx = Main.get_control().get_m_context(milter.getMandant());
            Vault vault = m_ctx.get_vault_by_da_id(milter.getDiskArchive().getId());
            if (!vault.has_sufficient_space())
            {
                LogManager.log(Level.SEVERE, Main.Txt("No_space_left_for_mail_from_milter") );
                if (m_ctx.wait_on_no_space())
                {
                    handler.set_status( StatusEntry.WAITING, Main.Txt("No_space_left_for_mail_from_milter") );
                    while (!vault.has_sufficient_space() && !handler.is_finished())
                    {
                        handler.sleep_seconds(10);
                    }
                }
            }
            if (!vault.has_sufficient_space())
            {
                Main.get_control().add_rfc_file_mail(file_mail, milter.getMandant(), milter.getDiskArchive(), /*bg*/true, /*del_after*/ true);
            }
            else
            {
                LogManager.log(Level.SEVERE, Main.Txt("Skipping_mail_from_milter,_no_space_left") );
                return JilterStatus.SMFIS_TEMPFAIL;
            }
        }

        catch (Exception ex)
        {
            // TODO: ARCHIVE FAILED
            LogManager.log(Level.SEVERE, null, ex);
        }
        return JilterStatus.SMFIS_CONTINUE;        
    }

    @Override
    public JilterStatus body( ByteBuffer bodyp )
    {
        try
        {
            if (bodyp.hasArray())
            {
                os.write(bodyp.array());
            }
            else
            {
                if (tmp_buffer.length < bodyp.position())
                {
                    tmp_buffer = new byte[bodyp.limit()];
                }
                bodyp.get(tmp_buffer, 0, bodyp.position());

                os.write(tmp_buffer, 0, bodyp.position());
            }
        }
        catch (IOException iOException)
        {
            LogManager.log(Level.SEVERE, Main.Txt("Could_not_write_milter_body"), iOException);
            return JilterStatus.SMFIS_TEMPFAIL;

        }
        return super.body(bodyp);
    }

    @Override
    public JilterStatus header( String headerf, String headerv )
    {
        header_sb.setLength(0);
        header_sb.append(headerf);
        header_sb.append(": ");
        header_sb.append(headerv);
        header_list.add(header_sb.toString());

        return JilterStatus.SMFIS_CONTINUE;
    }

    void add_bcc_recpients(MimeMessage m)
    {
        ArrayList<Address> bcc_list = new ArrayList<Address>();

        try
        {
            Address[] mail_recipients = m.getAllRecipients();


            for (int i = 0; i < esmtp_rcpt_args.size(); i++)
            {
                String string = esmtp_rcpt_args.get(i);
                Address a = new InternetAddress(string);

                boolean found = false;

                for (int j = 0; j < mail_recipients.length; j++)
                {
                    Address address = mail_recipients[j];
                    if (address.equals(a))
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    bcc_list.add(a);

                }
            }
        }
        catch (MessagingException messagingException)
        {
            LogManager.log(Level.WARNING, "Error while detecting bcc addresses", messagingException);
        }
        file_mail.set_bcc(bcc_list);
    }
}

public class MilterImporter extends WorkerParentChild
{

    private ServerSocketChannel serverSocketChannel = null;
    InetSocketAddress adress;

    final ArrayList<JilterServerRunnable> active_milter_list;
    Milter milter;

    public Milter get_milter()
    {
        return milter;
    }

    private JilterHandler newHandler() throws InstantiationException, IllegalAccessException
    {
        return new MailImportJilterHandler( this );
    }
    public SocketAddress getSocketAddress()
    {
        return serverSocketChannel.socket().getLocalSocketAddress();
    }


    public MilterImporter( Milter _milter) throws IOException
    {
        milter = _milter;
        adress = new InetSocketAddress(milter.getOutServer(), milter.getOutPort()) ;

        log_debug(Main.Txt("Opening_socket"));

        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(true);

        log_debug(Main.Txt("Binding_to_endpoint_") + adress);
        this.serverSocketChannel.socket().bind(adress);

        log_debug(Main.Txt("Bound_to_") + getSocketAddress());

        do_finish = false;

        active_milter_list = new ArrayList<JilterServerRunnable>();
    }
    
    private void log_debug( String s )
    {
        LogManager.debug_msg( s );
    }
    private void log_debug( String s, Exception e )
    {
        LogManager.debug_msg( s, e );
    }


    @Override
    public void finish()
    {
        do_finish = true;
        try
        {
            serverSocketChannel.close();
        }
        catch (IOException ex)
        {
        }
    }

    @Override
    public void run_loop()
    {
        started = true;
        while (!do_finish)
        {
            SocketChannel connection = null;

            try
            {
                log_debug(Main.Txt("Going_to_accept"));

                connection = this.serverSocketChannel.accept();
                log_debug(Main.Txt("Got_a_connection_from_") + connection.socket().getInetAddress().getHostAddress());

                log_debug(Main.Txt("Firing_up_new_thread"));

                JilterServerRunnable sr = new JilterServerRunnable( connection, newHandler() );
                synchronized(active_milter_list)
                {
                    active_milter_list.add(sr);
                }

                new Thread( sr, "MilterImporter " + connection.socket().getInetAddress().getHostAddress() ).start();



                log_debug(Main.Txt("Thread_started"));
            }
            catch (SocketTimeoutException ste)
            {

            // do nothing
            }
            catch (IOException e)
            {
                if (!do_finish)
                    log_debug(Main.Txt("Unexpected_exception"), e);
            }
            catch (InstantiationException e)
            {
                log_debug(Main.Txt("Unexpected_exception"), e);
            }
            catch (IllegalAccessException e)
            {
                log_debug(Main.Txt("Unexpected_exception"), e);
            }
        }
        finished = true;
    }
    @Override
    public void idle_check()
    {

        synchronized(active_milter_list)
        {
            for (int i = 0; i < active_milter_list.size(); i++)
            {
                JilterServerRunnable sr = active_milter_list.get(i);
                if (sr.is_finished())
                {
                    active_milter_list.remove(i);
                    i = -1;
                    continue;
                }
            }
        }
    }
    
    public int getInstanceCnt()
    {
        int r = 0;
        synchronized(active_milter_list)
        {
            r = active_milter_list.size();
        }
        return r;
    }

   
    @Override
    public Object get_db_object()
    {
        return milter;
    }
    @Override
    public String get_task_status_txt()
    {
        return "";
    }

   

    @Override
    public String get_name()
    {
        return "MilterImporter";
    }

}

