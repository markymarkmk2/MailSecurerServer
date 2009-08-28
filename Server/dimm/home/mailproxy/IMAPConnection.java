package dimm.home.mailproxy;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.logging.Level;

public class IMAPConnection extends MailConnection
{
    
    static int thread_count = 0;    
    
    private static final int IMAP_SINGLELINE = 1;
    private static final int IMAP_MULTILINE = 2;
    private static final int IMAP_RETR = 3;
    private static final int IMAP_IDLE = 4;
     
    /**
     *  Constructor
     * 
     * @param host Host name or IP address
     */
    IMAPConnection( ProxyEntry pe)
    {
        super( pe );
    }
        
    
    StringBuffer getIMAPStream(BufferedInputStream reader, int command_type)
    {
        final int MAX_BUF = 8192;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        StringBuffer output = new StringBuffer("");		// output string

        boolean finished = false;
        int rlen = 0;
        
        while ( !finished && m_error < 0)
        {
            try
            {
                // we are retrying the read operation
                // because the timeout was triggered.
                // we increase slowly the timeout.
                if (m_retries > 0)
                {
                    serverSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
                }

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    return output;
                }


                rlen = reader.read(buffer);
                
                // DETECT COMPLETE SERVER ANSWER:
/* Example:    C: A932 EXAMINE blurdybloop
               S: * 17 EXISTS
               S: * 2 RECENT
               S: * OK [UNSEEN 8] Message 8 is first unseen
               S: * OK [UIDVALIDITY 3857529045] UIDs valid
               S: * FLAGS (\Answered \Flagged \Deleted \Seen \Draft)
               S: * OK [PERMANENTFLAGS ()] No permanent flags permitted
               S: A932 OK [READ-ONLY] EXAMINE completed
  */              
                if (this.has_eol(buffer, rlen))
                {
                    if (command_type == IMAPConnection.IMAP_SINGLELINE)
                    {
                        finished = true;
                    }
                    else if (rlen > 2)
                    {
                        int last_nl_idx = rlen -2;
                        while (last_nl_idx > 0)
                        {
                            if (buffer[last_nl_idx] == '\n')
                            {
                                last_nl_idx++;
                                break;
                            }
                            last_nl_idx--;
                        }
                        if (buffer[last_nl_idx] != '*')
                            finished = true;
                    }
                }
                
                // NO MORE DATA ?
                if (rlen == -1)
                {
                    if (has_eol( output )) 
                        finished = true;   
                    else
                        m_error = ERROR_NO_ANSWER;
                }

                for (int i = 0; i < rlen; i++)
                {
                    output.append( (char) buffer[i]);
                }                        
            } 
            catch (SocketTimeoutException ste)
            {
                /////////////////////
                // timeout triggered
                /////////////////////

                // no data
                if (output.length() == 0)
                {
                    // the max quantity of retries was reached
                    // without answer
                    if (m_retries == SOCKET_TIMEOUT.length - 1)
                    {
                        m_error = ERROR_TIMEOUT_EXCEEDED;
                    } else
                    {
                        // we try again to read a message recursively
                        m_retries++;
                        log( "Mail timeout. Trying again [" + m_retries + "]");
                    }
                }
                else
                {
                    // COMMAND ENDED BY TIMEOUT, PROBABLY UNKNOWN, CHECK FOR EOL
                    if (has_eol( output )) 
                    {
                        finished = true;                         
                    }                   
                }
            } 
            catch (Exception e)
            {
                // reader failed
                m_error = ERROR_UNKNOWN;
                Main.err_log("Exception: " + e.getMessage());
            }            
        }
        
        if (!finished)
        {
            m_error = ERROR_NO_ANSWER;
            Main.err_log("Aborted answer: " + output.toString());
        }
        
        return output;
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
                LogManager.log(Level.SEVERE, null, ex);
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
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(2, "getSoTimeout: " + serverSocket.getSoTimeout());

            // get the server output stream
            serverWriter = new BufferedOutputStream(serverSocket.getOutputStream(), serverSocket.getSendBufferSize());
            // get the server input stream
            serverReader = new BufferedInputStream(serverSocket.getInputStream(), serverSocket.getReceiveBufferSize());

            String sData = "";

            // THE FIRST RESPONSE FROM SERVER IS SINGLE LINE
            m_Command = IMAPConnection.IMAP_SINGLELINE;
            
