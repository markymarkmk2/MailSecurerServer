/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import javax.net.ssl.SSLSocket;

/**
 *
 * @author mw
 */
public abstract class ProxyConnection implements Runnable
{

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

    // OVERALL WATCHDOG
    protected static int ACTIVITY_TIMEOUT = 120;

    static final int SOCKET_TIMEOUT[] = {30000, 60000};
    final static byte END_OF_MULTILINE[] = {'\r','\n', '.', '\r','\n' };
    final static byte END_OF_MULTILINE_QUIT[] = {'\r','\n', '.', '\r','\n','Q','U','I','T','\r','\n' };
    final static String END_OF_MULTILINE_STR = "\r\n.\r\n";
    final static String END_OF_MULTILINE_QUIT_STR = "\r\n.\r\nQUIT\r\n";
    final static byte END_OF_LINE[] = {'\r','\n'};
    final static String END_OF_LINE_STR = "\r\n";


    static final int ERROR_TIMEOUT_EXCEEDED = 1;
    static final int ERROR_NO_ANSWER = 2;
    static final int ERROR_UNKNOWN = 3;

    InputStream serverReader;			// server reader
    InputStream clientReader;
    OutputStream clientWriter;
    OutputStream serverWriter;

    int m_error;							// stores the last error
    Socket serverSocket;					// server socket
    Socket clientSocket;					// server socket

    static int m_retries;					// numbers of retries to read a message from the socket
    						// actual POP command

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
    

    //abstract StringBuffer processMessage( StringBuffer msg );
    abstract void inc_thread_count();
    abstract int get_thread_count();

    abstract void dec_thread_count();
    abstract void runConnection();
    abstract String[] get_single_line_commands();
    abstract String[] get_single_line_with_args_commands();
    abstract String[] get_multi_line_commands();
    abstract public int get_default_port();

    long last_activity;

    String last_command;

    
   // static final Semaphore sem = new Semaphore(MAX_THREADS);
    static final String mtx = new String();

    ProxyEntry pe;

    @Override
    public String toString()
    {
        if (clientSocket != null && clientSocket.isConnected())
            return this.getClass().getSimpleName() + ": " + clientSocket.getRemoteSocketAddress().toString();

        return this.getClass().getSimpleName();
    }



    ProxyConnection(ProxyEntry _pe, Socket _clientSocket)
    {
        pe = _pe;       
        m_error = -1;
        clientSocket = _clientSocket;
        pe.set_connection( this );
        reset_timeout();

        
    }

    public boolean is_connected()
    {
        if (clientSocket == null)
            return false;

        return !clientSocket.isClosed();
    }
    public ProxyEntry get_proxy()
    {
        return pe;
    }
    boolean has_args( String sData)
    {
        int idx = sData.indexOf(' ');
        {
            if (idx > 3 && sData.length() > 5 && !Character.isWhitespace( sData.charAt(5) ))
                return true;
        }
        return false;
    }

    boolean is_command_multiline(String sData)
    {
        String orig_data = sData;



        if (sData.length() > 5)
            sData = sData.substring(0, 5 );

        sData = sData.toUpperCase().replace('\r', ' ').replace('\n', ' ').trim();

        // HANDLE SINGLE LINE WITH ARGS / MULTILINE W/O ARGS
        if (!has_args(orig_data))
        {
            String[] cmds = get_single_line_with_args_commands();
            for (int i = 0; i < cmds.length; i++)
            {
                if (cmds[i].compareTo(sData) == 0)
                {
                    return true;
                }
            }
        }

        String[] cmds = get_multi_line_commands();
        for (int i = 0; i < cmds.length; i++)
        {
            if (cmds[i].compareTo(sData) == 0)
            {               
                return true;
            }
        }

        return false;
    }

