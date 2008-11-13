package dimm.home.mailproxyclient;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.io.OutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;
import dimm.home.mailproxyclient.Utilities.ParseToken;


/**
 *
 * @author Administrator
 */
public abstract class Communicator 
{
    public static final String MISS_ARGS = "missing args";
    public static final String WRONG_ARGS = "wrong args";
    public static final String UNKNOWN_CMD = "UNKNOWN_COMMAND";
    
    CommContainer main;
    String answer;    

    public Communicator( CommContainer _m )
    {
        main = _m;
    }
    public abstract String send( String str, OutputStream outp);
    
    public abstract void comm_open();        
    
    
    public abstract void comm_close();
    
    public abstract boolean send_cmd(String string);

    public abstract boolean send_cmd(String string, OutputStream outp);
    public abstract boolean send_fast_retry_cmd(String str);

    
    public String get_answer()
    {
        return answer;
    }

    public String get_answer_err_text()
    {       
        if (answer.indexOf(MISS_ARGS) >= 0)
        {
            return "Fehlende Argumente";
        }
        if (answer.indexOf(WRONG_ARGS) >= 0)
        {
            return "Fehlerhafte Argumente";
        }            
        if (answer.indexOf(UNKNOWN_CMD) >= 0)
        {
            return "Sorry, dieser Befehl wird von " + Main.SERVERAPP + " nicht unterst�tzt";
        }
        
        return answer;
    }
    
    public boolean check_answer( ) 
    {
        boolean ok = false;
        if (answer == null || answer.length() == 0)
        {
            answer = "Kommunikation fehlgeschlagen!";
            return false;
        }
        
        
        if (answer.compareTo("--failed--") == 0)
        {
            answer =  "Kommunikation fehlgeschlagen!";
            return false;
        }
          
        if (answer.compareTo("UNKNOWN_COMMAND") == 0)
        {
            answer =  "Oha, dieser Befehl wird von der Box nicht unterst�tzt!";
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

    public void do_scan( ArrayList<StationEntry> st_list, String fixed_ip, boolean fast_mode)
    {
        st_list.clear();
        //String answer = null;
        
        if (this instanceof TCP_Communicator)
        {
            if (fixed_ip != null)
            {
                TCP_Communicator tcom = (TCP_Communicator)this;
                answer = tcom.tcp_send( fixed_ip, "HELLO", null, null, 1);
            }
        }
        else
        {
            main.set_status("Scanne Netzwerk...");

            UDP_Communicator ucom = (UDP_Communicator)this;
            answer = ucom.udp_send("HELLO", true, (fast_mode) ? 2 : 10);
        }
        
        if (check_answer())
        {          
            StringTokenizer tok = new StringTokenizer( answer, "\n" );
            while (tok.hasMoreTokens())
            {
                String station = tok.nextToken();
                if (station.length() > 0)
                {
                    try
                    {
                        ParseToken pt = new ParseToken( station );
                        String st_str = pt.GetString("STATION:");
                        if (st_str == null || st_str.length() == 0)
                            continue;
                        
                        int st_id = 0;
                        
                        if (st_str.compareTo("null") == 0)                            
                            st_id = 0;
                        else
                            st_id = Integer.parseInt( st_str);
                        
                        String ver = pt.GetString("VER:");
                        String ip = pt.GetString("IP:");
                        if (fixed_ip != null)
                            ip = fixed_ip;
                        
                        StationEntry ste = new StationEntry( st_id, ver, ip, true, (this instanceof UDP_Communicator) );
                        boolean found = false;
                        boolean dbl_id = false;
                        for (int i = 0; i < st_list.size(); i++)
                        {
                            StationEntry stx = st_list.get(i);
                            if (stx.get_id() == ste.get_id())
                            {
                                if (stx.get_ip().compareTo( ste.get_ip() ) == 0)
                                {
                                    found = true;
                                    break;
                                }
                                else
                                {
                                    stx.set_dbl(true);
                                    ste.set_dbl(true);
                                }
                            }                                                        
                        }
                            
                        if (!found)
                            st_list.add( ste );    
                    }
                    catch (Exception exc)
                    {
                        exc.printStackTrace();
                    }
                }
            }
        }        
    }        
}
