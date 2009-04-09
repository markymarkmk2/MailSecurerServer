/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;

/**
 *
 * @author Administrator
 */
public abstract class MailConnection 
{
    
    static final int SOCKET_TIMEOUT[] = {60000, 60000};
//    static final int SOCKET_TIMEOUT[] = {400, 800, 1200, 1800, 2500, 3000, 6000, 10000, 45000};
    final static byte END_OF_MULTILINE[] = {'\r','\n', '.', '\r','\n' };
    final static byte END_OF_LINE[] = {'\r','\n'};
    
    // variables    
/*    String m_host;							// host name or IP adsress
    int m_RemotePort = 110;					// port to connect
    */
    
    static final int ERROR_TIMEOUT_EXCEEDED = 1;
    static final int ERROR_NO_ANSWER = 2;
    static final int ERROR_UNKNOWN = 3;
    
    
    InputStream serverReader;			// server reader
    InputStream clientReader;
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
    static final int SMTP_MULTILINE = 4;   
    static final int POP_SINGLELINE = 3;
    static final int SMTP_SINGLELINE = 5;   
    static final int SMTP_DATA = 1;
    static final int POP_QUIT = 6;
    static final int SMTP_QUIT = 7;
    static final int SMTP_CLIENTREQUEST = 8;
    

     
    int this_thread_id = 0;
    private static final int MAX_THREADS = 50;
    
    
    //abstract StringBuffer processMessage( StringBuffer msg );
    abstract void inc_thread_count();
    abstract int get_thread_count();
    
    abstract void dec_thread_count();
    abstract void runConnection(final Socket clientSocket);
    abstract String[] get_single_line_commands();
    abstract String[] get_multi_line_commands();
    abstract public int get_default_port();
    
    static Semaphore mtx = new Semaphore(MAX_THREADS);
    
    ProxyEntry pe;
    
    MailConnection(ProxyEntry _pe)
    {                 
        pe = _pe;
  /*      m_host = pe.getHost();
        m_RemotePort = pe.getRemotePort();
    */    
        m_Stop = false;
        m_error = -1;
        m_Command = -1;        
    }    
    
    boolean is_connected()
    {
        if (clientSocket == null)
            return true;
    
        return !clientSocket.isClosed();
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
            pe.incInstanceCnt();
        }

        log(2, "Opening Connection");
        