    boolean is_command_singleline(String sData)
    {
        String orig_data = sData;
        
        if (sData.length() > 5)
            sData = sData.substring(0, 5 );

        sData = sData.toUpperCase().replace('\r', ' ').replace('\n', ' ').trim();

        // HANDLE SINGLE LINE WITH ARGS / MULTILINE W/O ARGS
        if (has_args(orig_data))
        {
            String[] cmds = get_single_line_with_args_commands();
            for (int i = 0; i < cmds.length; i++)
            {
                if (cmds[i].compareTo(sData) == 0)
                {
                    return true;
                }
            }
        }

        String[] cmds = get_single_line_commands();
        for (int i = 0; i < cmds.length; i++)
        {
            if (cmds[i].compareTo(sData) == 0)
            {
                if (sData.compareTo("UIDL") == 0 || sData.compareTo("LIST") == 0)
                {
                    if (!has_args(orig_data))
                    {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public void run()
    {
        runConnection();

        synchronized (mtx)
        {
            dec_thread_count();
            pe.decInstanceCnt();
        }        
    }


    public void handleConnection(ExecutorService connect_thread_pool)
    {
        //Thread sockThread;


        // runs the server
        synchronized (mtx)
        {
            inc_thread_count();
            this_thread_id = get_thread_count();
            pe.incInstanceCnt();
        }

        log( Main.Txt("Opening_Connection"));

        connect_thread_pool.execute(this);
        

        /*
        if (get_thread_count() > MAX_THREADS)
        {
            runConnection();
            synchronized (mtx)
            {
                dec_thread_count();
                pe.decInstanceCnt();
            }
        }
        else
        {

            sockThread = new Thread(toString())
            {

                @Override
                public void run()
                {

                    runConnection();

                    synchronized (mtx)
                    {
                        dec_thread_count();
                        pe.decInstanceCnt();
                    }
                }
            };

            sockThread.start();
        }*/
    }


    public void closeConnections()
    {
        // close the connections
        log( get_description() + ": " + Main.Txt("Closing_Connection"));
        try
        {
            if (serverWriter != null)
            {
                serverWriter.close();                
            }

            if (serverReader != null)
            {
                serverReader.close();
            }

            if (clientWriter != null)
            {
                clientWriter.close();
            }

            if (clientWriter != null)
            {
                clientWriter.close();
            }
            if (clientSocket != null)
            {
                clientSocket.close();
            }
            if (serverSocket != null)
            {
                serverSocket.close();
            }

        } catch (IOException iex)
        {
            LogManager.msg_proxy(LogManager.LVL_ERR, Main.Txt("Error_while_closing_ProxyConnection"), iex);
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
            if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                log( Main.Txt("Detected_EOL") );
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
            if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                log( Main.Txt("Detected_EOL") );
            return true;
        }

        return false;
    }
    private boolean has_multi_eol( StringBuffer output )
    {
        if (output.length() < END_OF_MULTILINE.length)
            return false;

        String s = output.substring(output.length() - END_OF_MULTILINE.length);
        if (s.compareTo(END_OF_MULTILINE_STR) == 0)
            return true;

        return false;
    }

/*
 *    123-First line
      123-Second line
      123-234 text beginning with numbers
      123 The last line
*/
    private boolean has_smtp_eol( StringBuffer output )
    {
        if (output.length() < END_OF_LINE.length)
            return false;
        
        // MUST HAVE EOL
        if (!has_single_eol( output ))
            return false;
        
        
        // NOW FIND INDEX OF PREVIOUS LINE
        String start_txt = output.substring(0, output.length() - 2);
        int idx = start_txt.lastIndexOf(END_OF_LINE_STR);
        if (idx == -1)
            idx = 0;
        else
            idx += END_OF_LINE.length;

        // CHECK IF THIS LINE HAS SPCAE SEPARATOR OR NOTHING AFTER 3-DIGIT CODE
        if (idx + 3 > output.length())
            return true; // NO SPACE AFTER 3-DIGIT

        if (output.charAt(idx + 3) == ' ')
            return true;

        if (output.charAt(idx + 3) == '-')
            return false;

        LogManager.msg_proxy(LogManager.LVL_ERR, "Protocolerror on SMTP multiline: " + output.toString());


        return false;
    }

    private boolean has_single_eol( StringBuffer output )
    {
        if (output.length() < END_OF_LINE.length)
            return false;

        String s = output.substring(output.length() - END_OF_LINE.length);
        if (s.compareTo(END_OF_LINE_STR) == 0)
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
        {
            if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                log( Main.Txt("Detected_MEOL") );
            return true;
        }

        return false;
    }
    boolean has_multi_eol_quit( byte buffer[], int rlen )
    {
        if (rlen < END_OF_MULTILINE_QUIT.length)
            return false;

        int start = rlen - END_OF_MULTILINE_QUIT.length;
        int i = 0;
        for (i = 0; i < END_OF_MULTILINE_QUIT.length; i++)
        {
            if (buffer[i + start] != END_OF_MULTILINE_QUIT[i])
                break;
        }

        if ( i == END_OF_MULTILINE_QUIT.length)
        {
            if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                log( Main.Txt("Detected_MEOLQ") );
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
                if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                    log(  Main.Txt("Detected_QUIT") );
                return true;
            }
        }
        return false;
    }

    void log( String txt )
    {
        if (!LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_DEBUG))
            return;

        if (txt.endsWith("\r\n"))
            txt = txt.substring(0, txt.length() - 2);
        LogManager.msg_proxy(LogManager.LVL_DEBUG, get_description() + ": " + txt);
    }
    
    protected void reset_timeout()
    {
        last_activity = System.currentTimeMillis();
    }
    protected void disable_timeout()
    {
        last_activity = -1;
    }

    public boolean is_timeout()
    {
        if (last_activity == -1)
            return false;
        
        if ((System.currentTimeMillis()- last_activity)/1000 > ACTIVITY_TIMEOUT)
        {
            LogManager.msg_proxy(LogManager.LVL_WARN, "No activity for " + ACTIVITY_TIMEOUT + "s: " + get_description() + " last command:" + last_command );
            return true;
        }
        return false;
    }

/*
    StringBuffer getDataFromInputStream(InputStream reader, Socket sock, boolean wait)
    {
        return getDataFromInputStream( reader, sock, m_Command, wait );
    }
*/
    int wait_for_avail( InputStream reader, Socket sock, int s )
    {
        int maxwait = s * 10;
        int avail = 0;
        
        while (avail == 0 && maxwait > 0)
        {
            if (sock.isClosed() || sock.isInputShutdown() || !sock.isConnected())
                return 0;
            try
            {
                avail = reader.available();
            }
            catch (Exception exc)
            {
            }
            if (avail > 0)
            {
                reset_timeout();
                return avail;
            }
            Main.sleep(100);
            maxwait--;
        }
        return 0;
    }

    String get_description()
    {
        String ret =  pe.get_proxy().getType() + " " + this.this_thread_id;

        if (clientSocket != null && clientSocket.isConnected())
            ret += ": " + clientSocket.getRemoteSocketAddress().toString();

        return ret;
    }

    StringBuffer getDataFromInputStream(InputStream reader, Socket sock, int command_type, boolean wait)
    {
        final int MAX_BUF = 8192;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        StringBuffer output = new StringBuffer("");		// output string

        boolean finished = false;
        int rlen = 0;


        int orig_to = 0;
        try
        {
            orig_to = sock.getSoTimeout();
            sock.setSoTimeout((ACTIVITY_TIMEOUT - 5) * 1000);
        }
        catch (SocketException socketException)
        {
        }
        int empty_cnt = 0;

        while ( !finished && m_error < 0)
        {
            if (pe.is_finished())
                break;
            
            try
            {
                rlen = reader.read(buffer);
                if (rlen > 0)
                    reset_timeout();

                // DETECT BROKEN CONNECTION
                if (rlen <= 0)
                {
                    empty_cnt++;
                    LogicControl.sleep(500);
                }
                else
                {
                    empty_cnt = 0;
                }
                
                for (int i = 0; i < rlen; i++)
                {
                    output.append( (char) buffer[i]);
                }

                if (empty_cnt == 10)
                {
                    if (has_single_eol( output ))
                    {
                        finished = true;
                        break;
                    }
                    // 5 Seconds empty data
                    LogManager.msg_proxy(LogManager.LVL_ERR, "DataFromInputStream: " + get_description() + ": " + "no more data from connection, last command:" + last_command);
                    m_error = ERROR_UNKNOWN;
                    break;
                }

            }
            catch (SocketTimeoutException ste)
            {
                // COMMAND ENDED BY TIMEOUT, PROBABLY UNKNOWN, CHECK FOR EOL
                if (has_single_eol( output ))
                {
                    finished = true;
                    break;
                }
                LogManager.msg_proxy(LogManager.LVL_ERR, "DataFromInputStream: " + get_description() + ": " + ste.getMessage() + " last command:" + last_command + " last_recv:" + output.toString());
//                if (output.length() == 0)
//                {
                    m_error = ERROR_TIMEOUT_EXCEEDED;
//                }

            }
            catch (IOException e)
            {
                // reader failed
                m_error = ERROR_UNKNOWN;
                if (!finished)
                {
                    LogManager.msg_proxy(LogManager.LVL_ERR, "DataFromInputStream: " + get_description() + ": " + e.getMessage() + " last command:" + last_command);
                }
            }
            if (command_type == POP_MULTILINE)
            {
                if (rlen > 2 && buffer[0] == '-' && buffer[1] == 'E')
                    command_type = POP_SINGLELINE;
            }


            if (command_type == POP_MULTILINE)
            {
                if (has_multi_eol( output))
                {
                    finished = true;
                    break;
                }
            }
            else if (command_type == SMTP_MULTILINE || command_type == SMTP_SINGLELINE )
            {
                if (has_smtp_eol( output))
                {
                    finished = true;
                    break;
                }
            }
            else if (command_type == POP_SINGLELINE || command_type == SMTP_CLIENTREQUEST || command_type == SMTP_DATA)
            {
                if (has_single_eol( output))
                {
                    finished = true;
                    break;
                }
            }
            else
            {
                if (has_single_eol( output))
                {
                    finished = true;
                    break;
                }
            }
        }

        try
        {
            if (orig_to > 0)
            {
                sock.setSoTimeout(orig_to);
            }
        }
        catch (SocketException socketException)
        {
        }
        return output;
    }


    StringBuffer getDataFromInputStreamNoBlock(InputStream reader, Socket sock, int command_type, boolean wait)
    {
        final int MAX_BUF = 8192;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array
        StringBuffer output = new StringBuffer("");		// output string

        boolean finished = false;
        int rlen = 0;

        int avail = MAX_BUF;



        // WAIT TEN SECONDS (100*100ms) FOR DATA
        if (wait)
        {
             avail = wait_for_avail( reader, sock, 10 );
             if (avail == 0)
                LogManager.msg_proxy(LogManager.LVL_ERR, Main.Txt("Timeout_while_waiting_for_Server") + " getDataFromInputStream " + get_description() + " last command:" + last_command);
        }

        
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
                if (pe.is_finished())
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
                    reset_timeout();

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

                if (rlen > 0)
                    reset_timeout();


                // CHECK FOR STOPPED INPUT CONNECTION FROM APPLE MAIL
                avail = reader.available();
                if (rlen == 0 && avail == 0)
                {
                    if (wait_for_avail( reader, sock, 5 ) == 0)
                        rlen = -1;
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
                
                // IN CASE WE CANNOT DETECT AVAILABLE, WE BLOCK
/*                if (avail == 0)
                    avail = MAX_BUF;
*/
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
                        log( Main.Txt("Mail_timeout._Trying_again_[") + m_retries + "]");
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
                if (!finished)
                {
                    LogManager.msg_proxy(LogManager.LVL_ERR, "DataFromInputStream: " + get_description() + ": " + e.getMessage() + " last command:" + last_command);
                }
            }
        }

        if (!finished)
        {
            m_error = ERROR_NO_ANSWER;
            LogManager.msg_proxy(LogManager.LVL_ERR, Main.Txt("Aborted_answer:_") + output.toString());
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


    protected static final int DBG_VERB = 10;
    protected static final int DBG_DATA_VERB = 12;

    protected int get_multiline_proxy_data(InputStream Reader, OutputStream Writer, File rfc_dump, BufferedOutputStream bos)
    {
        // NOW MOVE DATA FROM READER TO WRITERCLIENT TO SERVER
        boolean finished = false;



        final int MAX_BUF = 2048;  						// buffer 8 Kb
        byte buffer[] = new byte[MAX_BUF];			// buffer array


        
        byte[] last_4 = new byte[4];


        int rlen = 0;
        while (!finished && m_error <= 0)
        {
            try
            {
                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    return 1;
                }

                rlen = Reader.read(buffer);


                if (rlen > 0)
                {

                    // DETECT EOM
                    if (rlen < END_OF_MULTILINE.length)
                    {
                        String str = new String( last_4 );
                        str += new String( buffer, 0, rlen );
                        if (has_multi_eol(str.getBytes(), str.length()))
                        {
                            finished = true;
                        }
                    }
                    else
                    {
                        if (has_multi_eol(buffer, rlen))
                        {
                            finished = true;
                        }
                        /*
                        // DETECT QUIT AFTER DATA PIPELINING
                        if (has_multi_eol_quit(buffer, rlen))
                        {
                            finished = true;
                            m_error = ERROR_QUIT_AFTER_DATA_PIPELINIG;
                        }*/
                    }



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
                    reset_timeout();


                    if (finished)
                    {
                        Writer.flush();
                    }

                    if (bos != null)
                    {
                        disable_timeout();
                        try
                        {
                            // WE FOUND \r\n.\r\n AFTER MESSAGE, OMIT THE END IN MSG
                            if (finished && rlen > 3 && buffer[rlen-3] == '.')
                                bos.write(buffer, 0, rlen - 3 );
                            else
                                bos.write(buffer, 0, rlen);
                        }
                        catch (Exception exc)
                        {
                            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
                            LogManager.msg_proxy(LogManager.LVL_ERR, "Cannot write to rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

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


                            if (Main.get_bool_prop(MandantPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
                            {
                                m_error = ERROR_UNKNOWN;
                                return m_error;
                            }
                        }
                        reset_timeout();
                    }
                }
                else
                {
                     m_error = ERROR_NO_ANSWER;                     
                }
            }
            catch (SocketTimeoutException ste)
            {
                if (rlen == 0)
                {
                    m_error = ERROR_TIMEOUT_EXCEEDED;
                    LogManager.msg_proxy(LogManager.LVL_ERR, "Timeout get_multiline_proxy_data: " + get_description() + ": " + ste.getMessage());
                }
            }
            catch (Exception e)
            {
                // reader failed
                m_error = ERROR_UNKNOWN;
                LogManager.msg_proxy(LogManager.LVL_ERR, "Exception get_multiline_proxy_data: " + get_description() + ": " + e.getMessage() + " last command:" + last_command);
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


    protected int get_multiline_proxy_data_no_block(InputStream Reader, OutputStream Writer, File rfc_dump, BufferedOutputStream bos)
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
            {
                reset_timeout();
                break;
            }
            Main.sleep(100);
            maxwait--;
        }

        if (avail <= 0)
        {
            LogManager.msg_proxy(LogManager.LVL_ERR, Main.Txt("Timeout_while_waiting_for_Server") + " start get_multiline_proxy_data " + get_description() + " last command:" + last_command );
            m_error = ERROR_UNKNOWN;
            return m_error;            
        }

     
        byte[] last_4 = new byte[4];


        int rlen = 0;
        while (!finished && m_error <= 0)
        {
            try
            {

                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    return 1;
                }

                // WAIT FOR DATA, WE ARE TOO FAST
                maxwait = 2;
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
                    {
                        reset_timeout();
                        break;
                    }
                    Main.sleep(1000);
                    maxwait--;
                }
                if (avail <= 0)
                {
                    LogManager.msg_proxy(LogManager.LVL_ERR, Main.Txt("Timeout_while_waiting_for_Server") + " get_multiline_proxy_data " + get_description() + " last command:" + last_command );
                    m_error = ERROR_UNKNOWN;
                    return m_error;
                }


                if (avail > buffer.length + END_OF_MULTILINE.length)
                {
                    rlen = Reader.read(buffer);
                    if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                    {
                        log( new String(buffer, 0, rlen));
                    }
                    reset_timeout();
                }
                else
                {
                    if (avail > END_OF_MULTILINE.length)
                    {
                        rlen = Reader.read(buffer, 0, avail - END_OF_MULTILINE.length);
                        if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                        {
                            log( new String(buffer, 0, rlen));
                        }
                        reset_timeout();
                    }
                    else
                    {
                        rlen = Reader.read(buffer, 0, avail);
                        if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                            log(  new String( buffer, 0, rlen ) );

                        if (rlen < END_OF_MULTILINE.length)
                        {
                            String str = new String( last_4 );
                            str += new String( buffer, 0, rlen );
                            if (has_multi_eol(str.getBytes(), str.length()))
                            {
                                finished = true;
                            }
                        }
                        reset_timeout();

                        if (!finished && rlen < END_OF_MULTILINE.length)
                        {
                            log( Main.Txt("Gathering_slow_data"));
                            // WAIT FOR ANY REMAINING DATA
                            Main.sleep(5000);
                            avail = Reader.available();

                            while (avail > 0 && (rlen + avail) <= buffer.length)
                            {
                                reset_timeout();
                                rlen += Reader.read(buffer, rlen, avail);
                                if (LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE))
                                    log(  new String( buffer, 0, rlen ) );

                                Main.sleep(2000);
                                avail = Reader.available();
                            }

                            // CHECK IF NO DATA AND NO MEOL
                            if (avail == 0 && !has_multi_eol( buffer, rlen ))
                            {
                                String str = new String( last_4 ).substring(1);
                                if (str.compareTo("\r\n.") == 0)
                                {
                                    log( Main.Txt("Detected_short_end_of_message,_ignoring_error"));
                                    finished = true;
                                }
                                else
                                {
                                    LogManager.msg_proxy(LogManager.LVL_WARN, "Protokoll Error in get_multiline_proxy_data, rlen:" + rlen + " avail:" + avail + " stillalavail:" + Reader.available() );
                                    LogManager.msg_proxy(LogManager.LVL_WARN,  "Detected short end of message, ignoring error");
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
                
                // IN CASE WE CANNOT DETECT AVAILABLE, WE BLOCK
                /*if (avail == 0)
                    avail = MAX_BUF;
*/

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
                    reset_timeout();


                    if (finished)
                    {
                        Writer.flush();
                    }

                    if (bos != null)
                    {
                        disable_timeout();
                        try
                        {
                            bos.write(buffer, 0, rlen);
                        }
                        catch (Exception exc)
                        {
                            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
                            LogManager.msg_proxy(LogManager.LVL_ERR, "Cannot write to rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

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


                            if (Main.get_bool_prop(MandantPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
                            {
                                m_error = ERROR_UNKNOWN;
                                return m_error;
                            }
                        }
                        reset_timeout();
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
                    LogManager.msg_proxy(LogManager.LVL_ERR, ex.getMessage());
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
                        LogManager.msg_proxy(LogManager.LVL_WARN, "Timeout. Trying again [" + m_retries + "]");
                    }
                }
            }
            catch (Exception e)
            {
                // reader failed
                m_error = ERROR_UNKNOWN;
                LogManager.msg_proxy(LogManager.LVL_ERR, "get_multiline_proxy_data: " + get_description() + ": " + e.getMessage() + " last command:" + last_command);
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

    int read_one_line( InputStream reader, byte[] first_line )
    {
        int i = 0;
        for (i = 0; i < first_line.length; i++)
        {
            byte b = 0;
            try
            {
                first_line[i] = (byte) reader.read();
                reset_timeout();
            }
            catch (IOException iOException)
            {
                return 0;
            }

            b = first_line[i];
            if (i >= 2)
            {
                if ( first_line[i-1] == '\r' && first_line[i] == '\n')
                    break;
                if ( first_line[i-1] == '\n' && first_line[i] == '\r')
                    break;
            }
            
        }
        // I IS INDEX, WE NEDD LENGTH
        if (i < first_line.length)
            i++;

        return i;
    }

    void close()
    {
        closeConnections();
    }





}