            while (true)
            {

                // read the answer from the server
                log( 2, "Waiting for Server...");
                sData = getIMAPStream(serverReader, m_Command).toString();

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
                        log( "Error : " + getErrorMessage());
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

                log( "S: " + sData);
                if (trace_writer != null)
                    trace_writer.write(sData);
                
                // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();
                
                if (m_Command == IMAP_IDLE && sData.charAt(0) == '+')
                {
                    if (!IDLE( trace_writer  ))
                    {
                        break;
                    }
                }
                    
                
                // QUIT
                if (do_quit || m_Stop)
                {
                    break;
                }

                // reset the command
                m_Command = -1;

                log( 2, "Waiting for Client...");
                
                // read the POP command from the client
                sData = getIMAPStream(clientReader, IMAPConnection.IMAP_SINGLELINE).toString();

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
                if (trace_writer != null)
                    trace_writer.write(sData);
                
                while (sData.toUpperCase().indexOf("FETCH") > 0)
                {
                    m_Command = IMAP_RETR;
                    
                    // write it to the POP server
                    serverWriter.write(sData.getBytes());
                    serverWriter.flush();

                    boolean with_body = ((sData.toUpperCase().indexOf("BODY[") > 0 || sData.toUpperCase().indexOf("BODY.PEEK[") > 0) && sData.toUpperCase().indexOf("[HEADER]") == -1);
                    
                    if (FETCH( trace_writer, with_body ) > 0)
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
                    
                    sData = getIMAPStream(clientReader, IMAP_SINGLELINE).toString();
                    log( "C: " + sData);
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
                
                // ALL FURTHER ANSWERS ARE MULTILINE
                m_Command = IMAP_MULTILINE;
                
                if (is_command_quit( sData ))
                {
                    do_quit = true;
                }         
                if (is_command_idle( sData ))
                {
                    m_Command = IMAP_IDLE;                    
                }      
                
            }  // while
        } 
        catch (UnknownHostException uhe)
        {
            String msgerror = "Verify that you are connected to the internet or that the IMAP server '" + pe.getHost() + "' exists.";
            //Common.showError(msgerror);
            Main.err_log(msgerror);
        } 
        catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
        
        try
        {
            closeConnections();
        }
        catch (Exception e)
        {
        }
        
        log( "Finished" );
    }  // handleConnection

    @Override
    boolean is_command_quit(String sData)
    {     
        if (sData.toUpperCase().indexOf("logout") > 0)
        {
            return true;
        }
        return false;
    }
    private boolean is_command_idle(String sData)
    {
        if (sData.toUpperCase().indexOf("IDLE") > 0)
        {
            return true;
        }
        return false;
    }
    
    boolean IDLE(FileWriter trace_writer) throws IOException
    {
        String sData;
        boolean idle_done = false;
        
        
        
        while (!idle_done && clientSocket.isConnected() && serverSocket.isConnected() && !m_Stop)
        {
            while (!m_Stop && serverReader.available() == 0 && clientReader.available() == 0 && clientSocket.isConnected() && serverSocket.isConnected())
            {
                LogicControl.sleep(500);
            }
            if (serverReader.available() > 0)
            {
                sData = getIMAPStream(serverReader, IMAP_SINGLELINE).toString();
                log( "S: " + sData);
                if (trace_writer != null)
                    trace_writer.write(sData);

                 // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();
            }

            if (clientReader.available() > 0)
            {
                sData = getIMAPStream(clientReader, IMAP_SINGLELINE).toString();
                log( "C: " + sData);
                if (sData.toUpperCase().indexOf("DONE") >= 0)
                    idle_done = true;

                if (trace_writer != null)
                    trace_writer.write(sData);

                 // write the answer to the POP client
                serverWriter.write(sData.getBytes());
                serverWriter.flush();

                sData = getIMAPStream(serverReader, IMAP_SINGLELINE).toString();
                log( "S: " + sData);
                // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();                        
            }
        }       
        
        return idle_done;
    }

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

   


    private int FETCH(FileWriter trace_writer, boolean with_body)
    {
        // GET ACCEPT ANSWER FROM SERVER

        // NOW MOVE DATA FROM SMTP-CLIENT TO SERVER 
	boolean finished = false;
        

        File rfc_dump = new File(Main.RFC_PATH + "imap_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = null;
        
        if (with_body)
        {
            bos = Main.get_control().get_mail_archiver().get_rfc_stream(rfc_dump);

            if (bos == null)
            {        
                if (Main.get_bool_prop(Preferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
                {
                    m_error = ERROR_UNKNOWN;
                    return m_error;
                }
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

                
               // int avail = serverReader.available();
                
                rlen = serverReader.read(buffer);
                
                if (this.has_eol(buffer, rlen))
                {
                    if (rlen > 2)
                    {
                        int last_nl_idx = rlen -2;
                        while (last_nl_idx > 0)
                        {
                            if (buffer[last_nl_idx] == '\n')
                            {
                                last_nl_idx++;
                                break;
                            }
                            last_nl_idx--;
                        }
                        if (buffer[last_nl_idx] != '*')
                            finished = true;
                    }
                }
                
                // NO MORE DATA ?
                if (rlen == -1)
                {
                    if (has_eol( buffer, rlen )) 
                        finished = true;   
                    else
                        m_error = ERROR_NO_ANSWER;
                }
                
                
                
                String d  = new String(buffer, 0, rlen);
                
                if (!with_body)
                {
                    log( "S: " + d  );
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
                         log( "IMAP timeout. Trying again [" + m_retries + "]");
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

    @Override
    public int get_default_port()
    {
        return 143;
    }

    @Override
    String[] get_single_line_commands()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    String[] get_multi_line_commands()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

 
}  // POP3connection

