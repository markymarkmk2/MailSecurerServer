package dimm.home.mailproxyclient;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 *
 * @author Administrator
 */
public class UDP_Communicator extends Communicator        
{
    private static final int UDP_SEND_TO_MS = 20000;   
    private static final int UDP_LEN = 1024;
    public  final int UDP_BIGBLOCK_SIZE = 48000;
    int answer_station_id;
    DatagramSocket keep_udp_s = null;
    private int ping;
    
    public String magic = "MAILPROXY:";
    
    public UDP_Communicator( CommContainer m )
    {
        super(m);
    }
    
    
    public int get_answer_station_id()
    {
        return answer_station_id;
    }

   
    
    public int ping( String id, int delay_ms )
    {        
        //System.out.println("Calling ping for " + id + ":...");

        String str = id + ":" + "GETSTATUS"; 
        answer = udp_send( str, false, 0, null, delay_ms ); 
        if (check_answer())
        {
            return ping;
        }
        return -1;
    }
    
    
    @Override
    public synchronized String send( String str, OutputStream outp)
    {
        
        return udp_send( str, false, 0, outp );
    }
  
    @Override
    public void comm_open()
    {
        
    }

    @Override
    public void comm_close()
    {
        
    }
    
    public String udp_send( String str, boolean scan, int retries )
    {
        return udp_send( str, scan, retries, null);
    }
        
    public synchronized boolean send_fast_retry_cmd(String str)
    {
        
        StationEntry ste = main.get_selected_box();
        if (ste != null)
        {
            String id = Long.toString(ste.get_id() ) + ":";
            str = id + str;

            // 3 TIMES
            answer = udp_send( str, false, 0, null, 2500 );
            if (check_answer())
                return true;
/*
            answer = udp_send( str, false, 0, null, 1 );
            if (check_answer())
                return true;

            answer = udp_send( str, false, 0, null, 1 );
            return check_answer();
 */
        }
        return false;
    }
            
    public boolean send_cmd(String string)
    {
        boolean ok = false;
        
        StationEntry ste = main.get_selected_box();
        if (ste != null)
        {
            String id = Long.toString(ste.get_id() ) + ":";

            answer = send( id + string, null );

            return check_answer();
        }
        return ok;
    }

    public boolean send_cmd(String string, OutputStream outp)
    {
        boolean ok = false;

        StationEntry ste = main.get_selected_box();
        if (ste != null)
        {
            String id = Long.toString(ste.get_id() ) + ":";

            answer = send( id + string, outp );
       
            return check_answer();
        }
        return ok;
    }
    
    @Override
    public boolean check_answer() 
    {
        boolean ok = false;
        if (answer   == null || answer.length() == 0)
        {
            answer   = "Kommunikation fehlgeschlagen!";
            return false;
        }
        
        
        if (answer.compareTo("--failed--") == 0)
        {
            answer =  "Kommunikation fehlgeschlagen!";
            return false;
        }
        
        if (answer.length() < magic.length() || answer.substring( 0, magic.length() ).compareTo( magic ) != 0)
        {
            answer =  "Unbekannter Kommunikationspartner!";
            return false;
        }

        // CLIP OFF ID
        answer_station_id = -1;
        answer = answer.substring( magic.length() );
        try
        {
            int index = answer.indexOf(":");
            String id_str =  answer.substring( 0, index );
            answer_station_id = Integer.parseInt( id_str );
            answer = answer.substring(index + 1 );
        }
        catch (Exception exc)
        {
            answer =  "Unbekannter Kommunikationspartner!";
            return false;
        }
        
        if (answer.compareTo("UNKNOWN_COMMAND") == 0)
        {            
            return false;
        }
            
                        
        if (answer.length() >= 2 && answer.substring(0, 2).compareTo("OK") == 0)
        {
            ok = true;
            if (answer.length() > 3)
                answer = answer.substring(3);
            else
                answer = "";

            ok = true;
        }
        else if (answer.length() >= 3 && answer.substring(0, 3).compareTo("NOK") == 0)
        {
            ok = false;
            if (answer.length() > 4)
                answer = answer.substring(4);
            else
                answer = "";

        }
        return ok;
    }
    
    public String udp_send( String str, boolean scan, int retries, OutputStream outp )
    {
        return udp_send( str, scan, retries, outp, -1 );
    }
    
