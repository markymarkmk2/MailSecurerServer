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
import dimm.home.mail.RFCMailStream;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;




/*
 * Jiolter Samle Code
 * */


 /*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */


/**
 *
 * @author mw
 */


class ServerRunnable implements Runnable
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

    public ServerRunnable(SocketChannel socket, JilterHandler handler)
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
        ByteBuffer dataBuffer = ByteBuffer.allocateDirect(4096);

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
    ByteArrayOutputStream out_stream;
    Milter milter;


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
        out_stream = new ByteArrayOutputStream();
        sender = null;
        connect_host = "localhost";
    }

    @Override
    public int getSupportedProcesses()
    {
        return PROCESS_CONNECT | PROCESS_BODY | PROCESS_ENVFROM | PROCESS_ENVRCPT | PROCESS_HEADER;
    }

    @Override
    public JilterStatus connect( String hostname, InetAddress hostaddr, Properties properties )
    {
		if (hostaddr != null)
        {
			connect_host = hostaddr.toString();
		} 
        else if (connect_host!=null)
        {
			connect_host = hostname;
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
        ByteArrayInputStream mail_stream = new ByteArrayInputStream( out_stream.toByteArray() );
        RFCMailStream mail = new RFCMailStream( mail_stream, this.getClass().getCanonicalName() );
        try
        {
            Main.get_control().add_new_outmail(mail, milter.getMandant(), milter.getDiskArchive(), false);
        }
        catch (Exception ex)
        {
            // TODO: ARCHIVE FAILED
            Logger.getLogger(MailImportJilterHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        return JilterStatus.SMFIS_CONTINUE;
        
    }

    @Override
    public JilterStatus body( ByteBuffer bodyp )
    {
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
}

public class MilterImporter implements WorkerParentChild
{

    private ServerSocketChannel serverSocketChannel = null;
    InetSocketAddress adress;

    final ArrayList<ServerRunnable> active_milter_list;
    Milter milter;


    private JilterHandler newHandler() throws InstantiationException, IllegalAccessException
    {
        return new MailImportJilterHandler( milter );
    }
    public SocketAddress getSocketAddress()
    {
        return serverSocketChannel.socket().getLocalSocketAddress();
    }


    public MilterImporter( Milter _milter)
        throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException
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

        active_milter_list = new ArrayList<ServerRunnable>();
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

                ServerRunnable sr = new ServerRunnable( connection, newHandler() );
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
    public void idle_check()
    {
        synchronized(active_milter_list)
        {
            for (int i = 0; i < active_milter_list.size(); i++)
            {
                ServerRunnable sr = active_milter_list.get(i);
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

