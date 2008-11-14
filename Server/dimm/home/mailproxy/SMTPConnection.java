package dimm.home.mailproxy;

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
public class SMTPConnection extends MailConnection
{
    // constants
    String multi_line_commands[] = {"DATA"};
    String single_line_commands[] = {"QUIT", "HELP", "MAIL", "RCPT", "RSET", "SEND", "SOML", "VRFY", "EXPN", "NOOP", "TURN"};
    
    String[] get_single_line_commands()
    {
        return single_line_commands;
    }
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
    SMTPConnection(String host, int RemotePort)
    {
        super( host, RemotePort );
    }

   
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
            clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            // get the client output stream
            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(),
                    clientSocket.getSendBufferSize());
            // get the client input stream	
            clientReader = new BufferedInputStream(clientSocket.getInputStream(),
                    clientSocket.getReceiveBufferSize());

            // connect to the real POP3 server
            serverSocket = new Socket(m_host, m_RemotePort);
            // set the socket timeout
            serverSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            Main.debug_msg(1, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(1, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(1, "getSoTimeout: " + serverSocket.getSoTimeout());

            // get the server output stream
            serverWriter = new BufferedOutputStream(serverSocket.getOutputStream(),
                    serverSocket.getSendBufferSize());
            // get the server input stream
            serverReader = new BufferedInputStream(serverSocket.getInputStream(),
                    serverSocket.getReceiveBufferSize());

            
            String sData = "";

            // FIRST RESPONSE IS SINGLE LINE
            m_Command = SMTP_SINGLELINE;
            
            while (true)
            {

                // read the answer from the server
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
                        Main.debug_msg(1, "Error : " + getErrorMessage());
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

                    m_Command = -1;
                    continue;
                }

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    break;
                }

                Main.debug_msg(1, "S: " + sData);
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

                // read the POP command from the client
                sData = getDataFromInputStream(clientReader, SMTP_SINGLELINE).toString();

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

                Main.debug_msg(1, "C: " + sData);

                // write it to the POP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();

                if (sData.toUpperCase().startsWith("DATA"))
                {
                    m_Command = SMTP_DATA;
                }
                else
                {
                    if (is_command_multiline(sData))
                        m_Command = SMTP_MULTILINE;
                    else if (is_command_singleline(sData))
                        m_Command = SMTP_SINGLELINE;
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
            String msgerror = "Verify if you are connected to the internet or " + " if the POP server '" + m_host + "' exists.";
            Common.showError(msgerror);
            Main.err_log(msgerror);
        }
        catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
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
        // GET ACCEPT ANSWER FROM SERVER

        // NOW MOVE DATA FROM SMTP-CLIENT TO SERVER 
        boolean finished = false;

        

        File rfc_dump = new File(Main.RFC_PATH + "smtp_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = Main.get_control().get_mail_archiver().get_rfc_stream(rfc_dump);
        
        if (bos == null)
        {        
            if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }

        final int MAX_BUF = 2048;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        
        int rlen = 0;
        while (!finished && m_error <= 0)
        {
            try
            {
                // we are retrying the read operation
                // because the timeout was triggered.
                // we increase slowly the timeout.

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    return 1;
                }

                int avail = clientReader.available();
                
                
                if (avail > buffer.length + END_OF_MULTILINE.length)
                {
                    rlen = clientReader.read(buffer);
                }
                else
                {
                    if (avail > END_OF_MULTILINE.length)
                    {
                        rlen = clientReader.read(buffer, 0, avail - END_OF_MULTILINE.length);
                    }
                    else
                    {
                        rlen = clientReader.read(buffer, 0, END_OF_MULTILINE.length);
                    }
                }

                if (rlen == END_OF_MULTILINE.length)
                {
                    int i = 0;
                    for (i = 0; i < END_OF_MULTILINE.length; i++)
                    {
                        if (buffer[i] != END_OF_MULTILINE[i])
                            break;
                    }
                    if ( i == END_OF_MULTILINE.length)
                        finished = true;
                }
                  

                if (rlen > 0)
                {                            
                    serverWriter.write(buffer, 0, rlen);
                    
                  
                    if (finished)
                    {
                        serverWriter.flush();
                    }

                    if (bos != null)
                    {
                        try
                        {
                            bos.write(buffer, 0, rlen);
                        }
                        catch (Exception exc)
                        {
                            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
                            Main.err_log_fatal("Cannot write to rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

                            // CLOSE AND INVALIDATE STREAM
                            try
                            {
                                bos.close();
                                
                            }
                            catch (Exception exce)
                            {
                            }
                            finally
                            {
                                if (rfc_dump.exists())
                                    rfc_dump.delete();
                            }

                            bos = null;
                            

                            if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
                            {
                                m_error = ERROR_UNKNOWN;
                                return m_error;
                            }
                        }
                    }
                }
                else
                {
                    if (bos != null)
                    {
                        try
                        {
                            bos.close();
                        }
                        catch (Exception exce)
                        {
                        }
                        finally
                        {
                            if (rfc_dump.exists())
                                rfc_dump.delete();
                        }                        
                    }
                    
                    m_error = ERROR_NO_ANSWER;
                    return m_error;
                }

                try
                {
                    // return to the normal timeout (faster answer)
                    if (m_retries > 0)
                    {
                        m_retries = 0;
                        clientSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
                    }
                }
                catch (Exception ex)
                {
                    Main.err_log(ex.getMessage());
                }

            }
            catch (SocketTimeoutException ste)
            {
                /////////////////////
                // timeout triggered
                /////////////////////

                if (rlen == 0)
                {
                    // the max quantity of retries was reached
                    // without answer
                    if (m_retries == SOCKET_TIMEOUT.length - 1)
                    {
                        m_error = ERROR_TIMEOUT_EXCEEDED;
                    }
                    else
                    {
                        // we try again to read a message recursively
                        m_retries++;
                        try
                        {
                            clientSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
                        }
                        catch (Exception exc)
                        {
                        }
                        Main.debug_msg(1, "timeout. Trying again [" + m_retries + "]");
                    }
                }
            }
            catch (Exception e)
            {
                // reader failed
                m_error = ERROR_UNKNOWN;
                Main.err_log(e.getMessage());
            }
        }
        if (bos != null)
        {
            // CLOSE STREAM
            try
            {
                bos.close();
                
                if (finished)
                {
                    Main.get_control().get_mail_archiver().add_rfc_file( rfc_dump );
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
        }
        
        if (finished)
        {
            return 0;
        }
        if (m_error > 0)
            return m_error;
        
        return -1;

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

}  // POP3connection

