package dimm.home.mailproxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import de.jocca.logger.MoreLogger;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

public class POP3Connection
{
    // constants
	/*
     *  The socket timeout is given in miliseconds. It means the max answer time of a read request.
     *  It begins with 0,4 seconds and if the read fails, it retries to read with 0,8 seconds and so on.
     *  In this example the sum would be 0,4 + 0,8 = 1,2 seconds for a read request.
     *  The sum of all 10 retries is 2 Minutes.
     */
    private static final int SOCKET_TIMEOUT[] = {800, 1200, 1800, 2500, 3000, 6000, 10000};
    /*
     * POP command RETR
     */
    private static final int POP_RETR = 1;
    /*
     * POP command LIST
     */
    private static final int POP_LIST = 2;
    /*
     * POP command QUIT
     */
    private static final int POP_QUIT = 3;
    private static final int ERROR_TIMEOUT_EXCEEDED = 1;
    private static final int ERROR_NO_ANSWER = 2;
    private static final int ERROR_UNKNOWN = 3;
    // variables    
    private String m_host;							// host name or IP adsress
    private int m_RemotePort = 110;					// port to connect
/*    private BufferedReader serverReader;			// server reader
    private BufferedReader clientReader;
    private BufferedWriter clientWriter;
    private BufferedWriter serverWriter;
 */
    private BufferedInputStream serverReader;			// server reader
    private BufferedInputStream clientReader;
    private BufferedOutputStream clientWriter;
    private BufferedOutputStream serverWriter;
    
    private static int m_retries;					// numbers of retries to read a message from the socket
    private int m_error;							// stores the last error
    private Socket serverSocket;					// server socket
    private int m_Command;							// actual POP command
    //private MoreLogger logger; 						// class from log4j by Jocca Jocaf
    private static boolean m_Stop;					// stop the thread

    
    static int thread_count = 0;
    
    int this_thread_id = 0;

    
    private static final int MAX_THREADS = 50;
    
    /**
     *  Constructor
     * 
     * @param host Host name or IP address
     */
    POP3Connection(String host, int RemotePort)
    {
      //  logger = new MoreLogger(POP3Connection.class);
        m_host = host;
        m_RemotePort = RemotePort;
    }

    public void handleConnection(final Socket clientSocket)
    {
        Thread sockThread;
        
        // runs the server
        synchronized( this )
        {
            thread_count++;
            this_thread_id = thread_count;
        }
        if (thread_count > MAX_THREADS)
        {
            runConnection( clientSocket );
            synchronized( this )
            {
                thread_count--;
            }
            
        }
        else
        {
            sockThread = new Thread()
            {

                @Override
                public void run()
                {
                    runConnection( clientSocket );

                    synchronized( this )
                    {
                        thread_count--;
                    }
                }
            };

            sockThread.start();
        }
    }
        
    public void runConnection(Socket clientSocket)
    {
        m_Stop = false;
        m_error = -1;
        m_Command = -1;

        try
        {
            // set the socket timeout
            clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            // get the client output stream
            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(), clientSocket.getSendBufferSize());
            // get the client input stream	
            clientReader = new BufferedInputStream(clientSocket.getInputStream(), clientSocket.getReceiveBufferSize());

            // connect to the real POP3 server
            serverSocket = new Socket(m_host, m_RemotePort);
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
            int commandCounter = 0;

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
                // write the answer to the POP client
                clientWriter.write(sData.getBytes());
                clientWriter.flush();
                
                // QUIT
                if (m_Command == POP_QUIT)
                {
                    break;
                }

                // reset the command
                m_Command = -1;

                // read the POP command from the client
                sData = getDataFromInputStream(clientReader).toString();

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
                
                while (sData.toUpperCase().startsWith("RETR "))
                {
                    m_Command = POP_RETR;
                    
                    // write it to the POP server
                    serverWriter.write(sData.getBytes());
                    serverWriter.flush();

                    if (RETR() > 0)
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
                    
                    sData = getDataFromInputStream(clientReader).toString();
                    Main.debug_msg(1, "C: " + sData);

                    
                    // verify if the user stopped the thread
                    if (m_Stop)
                    {
                        break;
                    }
                } 

                // write it to the POP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();

                if (sData.toUpperCase().startsWith("LIST"))
                {
                    m_Command = POP_LIST;
                } 
                else if (sData.toUpperCase().startsWith("QUIT"))
                {
                    m_Command = POP_QUIT;
                }                               

            }  // while

