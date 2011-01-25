/*
 * Communicator.java
 *
 * Created on 8. Oktober 2007, 09:35
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import dimm.home.mailarchiv.Commands.*;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import home.shared.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class Communicator extends WorkerParent
{
    private static final int UDP_LEN = 256;
    private static final int TCP_LEN = 32;
    private static final int UDP_LOCAL_PORT = 11511;
    private static final int UDP_SERVER_PORT = 11510;
    private static final int TCP_SERVER_PORT = 11510;
    public static final String HELLO_CMD = "HELLO";
    final int UDP_BIGBLOCK_SIZE = 48000;
    
    public static final String NAME = "Communicator";
    
    private static final String magic = "MAILSECURER:";
    
    ArrayList<AbstractCommand> cmd_list;
    
    DatagramSocket udp_s; 
    ServerSocket tcp_s;
    boolean using_fallback = false;
    
    int udp_listeners = 0;
    int tcp_listeners = 0;
    
    /** Creates a new instance of Communicator */
    public Communicator()
    {
        super(NAME);
        cmd_list = new ArrayList<AbstractCommand>();
        
        cmd_list.add( new HelloCommand() );
        cmd_list.add( new GetSetOption() );
        cmd_list.add( new ListOptions() );
        cmd_list.add( new IPConfig() );
        cmd_list.add( new Ping() );
        cmd_list.add( new ReadLog() );
        cmd_list.add( new Reboot() );
        cmd_list.add( new Restart() );
        cmd_list.add( new GetStatus() );
        cmd_list.add( new ShellCmd() );
        cmd_list.add( new GetLog() );
        cmd_list.add( new DumpLog() );
        cmd_list.add( new SetStation() );
        cmd_list.add( new WriteFile() );
        cmd_list.add( new StartVPN() );
        cmd_list.add( new ImportMailFile() );
        cmd_list.add( new UploadMailFile() );
        cmd_list.add( new LicenseConfig() );
        is_started = false;
    }

    public ArrayList<AbstractCommand> get_cmd_array()
    {
        return cmd_list;
    }
    @Override
    public boolean initialize()
    {
        using_fallback = false;
        
        // NOT AN APPLIANCE?
        if (!Main.get_bool_prop(GeneralPreferences.AUTO_SET_IP))
        {
            // WE ARE DONE
            return true;
        }
        
        if (System.getProperty("os.name").startsWith("Linux"))
        {
            IPConfig ipc = new IPConfig();

            String real_ip = ipc.get_ip_for_if( "eth0" );
            if (real_ip != null)
            {
                LogManager.msg_comm( LogManager.LVL_INFO, "Got real IP <" + real_ip + ">" );
            }
            else
            {
                LogManager.msg_comm( LogManager.LVL_ERR, "Cannot detect valid IP, setting to fallback IP: 192.168.201.201");

                ipc.set_ipconfig( 0, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
                using_fallback = true;

                return false;
            }

            if (!ipc.is_route_ok())
            {
                LogManager.msg_comm( LogManager.LVL_ERR, "Invalid routing detected, setting to fallback IP: 192.168.201.201");

                ipc.set_ipconfig( 0, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
                using_fallback = true;

                return false;
            }
        }
        return true;
    }
    
    private void run_loop()
    {
        int fallback_cnt = 0;
        while (!isShutdown())
        {
            Main.sleep(1000);
           
            
            // TRY EVERY MINUTE TO CHANGE FROM FALLBACK TO VALID IP
            if (using_fallback)
            {
                fallback_cnt++;
                if (fallback_cnt == 60)
                {
                    fallback_cnt = 0;
                    if (set_ipconfig())
                    {
                        using_fallback = false;
                    }
                }
            }
        }
        finished = true;
                    
    }
    
    @Override
    public boolean start_run_loop()
    {
        // SINGLE INSTANCE
        if (is_started)
            return true;

        LogManager.msg_comm( LogManager.LVL_DEBUG, "Starting communicator tasks" );
        
        start_broadcast_task();
        start_tcpip_task();
         
        BackgroundWorker worker = new BackgroundWorker(NAME)
        {
            @Override
            public Object construct()
            {
                run_loop();

                return null;
            }
        };
        
        worker.start();

        is_started = true;
        return true;
         
      
    }
        

    boolean start_broadcast_task()
    {
        BackgroundWorker worker = new BackgroundWorker(NAME + ".BroadcastTask")
        {
            @Override
            public Object construct()
            {
                int ret = -1;
                try
                {
                    broadcast_listener();
                }
                catch (Exception err)
                {
                    err.printStackTrace();
                    ret = -2;
                }
                Integer iret = new Integer(ret);
                return iret;
            }
        };
        
        worker.start();
        return true;
    }
    boolean start_tcpip_task()
    {
        BackgroundWorker worker = new BackgroundWorker(NAME + ".TcpIpTask")
        {
            @Override
            public Object construct()
            {
                int ret = -1;
                try
                {
                    tcpip_listener();
                }
                catch (Exception err)
                {
                    err.printStackTrace();
                    ret = -2;
                }
                Integer iret = new Integer(ret);
                return iret;
            }
        };
        
        worker.start();
        return true;
    }
    
    
    void broadcast_listener()
    {
            
        
        int recv_len = UDP_LEN;  // BIG ENOUGH FOR US
        
        udp_s = null;
        
        
        while (!isShutdown())
        {            
             try
             {
                 if (isGoodState())
                     this.setStatusTxt(ST_IDLE);
    
                 try
                 {

                    while( !isShutdown() )
                    {
                        // CHECK IF WE WANT TO LOCK IN IP
                        InetAddress adr = InetAddress.getByName(Main.get_base_ip());
                        if (adr.isLoopbackAddress())
                        {
                            udp_s = new DatagramSocket(UDP_SERVER_PORT,InetAddress.getByName("0.0.0.0"));
                        }
                        else
                        {
                            udp_s = new DatagramSocket(UDP_SERVER_PORT, adr);
                        }

                         
                         udp_s.setReuseAddress(true);
                         udp_s.setBroadcast(true);
                         udp_s.setSoTimeout(0); // NO TIMEOUT
                         byte[] buffer = new byte[recv_len];
                         final DatagramPacket packet = new DatagramPacket( buffer, recv_len );
                         
                         
                         //System.out.println( "Packet erwartet" );
                         udp_s.receive( packet );
                         udp_listeners++;
                         final DatagramSocket answer_sock = udp_s;
                         LogManager.msg_comm( LogManager.LVL_VERBOSE, "udp_recv packet");

                         if (isGoodState())
                             this.setStatusTxt("Connected to " + String.valueOf(udp_listeners + tcp_listeners) + " client(s)");
                         
                         //System.out.println( "Packet gefunden" );

                         BackgroundWorker work = new BackgroundWorker(NAME + ".UdpDispatch")
                         {
                          
                              @Override
                              public Object construct()
                              {
                                 try
                                 {
                                    dispatch_udp_packet( answer_sock, packet );
                                    
                                    if (isGoodState())
                                        setStatusTxt( ST_IDLE);
                                 }
                                 catch ( Exception exc )
                                 {
                                     if (!isShutdown())
                                     {
                                         exc.printStackTrace();
                                         setStatusTxt( "Communication broken: "  + exc.getMessage());
                                         setGoodState( false );
                                         LogManager.msg_comm( LogManager.LVL_WARN, getStatusTxt() );
                                         Main.sleep(2000);
                                     }
                                 }             

                                udp_listeners--;
                                answer_sock.close(); 
                                return null;
                              }
                            
                         };
                         
                         work.construct(); //.start();
                         
                         udp_s = null;
                     }
                 }
                 catch ( Exception exc )
                 {
                     if (!isShutdown())
                     {
                         exc.printStackTrace();
                         this.setStatusTxt( "Communication aborted: "  + exc.getMessage());
                         this.setGoodState( false );
                         LogManager.msg_comm( LogManager.LVL_WARN, getStatusTxt() );
                         Main.sleep(2000);
                     }
                 }             
             }
             catch ( Exception exc )
             {
                 if (!isShutdown())
                 {
                     exc.printStackTrace();
                     LogManager.msg_comm( LogManager.LVL_WARN, "Communication closed: "  + exc.getMessage() );
                     this.setStatusTxt( "Communication closed (2 processes?): "  + exc.getMessage());
                     this.setGoodState( false );
                     Main.sleep(2000);
                 }
             }         
             if (udp_s != null)
                 udp_s.close();
             
        }
    }
    
    
    void tcpip_listener()
    {
            
        while (!isShutdown())
        {            
             try
             {
                 tcp_s = new ServerSocket(TCP_SERVER_PORT);
                 tcp_s.setReuseAddress(true );                 
                 tcp_s.setReceiveBufferSize( 60000 );
                 
                 final Socket s = tcp_s.accept();
                 s.setTcpNoDelay( true );
                                  
                 BackgroundWorker work = new BackgroundWorker(NAME + ".TcpIpDispatch")
                 {

                     @Override
                     public Object construct()
                     {
                         try
                         {

                             while( !isShutdown() )
                             {                 
                                 // HELLO CLIENT...
                                 InputStream in = s.getInputStream();

                                 OutputStream out = s.getOutputStream();

                                 if (!handle_ip_command( in, out ))
                                 {
                                     //System.out.println("Closing socket" );
                                     //if (in.available() == 0)
                                     {
                                         s.close();
                                         break;
                                     }
                                 }
                             }
                         }
                         catch ( Exception exc )
                         {
                             try
                             {
                                 if (!s.isClosed())
                                     s.close();
                             }
                            catch ( Exception lexc )
                            {}

                             if (!isShutdown())
                             {
                                 exc.printStackTrace();
                                 setStatusTxt( "Communication aborted: "  + exc.getMessage());
                                 setGoodState( false );
                                 LogManager.msg_comm( LogManager.LVL_WARN, getStatusTxt() );
                             }
                         }             
                         return null;
                     }

                 };

                 work.start();
                         
             }
             catch ( Exception exc )
             {
                 if (!isShutdown())
                 {
                     exc.printStackTrace();
                     LogManager.msg_comm( LogManager.LVL_WARN,"Kommunikationsport geschlossen: "  + exc.getMessage() );
                     this.setStatusTxt( "Communication is closed (2 processes?): "  + exc.getMessage());
                     this.setGoodState( false );
                     Main.sleep(5000);
                 }
             }         
             if (tcp_s != null)
             {
                try
                {
                    tcp_s.close();
                } catch (IOException ex)
                {
                    ex.printStackTrace();
                }
             }
        }
    }    
    @Override
    public void setShutdown(boolean shutdown)
    {
        super.setShutdown( shutdown );
        
        try
        {
            udp_s.close();
            tcp_s.close();
        }
        catch (Exception exc)
        {}
    }

    private boolean handle_ip_command(InputStream in, OutputStream out) throws IOException
    {
        // TCP-PACKET HAS AT LEAST TCP_LEN BYTE
        byte[] buff = new byte[TCP_LEN];
//        System.out.println("Availeable: " + in.available() );       
 //       System.out.println("Before_read socket" );
        
        in.read( buff );
//        System.out.println("After_read socket" );
        if (buff[0] == 0)
            return false;
        
        String data = new String( buff);
        ParseToken pt = new ParseToken( data );
        String cmd = pt.GetString("CMD:");
        int len = pt.GetLong("LEN:").intValue();
        byte[] add_data = null;
        

        if (len > 0)
        {
            add_data = new byte[len];
            int rlen = 0;
            while (rlen < len)
            {
                int llen = in.read( add_data, rlen, len - rlen );
                rlen += llen;
            }
        }
        
        dispatch_tcp_comannd( cmd, add_data, out );
        
        return true;
    }        
    
    void write_tcp_answer( boolean ok, String ret, OutputStream out ) throws IOException
    {
        StringBuffer answer = new StringBuffer();
        
        if (ok)
            answer.append( "OK:LEN:");
        else
            answer.append( "NOK:LEN:");

        int alen = 0;
        
        if (ret != null)
        {
            alen = ret.getBytes("UTF-8").length;
        }
        
        answer.append(  Integer.toString(alen) );
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes() );

        if (alen > 0)
            out.write( ret.getBytes("UTF-8") );
        
        out.flush();
    }
    
    void write_tcp_answer( boolean ok, long alen, InputStream in, OutputStream out ) throws IOException
    {
        StringBuffer answer = new StringBuffer();
        
        if (ok)
            answer.append( "OK:LEN:");
        else
            answer.append( "NOK:LEN:");

          
        answer.append(  Long.toString(alen) );
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes(), 0, TCP_LEN );

        // PUSH DATA OVER BUFFER
        int buff_len = 8192;
        byte[] buff = new byte[buff_len];
        
        while (alen > 0)
        {
            long blen = alen;
            if (blen > buff_len)
                blen = buff_len;
            
            int rlen = in.read( buff, 0, (int)blen );
            out.write( buff, 0, rlen );
            alen -= rlen;
        }
        
        in.close();
                
        out.flush();
    }
    
    void write_tcp_answer( boolean ok, AbstractCommand cmd, OutputStream out ) throws IOException
    {
        if (cmd.has_stream())
        {
            write_tcp_answer( ok, cmd.get_data_len(), cmd.get_stream(), out );
        }
        else
        {                        
            write_tcp_answer( ok, cmd.get_answer(), out );
        }
    }
    
    void dispatch_tcp_comannd( String str, byte[] add_data, OutputStream out ) throws IOException
    {
        LogManager.msg_comm( LogManager.LVL_VERBOSE, "Received ip command <" + str + "> " );
                
        if (str.equals("?") || str.equals("help") )
        {
            String answer = "";
            for (int i = 0; i < cmd_list.size(); i++)
            {
                answer += "\n";                        
                answer += cmd_list.get(i).get_token();
            }

            write_tcp_answer( true, answer, out );                
            return;
        }
        else
        {                    
            // STRIP OFF STATION ID, ONLY REST TO FUNCS
            for (int i = 0; i < cmd_list.size(); i++)
            {
                AbstractCommand cmd = cmd_list.get(i);
                if (cmd.is_cmd( str ))
                {                
                    boolean ok = cmd.do_command( add_data );
                    
                    write_tcp_answer( ok, cmd, out );
                    return;
                }
           }
        }        
        write_tcp_answer( false, "UNKNOWN_COMMAND", out );
    }
        
    
   
    
    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        boolean ok = true;        