        if (get_thread_count() > MAX_THREADS)
        {
            runConnection(clientSocket);
            synchronized (mtx)
            {
                dec_thread_count();
                pe.decInstanceCnt();
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
                        pe.decInstanceCnt();
                    }
                }
            };

            sockThread.start();
        }
    }
    
    
    void closeConnections()
    {
        // close the connections
        log(2, "Closing Connection");
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
            if (clientSocket != null&& clientSocket.isConnected())
            {
                clientSocket.close();
            }
            if (serverSocket != null&& serverSocket.isConnected())
            {
                serverSocket.close();
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
        {
            log( 4, "Detected EOL" );            
            return true;              
        }
        
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
        {
            log( 4, "Detected EOL" );            
            return true;              
        }

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
        {
            log( 4, "Detected MEOL" );
            return true;              
        }

        return false;        
    }
    boolean is_command_quit(String sData)
    {
        if (sData.length() >= 4)
        {
            if (sData.substring(0,4).toUpperCase().compareTo("QUIT") == 0)
            {
                log( 4, "Detected QUIT" );
                return true;
            }
        }
        return false;
    }

    void log( String txt )
    {
        if (txt.endsWith("\r\n"))
            txt = txt.substring(0, txt.length() - 2);
        Main.debug_msg(2, pe.getProtokollStr() + " " + this.this_thread_id + ": " + txt);
    }
    void log( int dbg, String txt )
    {
        if (txt.endsWith("\r\n"))
            txt = txt.substring(0, txt.length() - 2);
        
        Main.debug_msg(dbg, pe.getProtokollStr() + " " + this.this_thread_id + ": " + txt);
    }

    
    
    StringBuffer getDataFromInputStream(InputStream reader)
    {
        return getDataFromInputStream( reader, m_Command );
    }
    
    
    StringBuffer getDataFromInputStream(InputStream reader, int command_type)
    {
        final int MAX_BUF = 8192;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        StringBuffer output = new StringBuffer("");		// output string

        boolean finished = false;
        int rlen = 0;
                
        int avail = 0;
        
        // WAIT TEN SECONDS (100*100ms) FOR DATA
        int maxwait = 100;
        while (avail == 0 && maxwait > 0)
        {
            try 
            {
                avail = reader.available();
            }
            catch (Exception exc)
            {
            }
            if (avail > 0)
                break;
            Main.sleep(100);
            maxwait--;
        }
        
        if (maxwait <= 0)
            Main.err_log("Timeout while waiting for Server" );
        
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
                

                if (command_type == POP_MULTILINE)
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
                            rlen = reader.read(buffer, 0, avail);
                        }
                    }
                    avail = reader.available();

                    if (rlen == END_OF_MULTILINE.length)
                    {
                        if (has_multi_eol( buffer, rlen )) 
                            finished = true;
                    }
                    // DETECT ERR ON MULTILINE ANSWER
                    if (buffer[0] == '-' && buffer[1] == 'E')
                    {                                                
                        if (avail > 0)
                        {
                            rlen += reader.read(buffer, rlen, avail );
                            avail = reader.available();
                        }
                        // END OF LINE IS ENOUGH
                        if ( has_eol( buffer, rlen ) && avail == 0) 
                            finished = true;    
                    }
                }                
                else if (command_type == POP_SINGLELINE || command_type == SMTP_CLIENTREQUEST || command_type == SMTP_DATA)
                {
                    rlen = reader.read(buffer);
                    if (has_eol( buffer, rlen ) && reader.available() == 0) 
                        finished = true;                        
                }
                else  // UNKNOWN COMMAND
                {
                    rlen = reader.read(buffer);
                }   
                avail = reader.available();
                
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
                
                if ( command_type == SMTP_MULTILINE || command_type == SMTP_SINGLELINE)
                {
                    // CHECK IF WE HAVE LAST LINE OF MULTILINE SMTP
                    int idx = output.length() - 2;
                    while (idx > 0 && output.charAt(idx) != '\n' )
                    {
                        idx--;
                    }
                    if (idx == 0)
                        idx--;
                    
                    idx += 4;
                    if (idx < output.length())
                    {
                        char ch_after_code = output.charAt(idx);
                        if (ch_after_code == ' ' || ch_after_code == '\r')
                            finished = true;
                    }                    
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
                        log(1, "Mail timeout. Trying again [" + m_retries + "]");
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
    
    
    protected int get_multiline_proxy_data(InputStream Reader, OutputStream Writer, File rfc_dump, BufferedOutputStream bos)
    {
        // NOW MOVE DATA FROM READER TO WRITERCLIENT TO SERVER 
        boolean finished = false;

        final int MAX_BUF = 2048;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
      
        int avail = 0;
        
        // WAIT TEN SECONDS (100*100ms) FOR DATA
        int maxwait = 100;
        while (avail == 0 && maxwait > 0)
        {
            try 
            {
                avail = Reader.available();
            }
            catch (Exception exc)
            {
            }
            if (avail > 0)
                break;
            Main.sleep(100);
            maxwait--;
        }
        
        if (maxwait <= 0)
            Main.err_log("Timeout while waiting for Server" );
        
        long dgb_level = Main.get_long_prop(Preferences.DEBUG);
        byte[] last_4 = new byte[4];
        
        
        int rlen = 0;
        while (!finished && m_error <= 0)
        {
            try
            {

                // verify if the user stopped the thread
                if (m_Stop)
                {
                    return 1;
                }
                       
                // WAIT FOR DATA, WE ARE TOO FAST
                maxwait = 10;
                while (avail == 0 && maxwait > 0)
                {
                    try 
                    {
                        avail = Reader.available();
                    }
                    catch (Exception exc)
                    {
                    }
                    if (avail > 0)
                        break;
                    Main.sleep(1000);
                    maxwait--;
                }
                
                
                if (avail > buffer.length + END_OF_MULTILINE.length)
                {
                    rlen = Reader.read(buffer);
                    if (dgb_level > 3)
                    {
                        log(4, new String(buffer, 0, rlen));
                    }
                    
                }
                else
                {
                    if (avail > END_OF_MULTILINE.length)
                    {
                        rlen = Reader.read(buffer, 0, avail - END_OF_MULTILINE.length);
                        if (dgb_level > 3)
                        {
                            log(4, new String(buffer, 0, rlen));
                        }                        
                    }
                    else
                    {
                        rlen = Reader.read(buffer, 0, avail);
                        if (dgb_level > 3)
                            log( 4, new String( buffer, 0, rlen ) );
                        
                        if (rlen < END_OF_MULTILINE.length)
                        {
                            String str = new String( last_4 );
                            str += new String( buffer, 0, rlen );
                            if (has_multi_eol(str.getBytes(), str.length()))
                            {
                                finished = true;
                            }
                        }
                        
                        if (!finished && rlen < END_OF_MULTILINE.length)
                        {
                            log(1, "Gathering slow data");
                            // WAIT FOR ANY REMAINING DATA
                            Main.sleep(5000);
                            avail = Reader.available();
                            
                            while (avail > 0 && (rlen + avail) <= buffer.length)
                            {
                                rlen += Reader.read(buffer, rlen, avail);
                                if (dgb_level > 3)
                                    log( 4, new String( buffer, 0, rlen ) );
                                
                                Main.sleep(2000);
                                avail = Reader.available();
                            }

                            // CHECK IF NO DATA AND NO MEOL
                            if (avail == 0 && !has_multi_eol( buffer, rlen ))
                            {
                                String str = new String( last_4 ).substring(1);
                                if (str.compareTo("\r\n.") == 0)
                                {
                                    log(1, "Detected short end of message, ignoring error");
                                    finished = true;
                                }
                                else
                                {
                                    log(1,"Protokoll Error in get_multiline_proxy_data, rlen:" + rlen + " avail:" + avail + " stillalavail:" + Reader.available() );
                                    log(1, "Detected short end of message, ignoring error");
                                    finished = true;
/*                                    m_error = ERROR_UNKNOWN;
                                    return m_error;                                
 */
                                }
                            }
                        }
                    }
                }

                avail = Reader.available();
                
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
                    
                    // SAVE LAST 4 BYTE
                    int start_idx = 0;
                    if (rlen > 4)
                    {
                        start_idx = rlen - 4;
                    }
                        
                    for (int i = start_idx; i < rlen; i++)
                    {
                        last_4[i - start_idx] = buffer[i];
                    }
                            
                    
                    Writer.write(buffer, 0, rlen);
                    
                  
                    if (finished)
                    {
                        Writer.flush();
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
                            serverSocket.setSoTimeout(SOCKET_TIMEOUT[m_retries]);
                        }
                        catch (Exception exc)
                        {
                        }
                        log(1, "Timeout. Trying again [" + m_retries + "]");
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
        
        if (finished)
        {
            return 0;
        }
        
        if (m_error > 0)
            return m_error;
        
        return -1;

    }
    

}
