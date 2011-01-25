package dimm.home.importmail;

import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.workers.MailProxyServer;
import home.shared.Utilities.DefaultSSLSocketFactory;
import home.shared.mail.RFCGenericMail;
import java.net.Socket;
import java.net.UnknownHostException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import javax.net.SocketFactory;

public class POP3Connection extends ProxyConnection
{

    private static final String NAME = "POP3-Proxy";

    // UIDL IS BOTH SINGLE AND MULTILINE (WITHOUT ARGS ->MULTI)
    String multi_line_commands[] = {"LIST", "RETR", "UIDL", "TOP", "CAPA"};
    String single_line_commands[] = {"QUIT", "USER", "UIDL", "PASS", "DELE", "STAT", "NOOP", "RSET", "APOP", "STLS", "AUTH"};

    static int thread_count = 0;

    public POP3Connection(ProxyEntry pe,Socket s)
    {
        super( pe, s );
    }
    
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

     
 
   
    @Override
    public void runConnection()
    {
        boolean do_quit = false;
        m_error = -1;
        int m_Command = POP_SINGLELINE;
                                    

        try
        {
            
/*            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(), clientSocket.getSendBufferSize());
            clientReader = new BufferedInputStream(clientSocket.getInputStream(), clientSocket.getReceiveBufferSize());
*/
            clientWriter = clientSocket.getOutputStream();
            clientReader = clientSocket.getInputStream();

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
         /*
            Main.debug_msg(DBG_VERB, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(DBG_VERB, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(DBG_VERB, "getSoTimeout: " + serverSocket.getSoTimeout());
*/
/*            serverWriter = new BufferedOutputStream(serverSocket.getOutputStream(), serverSocket.getSendBufferSize());
            serverReader = new BufferedInputStream(serverSocket.getInputStream(), serverSocket.getReceiveBufferSize());
  */
            serverWriter = serverSocket.getOutputStream();
            serverReader = serverSocket.getInputStream();

            String sData = "";

            // THE FIRST RESPONSE FROM SERVER IS SINGLE LINE
            m_Command = POP_SINGLELINE;
            boolean is_verbose = LogManager.has_lvl(LogManager.TYP_PROXY, LogManager.LVL_VERBOSE);
            boolean server_wait_input = pe.isSSL() ? false : true;
            
            
            while (true)
            {

                // read the answer from the server
                if (is_verbose)
                    log(  Main.Txt("Waiting_for_Server..."));
                sData = getDataFromInputStream(serverReader, serverSocket, m_Command, /*wait*/ server_wait_input).toString();

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
                        LogManager.msg_proxy(LogManager.LVL_WARN,  "Error : " + getErrorMessage());
                        clientWriter.write(getErrorMessage().getBytes());
                        clientWriter.flush();
                    }
                    break;
                }


                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    break;
                }

                if (is_verbose)
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
                m_Command = POP_SINGLELINE;

                if (is_verbose)
                    log(  Main.Txt("Waiting_for_Client..."));
                
                // read the POP command from the client
                sData = getDataFromInputStream(clientReader, clientSocket, POP_SINGLELINE, /*waot*/ false).toString();
                last_command = sData;

                // verify if the user stopped the thread
                if (pe.is_finished())
                {
                    break;
                }

                // if the reader failed, exit
                if (m_error > 0)
                {
                    break;
                }

                if (is_verbose)
                    log( "C: " + sData);
                
                while (sData.toUpperCase().startsWith("RETR "))
                {
                    m_Command = POP_RETR;
                    
                    // write it to the POP server
                    serverWriter.write(sData.getBytes());
                    serverWriter.flush();
                    reset_timeout();

                    if (RETRBYTE() > 0)
                    {
                        clientWriter.write(getErrorMessage().getBytes());
                        clientWriter.flush();                        
                        break;
                    }      
                    // verify if the user stopped the thread
                    if (pe.is_finished())
                    {
                        break;
                    }
                    
                    sData = getDataFromInputStream(clientReader, clientSocket, POP_SINGLELINE, /*wait*/ false).toString();
                    if (is_verbose)
                        log( "C: " + sData);

                    
                    // verify if the user stopped the thread
                    if (pe.is_finished())
                    {
                        break;
                    }
                } 

                // write it to the POP server
                serverWriter.write(sData.getBytes());
                serverWriter.flush();
                reset_timeout();
                
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

        } catch (UnknownHostException uhe)
        {
            String msgerror = Main.Txt("Verify_that_you_are_connected_to_the_internet_or_that_the_POP_server_'") + pe.get_proxy().getRemoteServer() + Main.Txt("'_exists.");
            //Common.showError(msgerror);
            LogManager.msg_proxy(LogManager.LVL_ERR, msgerror);
        }
        catch (Exception e)
        {
            if (!pe.is_finished())
            {
                LogManager.msg_proxy(LogManager.LVL_DEBUG, get_description() + ": " + e.getMessage() + " last command:" + last_command);
            }
        }
        finally
        {
            closeConnections();            
        }
        log( Main.Txt("Finished") + " " + pe.get_proxy().getRemoteServer()  );
    }  // handleConnection




    private int RETRBYTE()
    {
        // FIRST CHECK HEADER:
        byte[] first_line = new byte[256];
        int rlen = read_one_line(serverReader, first_line);
        if (rlen == 0)
        {
            m_error = ERROR_NO_ANSWER;
            return m_error;
        }
        try
        {
            // WRITE TO CLIENT
            clientWriter.write(first_line, 0, rlen);

            // IF SERVER GAVE ERR WE ARE DONE
            if (first_line[0] == '-')
            {
                return 0;
            }
        }
        catch (IOException iOException)
        {
            m_error = ERROR_NO_ANSWER;
            return m_error;
        }

        boolean encoded = true;
        reset_timeout();

        MandantContext m_ctx = Main.get_control().get_m_context(pe.get_proxy().getMandant());
        String suffix = ".eml";
        if (encoded)
            suffix = RFCGenericMail.get_suffix_for_encoded();

        File rfc_dump = m_ctx.getTempFileHandler().create_new_mailimp_file("pop3_" + this_thread_id + "_" + System.currentTimeMillis() + suffix,
                pe.get_proxy().getDiskArchive().getId());

        //File rfc_dump = new File(Main.RFC_PATH + "pop3_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = MailProxyServer.get_rfc_stream(rfc_dump, encoded);

        if (bos == null)
        {        
            if (Main.get_bool_prop(MandantPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }
        

        int ret = get_multiline_proxy_data(serverReader, clientWriter,  rfc_dump, bos);

        disable_timeout();

        // CLOSE STREAM
        try
        {
            bos.close();

            if (ret == 0)
            {
               Main.get_control().add_mail_file( rfc_dump, pe.get_proxy().getMandant(), pe.get_proxy().getDiskArchive(), 
                       /*bg*/ true, /*del_after*/ true, encoded );
            }  
            else
            {                
                rfc_dump.delete();
            }
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            LogManager.msg_proxy(LogManager.LVL_ERR, "Cannot close rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

            if (Main.get_bool_prop(MandantPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
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
        return 110;
    }

  

  
 
}  // POP3connection

