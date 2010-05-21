package dimm.home.importmail;

import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.workers.MailProxyServer;
import home.shared.Utilities.DefaultSSLSocketFactory;
import home.shared.mail.RFCGenericMail;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import java.io.BufferedOutputStream;
import java.io.File;
import javax.net.SocketFactory;

/**
 * POP3Connection - Handles the POP3 connections.
 *  
 *  
 * @version 1.00, 05/05/04
 * @author Jocca Jocaf
 *
 */
public class SMTPConnection extends ProxyConnection
{
    private static final String NAME = "SMTP-Proxy";
    // constants
    String multi_line_commands[] = {"EHLO"};
    String single_line_commands[] = {"AUTH", "QUIT", "HELP", "MAIL", "RCPT", "RSET", "SEND", "SOML", "VRFY", "EXPN", "NOOP", "TURN"};
    
    @Override
    String[] get_single_line_commands()
    {
        return single_line_commands;
    }
    @Override
    String[] get_multi_line_commands()
    {
        return multi_line_commands;
    }

    public SMTPConnection(ProxyEntry pe, Socket s)
    {
        super( pe, s );
    }

    
    // variables    
    static int thread_count = 0;
    
    /**
     *  Constructor
     * 
     * @param host Host name or IP address
     */

    boolean is_connected( Socket sock )
    {
        return !(sock.isClosed() || sock.isInputShutdown() || !sock.isConnected());
    }

    boolean client_is_connected()
    {
        return is_connected(clientSocket);
    }

    
   