/*        if (udp_s == null)
        {
            sb.append( "UDP Network init failed" );
            ok = false;
        }
  */
        return ok;
    }

    private void dispatch_udp_packet(DatagramSocket s, DatagramPacket in_packet) throws IOException
    {

        // BUILD TRIMMED TEXT
        byte[] in_data = in_packet.getData();
 
//        byte frame_nr = (byte)(in_data[0] - '0');
//        System.out.println("Got frame "+ Byte.toString( frame_nr ) );        
        
        
        String str = new String( in_data).trim();      
        int len = str.length();
        String answer = null;
        
        LogManager.msg_comm( LogManager.LVL_VERBOSE, "Received command <" + str + "> from " + in_packet.getSocketAddress().toString() );
        
        // THIS IS HANDLED BY BROADCAST -> EVERYBODY IN NETWORK MUST ANSWER
        if (len == HELLO_CMD.length() && str.compareTo(HELLO_CMD) == 0)
        {
            HelloCommand hello = new HelloCommand();
            if (hello.do_command(str))
                answer = "OK:" + hello.get_answer() + " PO:" + Main.get_base_port();
            else
                answer = "NOK:" + hello.get_answer();

            
            answer_udp( s, in_packet, answer );
        }
        else
        {
            // FILTER CALLS FOR THIS STATION OR STATION 0
            String station = Main.get_station_id() + ":";
            if (len > station.length() && 
                    (str.substring(0, station.length()).compareTo( station) == 0 || str.substring(0, 2).compareTo( "0:" ) == 0))
            {
                // TRIM STATION
                str = str.substring(station.length());
                
                if (str.equals("?") || str.equals("help") )
                {
                    answer = "";
                    for (int i = 0; i < cmd_list.size(); i++)
                    {
                        answer += "\n";                        
                        answer += cmd_list.get(i).get_token();
                    }
                }
                else
                {                    
                    // STRIP OFF STATION ID, ONLY REST TO FUNCS
                    answer = dispatch_remote_command( str );
                }
                
                answer_udp( s, in_packet, answer );
            }
            // ELSE THIS PACKET IS NOT FOR US
        }
    }
     
    // FORMAT IS <StationID>:COMMAND:[OTPIONS....  ]
    String  dispatch_remote_command( String data )
    {
        
        for (int i = 0; i < cmd_list.size(); i++)
        {
            AbstractCommand cmd = cmd_list.get(i);
            if (cmd.is_cmd( data ))
            {                
                if (cmd.do_command( data ))
                {
                    
                    if (cmd.get_answer() != null)
                        return "OK:" +  cmd.get_answer();
                    else
                        return "OK";                        
                }
                else
                {
                    if (cmd.get_answer() != null)
                        return "NOK:" +  cmd.get_answer();
                    else
                        return "NOK";                        
                }
            }
        }        
        return  "UNKNOWN_COMMAND";        
    }
    void answer_udp( DatagramSocket s, DatagramPacket in_packet, String answer ) throws SocketException, IOException
    {
        LogManager.msg_comm( LogManager.LVL_VERBOSE, "Sending answer <" + answer + "> to " + in_packet.getSocketAddress().toString() );
        
        //byte frame_nr = (byte)(in_packet.getData()[0] - '0');
        
        String answer_first_block = null;
        byte[] answer_next_blocks = null;
        if (answer.length() > UDP_LEN - 30)
        {
            answer_next_blocks = answer.getBytes("UTF-8");
            answer_first_block = magic + Main.get_station_id() + ":CONTINUE:" + answer_next_blocks.length;
        }
        else
        {
            answer_first_block =  magic + Main.get_station_id() + ":" + answer;
        }
        
        byte[] data = answer_first_block.getBytes("UTF-8");
        byte[] out_buff = new byte[UDP_LEN];
        for (int i = 0; i < out_buff.length; i++)
        {
            out_buff[i] = ' ';            
        }
        for (int i = 0; i < data.length; i++)
        {
            out_buff[i] = data[i];                        
        }
        
                                   
        
        DatagramPacket out_packet = null;
        
        // LOCAL CONNECT NO BROADCAST
        boolean is_local = false;
        
        if (in_packet.getAddress().getHostAddress().compareTo("127.0.0.1") == 0)
            is_local = true;
        
        
        if (is_local)
            out_packet = new DatagramPacket( out_buff, out_buff.length, in_packet.getSocketAddress() );
        else
            out_packet = new DatagramPacket( out_buff, out_buff.length, InetAddress.getByName("255.255.255.255"),UDP_LOCAL_PORT );
            
        s.send( out_packet );
        
  /*      System.out.println("Sent Header" + out_buff.length + " Byte " );
     */    
        
        if (answer_next_blocks != null)
        {
            int rest_len = answer_next_blocks.length;
            int offs = 0;
            while (rest_len > 0)
            {
                int slen = rest_len;
                if (slen > UDP_BIGBLOCK_SIZE)
                    slen = UDP_BIGBLOCK_SIZE;                                    
                
                
                if (is_local)
                    out_packet = new DatagramPacket( answer_next_blocks, offs, slen, in_packet.getSocketAddress() );
                else        
                    out_packet = new DatagramPacket( answer_next_blocks, offs, slen, InetAddress.getByName("255.255.255.255"),UDP_LOCAL_PORT );
                
                s.send( out_packet );
      //          System.out.println("Sent Data" + slen + " Byte at offs " + offs );

                offs += slen;
                rest_len -= slen;
            }
        }                        
    }        
    
    private boolean set_ipconfig()
    {
        IPConfig ipc = new IPConfig();
                
        
        if (ipc.set_programmed_ipconfig())
        {
            // ONLY RETURN TRUE IF EVERYTHING WORKS
            return true;
        }
        return false;
        
    }

    @Override
    public String get_task_status()
    {
        return "";
    }

    @Override
    public String get_task_status( int ma_id )
    {
        return "";
    }
}
 

