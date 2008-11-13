package dimm.home.mailproxyclient;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

/**
 *
 * @author Administrator
 */
public class TCP_Communicator extends Communicator
{
    
    private static final int TCP_LEN = 32;
    
    
    Socket keep_s;
    boolean keep_tcp_open;
    String last_ip;
    private int ping;
    
    public TCP_Communicator( CommContainer m )
    {
        super(m);
    }
    
    public synchronized String send( String str, OutputStream outp)
    {
        StationEntry se = main.get_selected_box();
        if (se != null)
        {
            return tcp_send( se.get_ip(), str, outp, null, -1 );
        }
        return "";
    }
    public boolean send_tcp_byteblock( String str, byte[] data)
    {
        StationEntry se = main.get_selected_box();
        if (se != null)
        {
            answer =  tcp_send( se.get_ip(), str, null, data, -1);
            return check_answer();
        }
        return false;
    }
   
    void write_output( InputStream ins, int alen, OutputStream outs ) throws IOException
    {
         // PUSH DATA OVER BUFFER
        int buff_len = 8192;
        byte[] buff = new byte[buff_len];
        
        while (alen > 0)
        {
            long blen = alen;
            if (blen > buff_len)
                blen = buff_len;
            
            int rlen = ins.read( buff, 0, (int)blen );
            outs.write( buff, 0, rlen );
            alen -= rlen;
        }
        outs.flush();           
    }
    
    public void comm_open()
    {
        comm_close();
        
        keep_tcp_open = true;
    }        
        
    
    