    @Override
    public void runConnection()
    {
        boolean do_quit = false;
        
        m_error = -1;
        int m_Command = -1;

        try
        {
/*            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(),
                    clientSocket.getSendBufferSize());

            clientReader = new BufferedInputStream(clientSocket.getInputStream(),
                    clientSocket.getReceiveBufferSize());
*/
            clientWriter = clientSocket.getOutputStream();
            clientReader = clientSocket.getInputStream();
            
            // CREATE SERVERSOCKET
            if (pe.isSSL())
            {
                SocketFactory sf = DefaultSSLSocketFactory.getDefault();
                serverSocket = sf.createSocket(pe.get_proxy().getRemoteServer(), pe.get_proxy().getRemotePort());
            }
            else
            {
                serverSocket = new Socket(pe.get_proxy().getRemoteServer(), pe.get_proxy().getRemotePort());
            }

            serverSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            
      /*      LogManager.debug_msg(DBG_VERB, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            LogManager.debug_msg(DBG_VERB, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            LogManager.debug_msg(DBG_VERB, "getSoTimeout: " + serverSocket.getSoTimeout());*/

            serverWriter = serverSocket.getOutputStream();
            serverReader = serverSocket.getInputStream();
 
 
            
            String sData = "";

            // FIRST RESPONSE IS SINGLE LINE
            m_Command = SMTP_SINGLELINE;
            int dbg_level = (int)Main.get_debug_lvl();
            
            while (true)
            {

                // read the answer from the server
                if (dbg_level >= DBG_VERB)
                    log( DBG_VERB, Main.Txt("Waiting_for_Server..."));
                reset_timeout();
                sData = getDataFromInputStream(serverReader, serverSocket, m_Command, /*-wait*/ true).toString();

                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    break;
                }

                // if the reader failed, exit
                if (m_error > 0)
                {
                    if (clientSocket.isConnected() && !clientSocket.isClosed() && clientWriter != null && sData.length() > 0)
                    {
                        // write the answer to the POP client
                        log(1, "Error : " + getErrorMessage());
                        clientWriter.write(getErrorMessage().getBytes());
                        clientWriter.flush();
                    }
                    break;
                }

                if (m_Command == SMTP_DATA)
                {
                    clientWriter.write(sData.getBytes());
                    clientWriter.flush();
                    reset_timeout();

                    if (DATA() != 0)
                    {
                        break;
                    }

                    m_Command = SMTP_SINGLELINE;
                    continue;
                }
                reset_timeout();

                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    break;
                }

                if (dbg_level >= DBG_VERB)
                    log( "S: " + sData);
                // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();
                reset_timeout();

                // QUIT
                if (do_quit)
                {
                    break;
                }

                // reset the command
                m_Command = SMTP_SINGLELINE;

                if (dbg_level >= DBG_VERB)
                    log( DBG_VERB, Main.Txt("Waiting_for_Client..."));

                // CHECK FOR CLIENT W/O LOGOUT
                if (last_command != null && last_command.equals("DATA"))
                {
                    // WE LOG AFTER OUT 120 s
                    
                    int avail = wait_for_avail( clientReader, clientSocket, 120 );
                    if (avail == 0)
                    {
                        serverWriter.write("QUIT\r\n".getBytes());
                        serverWriter.flush();
                        break;
                    }
                }

                int sa = ACTIVITY_TIMEOUT;
                ACTIVITY_TIMEOUT = 180;
                reset_timeout();
                // read the SMTP command from the client
                sData = getDataFromInputStream(clientReader, clientSocket,  SMTP_CLIENTREQUEST, /*wait*/false).toString();
                last_command = sData.toUpperCase().trim();
                ACTIVITY_TIMEOUT = sa;

               

                // verify if the user stopped the thread
                if (pe.is_finished() )
                {
                    break;
                }

                // if the reader failed, exit
                if (m_error > 0)
                {
                    break;
                }

                if (dbg_level >= DBG_VERB)
                    log( "C: " + sData);

                // write it to the SMTP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();
                reset_timeout();

                String token = sData.toUpperCase();
                if (token.startsWith("DATA") || token.endsWith("\r\nDATA\r\n") )
                {
                    m_Command = SMTP_DATA;
                }
                else
                {
                    if (is_command_singleline(sData))
                        m_Command = SMTP_SINGLELINE;
                    else
                        m_Command = SMTP_MULTILINE;                    
                }
                if (is_command_quit(sData))
                {
                    do_quit = true;
                }
            }  // while


        }
        catch (UnknownHostException uhe)
        {
            String msgerror = Main.Txt("Verify_that_you_are_connected_to_the_internet_or_that_the_SMTP_server_'") + pe.get_proxy().getRemoteServer() + Main.Txt("'_exists.");
            LogManager.err_log(msgerror);
        }
        catch (Exception e)
        {
            if (!pe.is_finished())
                LogManager.err_log(get_description() + ": " + e.getMessage() + " last command:" + last_command);
        }
        finally
        {
            closeConnections();

            //System.gc();
        }
        log(DBG_VERB -2, Main.Txt("Finished") + " " + pe.get_proxy().getRemoteServer() );
        
    }  // handleConnection

    int read_line( byte buffer[] ) throws IOException
    {
                
        int rlen = clientReader.read(buffer);
        if (rlen <= 0)
            return -1;

        reset_timeout();

        while ( buffer[ rlen - 1 ] != '\n' && buffer[ rlen - 1] != '\r')
        {
            int local_rlen = clientReader.read(buffer, rlen, buffer.length );

            if (local_rlen <= 0)
                return -1;

            reset_timeout();
            rlen += local_rlen;
        }
        return rlen;        
    }
    
    private int DATA()
    {
        boolean encoded = true;
        String suffix = ".eml";
        if (encoded)
            suffix = RFCGenericMail.get_suffix_for_encoded();

        MandantContext m_ctx = Main.get_control().get_m_context(pe.get_proxy().getMandant());
        File rfc_dump = m_ctx.getTempFileHandler().create_new_mailimp_file("pop3_" + this_thread_id + "_" + System.currentTimeMillis() + suffix,
                            pe.get_proxy().getDiskArchive().getId());
//        File rfc_dump = new File(Main.RFC_PATH + "smtp_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = MailProxyServer.get_rfc_stream(rfc_dump, encoded);

        if (bos == null)
        {        
            if (Main.get_bool_prop(GeneralPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }


        int ret = get_multiline_proxy_data(clientReader, serverWriter,  rfc_dump, bos);

        disable_timeout();

        // CLOSE STREAM
        try
        {
            bos.close();

            if (ret == 0)
            {
               Main.get_control().add_mail_file( rfc_dump, pe.get_proxy().getMandant(), pe.get_proxy().getDiskArchive(), 
                                            /*bg*/ true, /*del_after*/ true, /*encoded*/ encoded );
            }  
            else
            {                
                rfc_dump.delete();
            }
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            LogManager.err_log_fatal("Cannot close rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

            if (Main.get_bool_prop(GeneralPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }
        

        return ret;
    }
    
    @Override
    void inc_thread_count()
    {
        thread_count++;
    }

    @Override
    int get_thread_count()
    {
        return thread_count;
    }

    @Override
    void dec_thread_count()
    {
        thread_count--;
    }

    @Override
    public int get_default_port()
    {
        return 25;
    }
} 

