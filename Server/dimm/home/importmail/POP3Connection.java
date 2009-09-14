package dimm.home.importmail;

import dimm.home.mailarchiv.*;
import dimm.home.workers.MailProxyServer;
import java.net.Socket;
import java.net.UnknownHostException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

public class POP3Connection extends ProxyConnection
{

    private static final String NAME = "POP3-Proxy";
    
    String multi_line_commands[] = {"LIST", "RETR", "UIDL", "TOP", "CAPA"};
    String single_line_commands[] = {"QUIT", "USER", "PASS", "DELE", "STAT", "NOOP", "RSET", "APOP", "STLS", "AUTH"};

    static int thread_count = 0;

    public POP3Connection(ProxyEntry pe)
    {
        super( pe );
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
            clientWriter = new BufferedOutputStream(clientSocket.getOutputStream(), clientSocket.getSendBufferSize());
            // get the client input stream	
            clientReader = new BufferedInputStream(clientSocket.getInputStream(), clientSocket.getReceiveBufferSize());

            // connect to the real POP3 server
            serverSocket = new Socket(pe.getRemoteServer(), pe.getRemotePort());
            // set the socket timeout
            serverSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            clientSocket.setSoTimeout(SOCKET_TIMEOUT[0]);
            
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getReceiveBufferSize());
            Main.debug_msg(2, "getReceiveBufferSize: " + serverSocket.getSendBufferSize());
            Main.debug_msg(2, "getSoTimeout: " + serverSocket.getSoTimeout());

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
                log( 3, Main.Txt("Waiting_for_Server..."));
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

                log( 3, Main.Txt("Waiting_for_Client..."));
                
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

                log( "C: " + sData);
                
                while (sData.toUpperCase().startsWith("RETR "))
                {
                    m_Command = POP_RETR;
                    
                    // write it to the POP server
                    serverWriter.write(sData.getBytes());
                    serverWriter.flush();

                    if (RETRBYTE() > 0)
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
                    log( "C: " + sData);

                    
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
            String msgerror = Main.Txt("Verify_that_you_are_connected_to_the_internet_or_that_the_POP_server_'") + pe.getRemoteServer() + Main.Txt("'_exists.");
            //Common.showError(msgerror);
            Main.err_log(msgerror);
        } catch (Exception e)
        {
            Main.err_log(e.getMessage());
        }
        log(2, Main.Txt("Finished") );
    }  // handleConnection




    private int RETRBYTE()
    {

        File rfc_dump = new File(Main.RFC_PATH + "pop3_" + this_thread_id + "_" + System.currentTimeMillis() + ".txt");
        
        
        BufferedOutputStream bos = MailProxyServer.get_rfc_stream(rfc_dump);

        if (bos == null)
        {        
            if (Main.get_bool_prop(MandantPreferences.ALLOW_CONTINUE_ON_ERROR, false) == false)
            {
                m_error = ERROR_UNKNOWN;
                return m_error;
            }
        }
        

        int ret = get_multiline_proxy_data(serverReader, clientWriter,  rfc_dump, bos);
        
        // CLOSE STREAM
        try
        {
            bos.close();

            if (ret == 0)
            {
               Main.get_control().add_mail_file( rfc_dump, pe.getMandant(), pe.getDiskArchive(), /*bg*/ true, /*del_after*/ true );
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

