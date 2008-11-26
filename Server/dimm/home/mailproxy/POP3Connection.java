package dimm.home.mailproxy;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class POP3Connection extends MailConnection
{
    
    String multi_line_commands[] = {"LIST", "RETR", "UIDL", "TOP", "CAPA"};
    String single_line_commands[] = {"QUIT", "USER", "PASS", "DELE", "STAT", "NOOP", "RSET", "APOP", "STLS", "AUTH"};

    static int thread_count = 0;    
    
    String[] get_single_line_commands()
    {
        return single_line_commands;
    }
    String[] get_multi_line_commands()
    {
        return multi_line_commands;
    }

     
    /**
     *  Constructor
     * 
     * @param host Host name or IP address
     */
    POP3Connection( ProxyEntry pe)
    {
        super( pe );
    }

   
    public void runConnection(Socket _clientSocket)
    {
        boolean do_quit = false;
        m_Stop = false;
        m_error = -1;
        m_Command = -1;
        
        clientSocket = _clientSocket;
        
        FileWriter trace_writer = null;
        
        if (Main.trace_mode)
        {
            try
            {
                trace_writer = new FileWriter(File.createTempFile("pop3", ".txt"));
            }
            catch (IOException ex)
            {
                Logger.getLogger(POP3Connection.class.getName()).log(Level.SEVERE, null, ex);
                trace_writer = null;
            }
        }
            

        try
        {
            // set the socket timeout
            //clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            // get the client output stream
            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(), clientSocket.getSendBufferSize());
            // get the client input stream	
            clientReader = new BufferedInputStream(clientSocket.getInputStream(), clientSocket.getReceiveBufferSize());

            // connect to the real POP3 server
            serverSocket = new Socket(pe.getHost(), pe.getRemotePort());
            // set the socket timeout
            serverSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            Main.debug_msg(1, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(1, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(1, "getSoTimeout: " + serverSocket.getSoTimeout());

            // get the server output stream
            serverWriter = new BufferedOutputStream(serverSocket.getOutputStream(), serverSocket.getSendBufferSize());
            // get the server input stream
            serverReader = new BufferedInputStream(serverSocket.getInputStream(), serverSocket.getReceiveBufferSize());

            String sData = "";

            // THE FIRST RESPONSE FROM SERVER IS SINGLE LINE
            m_Command = POP_SINGLELINE;
            
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


                // verify if the user stopped the thread
                if (m_Stop)
                {
                    break;
                }

                Main.debug_msg(1, "S: " + sData);
                if (trace_writer != null)
                    trace_writer.write(sData);
                
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
                sData = getDataFromInputStream(clientReader, POP_SINGLELINE).toString();

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
                if (trace_writer != null)
                    trace_writer.write(sData);
                
                while (sData.toUpperCase().startsWith("RETR "))
                {
                    m_Command = POP_RETR;
                    
                    // write it to the POP server
                    serverWriter.write(sData.getBytes());
                    serverWriter.flush();

                    if (RETRBYTE( trace_writer ) > 0)
                    {
                        clientWriter.write(getErrorMessage().getBytes());
                        clientWriter.flush();                        
                        break;
                    }      
                    // verify if the user stopped the thread
                    if (m_Stop)
                    {
                        break;
                    }
                    
                    sData = getDataFromInputStream(clientReader, POP_SINGLELINE).toString();
                    Main.debug_msg(1, "C: " + sData);
                    if (trace_writer != null)
                        trace_writer.write(sData);

                    
                    // verify if the user stopped the thread
                    if (m_Stop)
                    {
                        break;
                    }
                } 

                // write it to the POP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();
                
                if (is_command_multiline( sData ))
                {
                    m_Command = POP_MULTILINE;
                }
                else if (is_command_singleline( sData ))
                {
                    m_Command = POP_SINGLELINE;
                }
                if (is_command_quit( sData ))
                {
                    do_quit = true;
                }                               

            }  // while

            closeConnections();

            System.gc();

        } catch (UnknownHostException uhe)
        {
            String msgerror = "Verify that you are connected to the internet or that the POP server '" + pe.getHost() + "' exists.";
            //Common.showError(msgerror);
            Main.err_log(msgerror);
        } catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
    }  // handleConnection


/*
    StringBuffer processMessage(StringBuffer sData)
    {
        switch (m_Command)
        {            
            case POP_MULTILINE:
                sData = ensureEndOfMessage(sData);
        }

        return sData;
    }*/

    private StringBuffer ensureEndOfMessage(StringBuffer sData)
    {
        // look for the point at the end of the message
        int pointPosition = sData.lastIndexOf(".");

        // if the message was not concluded
        // i.e. the point was not found at the end of the string
        if (pointPosition < sData.length() - 5)
        {
            Main.debug_msg(1, "MESSAGE NOT FINISHED");
            // remove the last new line
//    		int index = sData.lastIndexOf(Common.LINE_FEED);
//    		// if the line feed was found
//    		if (index>-1)
//    		{
//    			// remove the line feed
//    			sData = sData.delete(index, sData.length());
//    		}
            // add the rest of the message
            sData.append(getDataFromInputStream(serverReader));
        }

        return sData;
    }


    private int RETRBYTE(FileWriter trace_writer)
    {
        // GET ACCEPT ANSWER FROM SERVER

        // NOW MOVE DATA FROM SMTP-CLIENT TO SERVER 
	boolean finished = false;
        

        File rfc_dump = new File(Main.RFC_PATH + "pop3_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = Main.get_control().get_mail_archiver().get_rfc_stream(rfc_dump);

        if (bos == null)
        {        
            if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }
        
        int rlen = 0;
        final int MAX_BUF = 2048;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        
        
        
        while (!finished && m_error <= 0)
        {
            try 
            {
                // we are retrying the read operation
                // because the timeout was triggered.
                // we increase slowly the timeout.

                // verify if the user stopped the thread
                if (m_Stop)	
                    return 1;

                
                int avail = serverReader.available();
                
                if (avail > buffer.length + END_OF_MULTILINE.length)
                {
                    rlen = serverReader.read(buffer);
                }
                else
                {
                    if (avail > END_OF_MULTILINE.length)
                    {
                        rlen = serverReader.read(buffer, 0, avail - END_OF_MULTILINE.length);
                    }
                    else
                    {
                        rlen = serverReader.read(buffer, 0, END_OF_MULTILINE.length);
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
                    if (trace_writer != null)
                    {
                        String data = new String( buffer, 0, rlen );
                        trace_writer.write(data, 0, rlen);
                    }
                    
                    clientWriter.write(buffer, 0, rlen);
                                      
                    if (finished)
                    {
                        clientWriter.flush();
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
                    if (m_retries>0)
                    {
                        m_retries=0;
                        serverSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
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
                     if(m_retries==SOCKET_TIMEOUT.length -1)
                     {
                        m_error = ERROR_TIMEOUT_EXCEEDED;
                     }
                     else
                     {
                         // we try again to read a message recursively
                         m_retries++;
                         try
                         {
                            serverSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
                         }
                         catch (Exception exc)
                         {}
                         Main.debug_msg(1, "POP3 timeout. Trying again [" + m_retries + "]");
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
                bos.flush();
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