    public synchronized String udp_send( String str, boolean scan, int retries, OutputStream outp, int timeout )
    {
               
        try
        {

            if (keep_udp_s == null)
            {
                if (!Main.scan_local)
                    keep_udp_s = new DatagramSocket(MainFrame.UDP_LOCAL_PORT,InetAddress.getByName("0.0.0.0"));
                else
                    keep_udp_s = new DatagramSocket(MainFrame.UDP_LOCAL_PORT,InetAddress.getByName("localhost"));

                if (timeout == -1)
                    timeout = UDP_SEND_TO_MS;

                keep_udp_s.setSoTimeout(timeout );
                keep_udp_s.setReuseAddress(true);
                keep_udp_s.setBroadcast(true);
            }



            byte[] out_data = new byte[UDP_LEN];
            for (int i = 0; i < out_data.length; i++)
            {
                out_data[i] = ' ';
            }
            byte[] str_data = str.getBytes();
            for (int i = 0; i < str_data.length; i++)
            {
                out_data[i] = str_data[i];
            }


            byte[] in_data = new byte[UDP_LEN];

            DatagramPacket out_packet = null;

            if (!Main.scan_local)
                out_packet = new DatagramPacket(out_data,out_data.length,InetAddress.getByName("255.255.255.255"),MainFrame.UDP_SERVER_PORT);
            else                
                out_packet = new DatagramPacket(out_data,out_data.length,InetAddress.getByName("localhost"),MainFrame.UDP_SERVER_PORT);


            DatagramPacket in_packet = new DatagramPacket( in_data, in_data.length );

            if (scan)
            {
                keep_udp_s.setSoTimeout(250);  // MAX N* 200ms WARTEZEIT
            }

            try
            {
                long start = System.currentTimeMillis();

                keep_udp_s.send( out_packet );           
                if (!scan)
                {
                    //System.out.println("Sending <" + str +">");
                    keep_udp_s.receive( in_packet );

                    ping = (int)(System.currentTimeMillis() - start);
                    //System.out.println("Ping is " + ping  + " ms <" + str + ">");

                    String result = new String( in_packet.getData(), "UTF-8").trim();


                    //System.out.println("Received <" + result +">");

                    // MULTI DATAGRAM MESSAGE?
                    int follow_frames_idx = result.indexOf(":CONTINUE:");
                    if (follow_frames_idx > 0 && follow_frames_idx < 20) // MUST BE SONICBOX:ID:CONTINUE:NNNNNN
                    {
                        long data_cnt = Long.parseLong(result.substring( follow_frames_idx + 10 ));

                        // SAVE START OF TELEGRAM
                        StringBuffer sb = new StringBuffer( result.substring(0, follow_frames_idx + 1 ) );

                        while (data_cnt > UDP_BIGBLOCK_SIZE)
                        {
                            byte[] big_data = new byte[UDP_BIGBLOCK_SIZE];

                            DatagramPacket in_p2 = new DatagramPacket( big_data, big_data.length );
                            keep_udp_s.receive( in_p2 );

                            if (outp != null)
                            {
                                outp.write(in_p2.getData());
                            }
                            else
                            {                            
                                String _s = new String(in_p2.getData(), "UTF-8" );
                                sb.append( _s.trim() );
                            }

                            data_cnt -= UDP_BIGBLOCK_SIZE;
                        }
                        if (data_cnt > 0)
                        {
                            byte[] big_data = new byte[(int)data_cnt ];

                            DatagramPacket in_p2 = new DatagramPacket( big_data, big_data.length );
                            keep_udp_s.receive( in_p2 );   

                            if (outp != null)
                            {
                                outp.write(in_p2.getData());
                            }
                            else
                            {                            
                                String _s = new String(in_p2.getData(), "UTF-8" );
                                sb.append( _s.trim() );
                            }

                        }
                        result = sb.toString();
                    }
                    else
                    {
                        if (result.substring(0, magic.length()).compareTo( magic ) != 0)
                            System.out.println("Result: " + result );

                    }

                    keep_udp_s.close();
                    keep_udp_s = null;

                    main.set_status( "" );
                    return result;
                }
                else
                {

                    String result = "";
                    int start_retries = retries;

                    while (retries > 0)
                    {
                        try
                        {
                            keep_udp_s.receive( in_packet );
                            retries = start_retries; // START AGAIN


                            String ping = new String( in_packet.getData()).trim();    
                            String ip = in_packet.getSocketAddress().toString();

                            int s_idx = ip.indexOf("/");
                            if (s_idx >= 0)
                                ip = ip.substring( s_idx + 1 );

                            int e_idx = ip.indexOf(":");
                            if (e_idx >= 0)
                                ip = ip.substring( 0, e_idx );

                            ping += " IP:" + ip;

                            main.set_status("Scanning... found: " +  ping);

                            if (result.length() > 0)
                                result += "\n";

                            result += ping;                            
                        }
                        catch ( SocketTimeoutException texc)
                        {
                             retries--;                         
                        }
                    }
                    keep_udp_s.close();
                    keep_udp_s = null;

                    return result;
                }                                           
            }

            catch ( SocketTimeoutException texc)
            {
                if (keep_udp_s != null)
                    keep_udp_s.close();
                keep_udp_s = null;
                main.set_status("No answer from box" + texc.getMessage() );
                //texc.printStackTrace();
                return "--timeout--";

            }
            catch ( Exception exc )
            {
                if (keep_udp_s != null)
                    keep_udp_s.close();
                keep_udp_s = null;
                main.set_status("Communication failed: " + exc.getMessage() );
                //exc.printStackTrace();
            }
        }
        catch ( Exception exc )
        {      
            if (keep_udp_s != null)
                keep_udp_s.close();
            keep_udp_s = null;
            //exc.printStackTrace();
            main.set_status("Socket failed " + exc.getMessage());
        }
        return "--failed--";
    }
    
        

}
