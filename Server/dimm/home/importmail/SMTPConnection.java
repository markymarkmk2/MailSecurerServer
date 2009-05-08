package dimm.home.importmail;

import dimm.home.mailarchiv.*;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

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
    
    
    // variables    
    static int thread_count = 0;
    
    /**
     *  Constructor
     * 
     * @param host Host name or IP address
     */

    
   
    public void runConnection(Socket _clientSocket)
    {
        boolean do_quit = false;
        
        m_Stop = false;
        m_error = -1;
        m_Command = -1;

        clientSocket = _clientSocket;
        try
        {
            // set the socket timeout
            //clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            // get the client output stream
            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(),
                    clientSocket.getSendBufferSize());
            // get the client input stream	
            clientReader = new BufferedInputStream(clientSocket.getInputStream(),
                    clientSocket.getReceiveBufferSize());

//            clientReader = clientSocket.getInputStream();
            
            // connect to the real POP3 server
            serverSocket = new Socket(proxy.getOutServer(), proxy.getOutPort());
            // set the socket timeout
            serverSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(2, "getSoTimeout: " + serverSocket.getSoTimeout());

            // get the server output stream
            serverWriter = new BufferedOutputStream(serverSocket.getOutputStream(),
                    serverSocket.getSendBufferSize());
            // get the server input stream
            serverReader = new BufferedInputStream(serverSocket.getInputStream(),
                    serverSocket.getReceiveBufferSize());
 
 //           serverReader = serverSocket.getInputStream();

            
            String sData = "";

            // FIRST RESPONSE IS SINGLE LINE
            m_Command = SMTP_SINGLELINE;
            
            while (true)
            {

                // read the answer from the server
                log( 3, "Waiting for Server...");
                sData = getDataFromInputStream(serverReader).toString();

                // verify if the user stopped the thread
                if (m_Stop)
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
                    if (DATA() != 0)
                    {
                        break;
                    }

                    m_Command = SMTP_SINGLELINE;
                    continue;
                }

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    break;
                }

                log( "S: " + sData);
                // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();

                // QUIT
                if (do_quit)
                {
                    break;
                }

                // reset the command
                m_Command = -1;

                log( 3, "Waiting for Client...");
                // read the POP command from the client
                sData = getDataFromInputStream(clientReader, SMTP_CLIENTREQUEST).toString();

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    break;
                }

                // if the reader failed, exit
                if (m_error > 0)
                {
                    break;
                }

                log( "C: " + sData);

                // write it to the POP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();

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

            closeConnections();

            System.gc();

        }
        catch (UnknownHostException uhe)
        {
            String msgerror = "Verify that you are connected to the internet or that the SMTP server '" + proxy.getOutServer() + "' exists.";
            //Common.showError(msgerror);
            Main.err_log(msgerror);
        }
        catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
        log(2, "Finished" );
        
    }  // handleConnection

/*
    StringBuffer processMessage(StringBuffer sData)
    {
        	switch (m_Command)
        {
        case SMTP_DATA:
        sData = ensureEndOfMessage(sData);
        }
         
        return sData;
    }
*/
    int read_line( byte buffer[] ) throws IOException
    {
                
        int rlen = clientReader.read(buffer);
        if (rlen <= 0)
            return -1;
        
        while ( buffer[ rlen - 1 ] != '\n' && buffer[ rlen - 1] != '\r')
        {
            int local_rlen = clientReader.read(buffer, rlen, buffer.length );
            if (local_rlen <= 0)
                return -1;
            
                rlen += local_rlen;
        }
        return rlen;        
    }
    
    private int DATA()
    {

        File rfc_dump = new File(Main.RFC_PATH + "smtp_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = get_rfc_stream(rfc_dump);

        if (bos == null)
        {        
            if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }


        int ret = get_multiline_proxy_data(clientReader, serverWriter,  rfc_dump, bos);
        
        // CLOSE STREAM
        try
        {
            bos.close();

            if (ret == 0)
            {
                notify_control();
            }  
            else
            {                
                rfc_dump.delete();
            }
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            Main.err_log_fatal("Cannot close rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

            if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
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

    @Override
    public String getProtokollStr()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}  // POP3connection

