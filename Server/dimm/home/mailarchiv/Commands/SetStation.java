/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import java.io.File;
import java.io.FileWriter;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Preferences;
import dimm.home.mailarchiv.Utilities.CmdExecutor;
import dimm.home.mailarchiv.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class SetStation extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public SetStation()
    {
        super("SETSTATION");
    }

    public boolean do_command(String data)
    {
        String opt = get_opts( data );
        answer = "";
        
        ParseToken pt = new ParseToken(opt);
        
        int new_id = pt.GetLong("ID:").intValue();
        
        if (write_new_vpn_conf( new_id))
        {
            Main.set_long_prop(Preferences.STATION_ID, new_id );
            Main.write_prefs();
            return true;
        }
        return false;
    }
 
    
    public boolean write_new_vpn_conf(int new_id)
    {
        return true;
/*        
        StringBuffer sb = new StringBuffer();
        String vpn_server = Main.get_prop(Preferences.VPN_SERVER, Main.DEFAULTSERVER);
        int vpn_port = (int)Main.get_long_prop(Preferences.VPN_PORT, (long) 1196);
        
        String vpn_conf_path = new File(Main.CERTPATH).getAbsolutePath();
        
        File vpn_zertifikat = new File (vpn_conf_path + "/client" + new_id +".crt" );
        if (!vpn_zertifikat.exists())
        {
            answer = "Es gibt kein Zertifikat fuer diese Stations-ID, die Stations-ID kann nicht uebernommen werden";
            Main.err_log(answer);
            return false;
        }
        
        sb.append( "dev tap\n" );
        sb.append( "tls-client\n" );
        sb.append( "ca "   + vpn_conf_path + "/ca.crt\n" );
        sb.append( "key "  + vpn_conf_path + "/client" + new_id +".key\n" );
        sb.append( "cert " + vpn_conf_path + "/client" + new_id +".crt\n" );
        
        sb.append( "pull\n" );
        sb.append( "port " + vpn_port + "\n" );
        sb.append( "proto tcp-client\n" );
        sb.append( "remote " + vpn_server + "\n" );
        sb.append( "resolv-retry infinite\n" );
        sb.append( "persist-key\n" );
        sb.append( "persist-tun\n" );
        sb.append( "comp-lzo\n" );
        
        String px_enable = Main.get_prop( Preferences.PXENABLE );
        if (px_enable != null && px_enable.length() > 0 && px_enable.charAt(0) == '1')
        {
            sb.append( "http-proxy " + Main.get_prop( Preferences.PXSERVER ) + " " + Main.get_prop( Preferences.PXPORT ) );
        }
        
        
        try
        {
            String conf_file = "/etc/openvpn/openvpn.conf";
            FileWriter fw = new FileWriter( conf_file ); 
            fw.write(sb.toString());
            fw.close();
        }
        catch (Exception exc)
        {
            answer = "Writing VPN config failed: " + exc.getMessage() ;
            Main.err_log( answer );
            return false;
        }
        
        
        String cmd1[] = {"/etc/init.d/openvpn", "restart" };

        CmdExecutor exe = new CmdExecutor( cmd1 );       
        exe.exec();
        
        String cmd4[]  = {"ifconfig tap0 | grep 'inet addr'"  };
        exe = new CmdExecutor( cmd4 );        
        int retcode = exe.exec();
        
        // VPN-IP IN DB EINTRAGEN
        if (retcode == 0)
        {
            String vpn_ip = "";
            
            try
            {
                int ip_idx = exe.get_out_text().indexOf("inet addr:");
                if (ip_idx >= 0)
                {
                    String ip_txt = exe.get_out_text().substring( ip_idx + 10 );
                    
                    int end_idx = ip_txt.indexOf(" ");
                    if (end_idx > 0)
                        vpn_ip = ip_txt.substring(0, end_idx );
                }
            }
            catch (Exception exc)
            {
                Main.err_log( "Cannot determine VPN-IP: " + exc.getMessage() );
            }
            if (vpn_ip.length() > 0)
            {
                Main.get_control().get_sql_worker().set_boxdata( vpn_ip );
            }
        }

        return true;
*/ 
    }    
    
}