            closeConnections();

            System.gc();

        } catch (UnknownHostException uhe)
        {
            String msgerror = "Verify if you are connected to the internet or " + " if the POP server '" + m_host + "' exists.";
            Common.showError(msgerror);
            Main.err_log(msgerror);
        } catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
    }  // handleConnection

    private void closeConnections()
    {
        // close the connections
        Main.info_msg("Closing Connection...");
        try
        {
            if (serverWriter != null)
            {
                serverWriter.close();
                serverWriter = null;
            }

            if (serverReader != null)
            {
                serverReader.close();
                serverReader = null;
            }

            if (clientWriter != null)
            {
                clientWriter.close();
                clientWriter = null;
            }

            if (clientWriter != null)
            {
                clientWriter.close();
                clientWriter = null;
            }

        } catch (IOException iex)
        {
            Main.err_log(iex.getMessage());
        }

    }

    private StringBuffer getDataFromInputStream(BufferedInputStream reader)
    {
        int charRead = 0;									// char read
        final int MAX_BUF = 8192;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        StringBuffer output = new StringBuffer("");		// output string

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

            // read the stream until EOF (-1)
            while (charRead != -1)
            {
                // read the buffer
                charRead = reader.read(buffer);
                if (charRead > -1)
                {
                    // append to the output string
                    output.append(new String(buffer, 0, charRead));
                }
            }

            // no data returned
            if (output.length() == 0)
            {
                m_error = ERROR_NO_ANSWER;
            }

        } catch (SocketTimeoutException ste)
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
                    Main.debug_msg(1, "timeout. Trying again [" + m_retries + "]");
                    output = getDataFromInputStream(reader);
                }
            }

        } catch (Exception e)
        {
            // reader failed
            m_error = ERROR_UNKNOWN;
            Main.err_log(e.getMessage());
        }

        // errors found
        if (m_error < 0)
        {
            output = processMessage(output);
        }

        try
        {
            // return to the normal timeout (faster answer)
            if (m_retries > 0)
            {
                m_retries = 0;
                serverSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
            }
        } catch (Exception ex)
        {
            Main.err_log(ex.getMessage());
        }

        return output;

    }  // getDataFromInputStream

    private String getErrorMessage()
    {
        switch (m_error)
        {
            case ERROR_TIMEOUT_EXCEEDED:
                return "-ERR timeout exceeded";
            case ERROR_NO_ANSWER:
                return "-ERR no answer";
            case ERROR_UNKNOWN:
                return "-ERR unknown error";
        }
        return null;
    }

    private StringBuffer processMessage(StringBuffer sData)
    {
        switch (m_Command)
        {            
            case POP_LIST:
                sData = ensureEndOfMessage(sData);
        }

        return sData;
    }

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

    private int RETR()
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

                //Main.sleep(10);
                int avail = clientReader.available();
                
                if (avail > buffer.length + 3)
                {
                    rlen = clientReader.read(buffer);
                }
                else
                {
                    if (avail > 3)
                    {
                        rlen = clientReader.read(buffer, 0, avail -3);
                    }
                    else if (avail > 0)
                    {
                        rlen = clientReader.read(buffer, 0, avail);
                    }
                    else
                    {
                        rlen = clientReader.read(buffer, 0, 3);
                    }
                }

                if (rlen > 0 && rlen <= 3)
                {
                    int last_idx = rlen-1;
                    byte b = buffer[last_idx];
                    if (b == '\n' || b == '\r')
                    {
                        last_idx--;
                        b = buffer[last_idx];
                        if (b == '\n' || b == '\r')
                        {
                            last_idx--;
                            b = buffer[last_idx];
                        }
                        if (b == '.')                    
                            finished = true;
                            
                    }
                }
                
//                String line = serverReader.readLine();

                
                if (line != null)
                {
                    clientWriter.write( line );
                    clientWriter.write( "\r\n" );
                    
                    if (line.length() == 1 && line.charAt(0) == '.')
                    {
                        clientWriter.flush();
                        finished = true;
                    }
                    
                    if (bos != null)
                    {
                        try
                        {
                            bos.write(line.getBytes());
                            bos.write("\r\n".getBytes());
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
	    return m_error;

    }

    public static void StopServer()
    {
        m_Stop = true;
    }
}  // POP3connection