    public void comm_close()
    {
        if (keep_s != null)
        {
            try
            {
                keep_s.close();
            } catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        keep_s = null;
        
        keep_tcp_open = false;
    }        

    public boolean send_cmd(String string)
    {
        boolean ok = false;
        
        answer = send( string, null );
        
        return check_answer();
    }

    public boolean send_cmd(String string, OutputStream outp)
    {
        boolean ok = false;
        
        answer = send( string, outp );
        
        return check_answer();
    }
    @Override
    public boolean send_fast_retry_cmd(String str)
    {
        StationEntry se = main.get_selected_box();
        if (se != null)
        {
            answer = tcp_send( se.get_ip(), str, null, null, 3 );
            if (check_answer())
                return true;
/*            answer = tcp_send( se.get_ip(), str, null, null, 1 );
            if (check_answer())
                return true;
            answer = tcp_send( se.get_ip(), str, null, null, 1 );
            return check_answer();*/
        }
        return false;
    }
    
    String ping_answer;
    public String get_ping_answer()
    {
        return ping_answer;
    }
    public int ping( String ip, int delay_ms )
    {
        int ret = -1;
        //System.out.println("Calling ping for " + ip + ":...");

        Socket s;
        boolean keep_sock_open = false;
        
        if (keep_s == null || last_ip.compareTo( ip ) != 0)
        {
            s = new Socket();
            SocketAddress saddr = new InetSocketAddress( ip, MainFrame.TCP_SERVER_PORT );
            try
            {
                s.setSoTimeout( delay_ms);
                s.connect( saddr, delay_ms );  
                s.setTcpNoDelay(true);
            }
            catch (Exception exc)
            {
                System.out.println( " Fehler: " + exc.getMessage() );
                ret = -1;            
                return ret;
            }
        }
        else
        {
            s = keep_s;
            keep_sock_open = true;
        }
        
        try
        {
                                   
            ping_answer = tcp_send( s, "GETSTATUS", null, null );
            
            ret = ping;
            System.out.println(ret + " ms");
            
            if (!keep_sock_open)
                s.close();            
        }
        catch (java.net.SocketTimeoutException texc)
        {
            ret = -1;
            System.out.println( " Timeout");
        }
        catch (Exception exc)
        {
            System.out.println( " Fehler: " + exc.getMessage() );
            ret = -1;            
        }
        
        return ret;
    }
    
    public synchronized String tcp_send( Socket s, String str, OutputStream outp, byte[] add_data) throws IOException, Exception
    {
        StringBuffer sb = new StringBuffer();        
        
        sb.append("CMD:");

        // DO WE HAVE OPT. DATA?
        int opt_index = str.indexOf(" " );
        if (opt_index == -1)
            sb.append( str );  // NO ONLY PUT CMD
        else
        {
            sb.append( str.substring( 0, opt_index ) );  // CUT OFF CMD
        }
        sb.append( " LEN:");
        int opt_len = 0;

        byte[] opt_data = null;
        if (opt_index != -1 )
        {
            opt_data = str.substring( opt_index + 1).getBytes();
            opt_len = opt_data.length;
        }
        int add_data_len = 0;
        if (add_data != null)
            add_data_len += add_data.length;

        sb.append( (opt_len  + add_data_len) );

        // PAD FIRST BLOCK TO 32 BYTE
        while (sb.length() < TCP_LEN)
        {
            sb.append( " " );
        }

        byte[] data = sb.toString().getBytes();
        long start = System.currentTimeMillis();

        s.getOutputStream().write( data, 0, TCP_LEN );        

        // AND PUT OPT DATA IN NEXT BLOCK
        if (opt_len > 0)
        {
            s.getOutputStream().write( opt_data );
        }
        if (add_data_len > 0)
        {
            s.getOutputStream().write( add_data );
        }

        s.getOutputStream().flush();


        // READ ANSER
        byte[] in_buff = new byte[TCP_LEN];

        s.getInputStream().read( in_buff );

        ping = (int)(System.currentTimeMillis() - start);

        //System.out.println("Ping is " + ping  + " ms <" + str + ">");

        // THIS IS THE FORMAT OF IT
        //answer.append( "OK:LEN:");
        String local_answer = new String( in_buff, "UTF-8" );

        int len_idx = local_answer.indexOf("LEN:");
        if (len_idx <= 0)
            throw new Exception( "Data error" );

        // GET OK / NOK
        String ret = local_answer.substring(0, len_idx );
        int alen = Integer.parseInt( local_answer.substring( len_idx + 4).trim() );

        // MORE DATA?
        if (alen > 0)
        {
            if (outp != null)
            {
                write_output( s.getInputStream(), alen, outp );
            }
            else
            {
                byte[] res_data = new byte[alen];
                int rlen = s.getInputStream().read( res_data );
                while (rlen != alen)
                {
                    int rrlen = s.getInputStream().read( res_data, rlen, alen - rlen );
                    rlen +=  rrlen;
                }                   
                ret += new String( res_data, "UTF-8" );
            }
        }
        return ret;
    
    }
    
    
    public String tcp_send( String ip, String str, OutputStream outp, byte[] add_data, int timeout)
    {
        try
        {
            if (last_ip != null)
            {
                if (last_ip.compareTo( ip ) != 0)
                {
                    comm_close();
                }
            }
                    

            if (keep_s == null)
            {
                keep_s = new Socket();
                keep_s.setTcpNoDelay( true );
                keep_s.setReuseAddress( true );
               // keep_s.setSendBufferSize( 6000 );
               // keep_s.setReceiveBufferSize( 60000 );
                if (timeout > 0)
                    keep_s.setSoTimeout(timeout* 1000);
                else
                    keep_s.setSoTimeout(60* 1000); // DEFAULT TIMEOUT 60 SECONDS

                SocketAddress saddr = new InetSocketAddress( ip, MainFrame.TCP_SERVER_PORT );
                if (timeout > 0)
                    keep_s.connect( saddr, timeout*1000 );
                else 
                    keep_s.connect( saddr);
            }
            
            Socket s = keep_s;

            String ret = tcp_send( s, str, outp, add_data);

            
            // LATCH IP 
            last_ip = ip;
            /*
            if (!keep_tcp_open)
            {
                s.close();
                keep_s = null;
            }
            */
            main.set_status( "" );

            return ret;
        }
        
        //  throws SocketException, IOException, Exception
        catch ( SocketTimeoutException texc )
        {
             this.comm_close();
             return "--timeout--";
        }
        catch ( java.net.ConnectException cexc)
        {
            this.comm_close();
            main.set_status("Kommunikation schlug fehl: " + cexc.getMessage());
        }
        catch ( Exception exc )
        {
             this.comm_close();
             //exc.printStackTrace();
             main.set_status("Kommunikation schlug fehl: " + exc.getMessage());
        }
        
        return "--failed--";
    }

     

}
