/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;

/**
 *
 * @author Administrator
 */
public abstract class MailConnection 
{
    
    static final int SOCKET_TIMEOUT[] = {400, 800, 1200, 1800, 2500, 3000, 6000, 10000};
    final static byte END_OF_MULTILINE[] = {'\r','\n', '.', '\r','\n' };
    final static byte END_OF_LINE[] = {'\r','\n'};
    
    // variables    
    String m_host;							// host name or IP adsress
    int m_RemotePort = 110;					// port to connect
    
    
    static final int ERROR_TIMEOUT_EXCEEDED = 1;
    static final int ERROR_NO_ANSWER = 2;
    static final int ERROR_UNKNOWN = 3;
    
    
    BufferedInputStream serverReader;			// server reader
    BufferedInputStream clientReader;
    BufferedOutputStream clientWriter;
    BufferedOutputStream serverWriter;
    
    int m_error;							// stores the last error
    Socket serverSocket;					// server socket
    Socket clientSocket;					// server socket
    
    static int m_retries;					// numbers of retries to read a message from the socket
    int m_Command;							// actual POP command
    static boolean m_Stop;					// stop the thread
    
    static final int POP_RETR = 1;
    static final int POP_MULTILINE = 2;
    static final int SMTP_MULTILINE = 2;   
    static final int POP_SINGLELINE = 4;
    static final int SMTP_SINGLELINE = 4;   
    static final int SMTP_DATA = 1;
    static final int POP_QUIT = 3;
    static final int SMTP_QUIT = 3;
    

     
    int this_thread_id = 0;
    private static final int MAX_THREADS = 50;
    
    
    //abstract StringBuffer processMessage( StringBuffer msg );
    abstract void inc_thread_count();
    abstract int get_thread_count();
    
    abstract void dec_thread_count();
    abstract void runConnection(final Socket clientSocket);
    abstract String[] get_single_line_commands();
    abstract String[] get_multi_line_commands();
    
    static Semaphore mtx = new Semaphore(MAX_THREADS);
    
    
    
    protected MailConnection(String host, int RemotePort)
    {        
        m_host = host;
        m_RemotePort = RemotePort;
        
        m_Stop = false;
        m_error = -1;
        m_Command = -1;
        
    }    
    
    boolean is_command_multiline(String sData)
    {        
        if (sData.length() > 5)
            sData = sData.substring(0, 5 );
     
        sData = sData.toUpperCase().replace('\r', ' ').replace('\n', ' ').trim();
        
        String[] cmds = get_multi_line_commands();
        for (int i = 0; i < cmds.length; i++)
        {
            if (cmds[i].compareTo(sData) == 0)
                return true;
        }
                        
        return false;
    }
 
    boolean is_command_singleline(String sData)
    {        
        if (sData.length() > 5)
            sData = sData.substring(0, 5 );
     
        sData = sData.toUpperCase().replace('\r', ' ').replace('\n', ' ').trim();
        
        String[] cmds = get_single_line_commands();        
        for (int i = 0; i < cmds.length; i++)
        {
            if (cmds[i].compareTo(sData) == 0)
                return true;
        }
                        
        return false;
    }
    
    
    public void handleConnection(final Socket clientSocket)
    {
        Thread sockThread;

        // runs the server
        synchronized (mtx)
        {
            inc_thread_count();
            this_thread_id = get_thread_count();
        }

        if (get_thread_count() > MAX_THREADS)
        {
            runConnection(clientSocket);
            synchronized (mtx)
            {
                dec_thread_count();
            }
        }
        else
        {

            sockThread = new Thread()
            {

                @Override
                public void run()
                {

                    runConnection(clientSocket);

                    synchronized (mtx)
                    {
                        dec_thread_count();
                    }
                }
            };

            sockThread.start();
        }
    }
    
    
    void closeConnections()
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
    
    boolean has_eol( StringBuffer output )
    {
        if ( output.length() < END_OF_LINE.length)
            return false;
        
        int start = output.length() - END_OF_LINE.length;
        int i = 0;
        for (i = 0; i < END_OF_LINE.length; i++)
        {
            if (output.charAt(i + start) != END_OF_LINE[i])
                break;
        }
        if ( i == END_OF_LINE.length)
            return true;
        
        return false;        
    }
    boolean has_eol( byte buffer[], int rlen )
    {
        if (rlen < END_OF_LINE.length)
            return false;
        
        int start = rlen - END_OF_LINE.length;
        int i = 0;
        for (i = 0; i < END_OF_LINE.length; i++)
        {
            if (buffer[i + start] != END_OF_LINE[i])
                break;
        }
        
        if ( i == END_OF_LINE.length)
            return true;                        

        return false;        
    }
    boolean has_multi_eol( byte buffer[], int rlen )
    {
        if (rlen < END_OF_MULTILINE.length)
            return false;
        
        int start = rlen - END_OF_MULTILINE.length;
        int i = 0;
        for (i = 0; i < END_OF_MULTILINE.length; i++)
        {
            if (buffer[i + start] != END_OF_MULTILINE[i])
                break;
        }
        
        if ( i == END_OF_MULTILINE.length)
            return true;                        

        return false;        
    }
    boolean is_command_quit(String sData)
    {
        if (sData.length() >= 4)
        {
            if (sData.substring(0,4).toUpperCase().compareTo("QUIT") == 0)
            {
                return true;
            }
        }
        return false;
    }

    
    
    StringBuffer getDataFromInputStream(BufferedInputStream reader)
    {
        return getDataFromInputStream( reader, m_Command );
    }
    
    
    StringBuffer getDataFromInputStream(BufferedInputStream reader, int command_type)
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

                int avail = reader.available();

                if (command_type == POP_MULTILINE || command_type == SMTP_MULTILINE)
                {
                    if (avail > buffer.length + END_OF_MULTILINE.length)
                    {
                        rlen = reader.read(buffer);
                    }
                    else
                    {
                        if (avail > END_OF_MULTILINE.length)
                        {
                            rlen = reader.read(buffer, 0, avail - END_OF_MULTILINE.length);
                        }
                        else
                        {
                            rlen = reader.read(buffer, 0, END_OF_MULTILINE.length);
                        }
                    }

                    if (rlen == END_OF_MULTILINE.length)
                    {
                        if (has_multi_eol( buffer, rlen )) 
                            finished = true;
                    }
                }                
                else if (command_type == POP_SINGLELINE || command_type == SMTP_SINGLELINE)
                {
                    rlen = reader.read(buffer);
                    if (has_eol( buffer, rlen ) && reader.available() == 0) 
                        finished = true;                        
                }
                else  // UNKNOWN COMMAND
                {
                    rlen = reader.read(buffer);
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
                        Main.debug_msg(1, "timeout. Trying again [" + m_retries + "]");
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
                Main.err_log(e.getMessage());
            }            
        }
        
        if (!finished)
        {
            m_error = ERROR_NO_ANSWER;
            Main.err_log("Aborted answer: " + output.toString());
        }
        
        return output;
    }    
    /*
    StringBuffer _getDataFromInputStream(BufferedInputStream reader)
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
    */

    String getErrorMessage()
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

    public static void StopServer()
    {
        m_Stop = true;
    }
    
    

}