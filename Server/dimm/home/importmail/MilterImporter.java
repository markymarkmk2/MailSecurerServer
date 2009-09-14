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
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.CS_Constants;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCMimeMail;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import javax.mail.Address;
import javax.mail.Message.RecipientType;
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
    Milter milter;

    File tmp_file;
    OutputStream os;
    byte[] tmp_buffer;


    public MailImportJilterHandler(Milter _milter)
    {
        milter = _milter;

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
                tmp_file.delete();
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

        MandantContext m_ctx = Main.get_control().get_m_context(milter.getMandant());

        try
        {
            tmp_file = m_ctx.getTempFileHandler().create_temp_file(/*SUBDIR*/"", "dump", "tmp");
            os = RFCFileMail.open_outputstream(tmp_file, RFCFileMail.dflt_encoded);
        }
        catch (Exception archiveMsgException)
        {
            LogManager.log(Level.SEVERE, Main.Txt("cannot_create_temp_file"), archiveMsgException);
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
            
            RFCFileMail file_mail = new RFCFileMail(tmp_file, false);

            RFCMimeMail mime_mail = new RFCMimeMail();
            mime_mail.parse(file_mail);

            add_bcc_recpients( mime_mail.getMsg() );

            
            Main.get_control().add_mail_file(tmp_file, milter.getMandant(), milter.getDiskArchive(), /*bg*/true, /*del_after*/ true);
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
        Address[] ret = new Address[bcc_list.size()];
        for (int i = 0; i < bcc_list.size(); i++)
        {
            ret[i] = bcc_list.get(i);
        }
        try
        {
            m.addRecipients(RecipientType.BCC, ret);
        }
        catch (MessagingException messagingException)
        {
            LogManager.log(Level.WARNING, "Error while adding bcc addresses", messagingException);
        }
    }
}

public class MilterImporter implements WorkerParentChild
{

    private ServerSocketChannel serverSocketChannel = null;
    InetSocketAddress adress;

    final ArrayList<JilterServerRunnable> active_milter_list;
    Milter milter;


    private JilterHandler newHandler() throws InstantiationException, IllegalAccessException
    {
        return new MailImportJilterHandler( milter );
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

    boolean do_finish;

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
            catch (IOException e)
            {
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
}

