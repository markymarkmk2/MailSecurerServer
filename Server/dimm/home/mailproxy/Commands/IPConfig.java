/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxy.Commands;

import java.io.File;
import java.io.FileWriter;
import dimm.home.mailproxy.Main;
import dimm.home.mailproxy.Preferences;
import dimm.home.mailproxy.Utilities.CmdExecutor;
import dimm.home.mailproxy.Utilities.ParseToken;

/**
 *
 * @author Administrator
 */
public class IPConfig extends AbstractCommand
{
    
    /** Creates a new instance of HelloCommand */
    public IPConfig()
    {
        super("IPCONFIG");
    }

    public boolean do_command(String data)
    {
        answer = "";
        
        String opt = get_opts( data );
        
        ParseToken pt = new ParseToken(opt);
        
        String command = pt.GetString("CMD:");
        if (command == null)
        {
            answer = MISS_ARGS;
            return false;
        }
        
        if ( command.compareTo("GET") == 0)
        {
            int eth_nr = pt.GetLong("IF:").intValue();
            String ip = Main.get_prop( Preferences.IP, eth_nr );
            String mask = Main.get_prop( Preferences.MASK, eth_nr );
            String dhcp = Main.get_prop( Preferences.DHCP, eth_nr );
            String gw = Main.get_prop( Preferences.GW, eth_nr );
            String dns = Main.get_prop( Preferences.DNS, eth_nr );
            if (ip == null || dhcp == null || gw == null || dns == null)
            {
                answer = WRONG_ARGS;
                return false;                
            }
            
            String px_enable = Main.get_prop( Preferences.PXENABLE );
            String px_server = Main.get_prop( Preferences.PXSERVER, "" );
            String px_port = Main.get_prop( Preferences.PXPORT, "3128" );
            String px_socksport = Main.get_prop( Preferences.PXSOCKSPORT, "1080" );
 
            answer = "IF:" + eth_nr + "IP:" + ip + " MASK:" + mask + " DHCP:" + dhcp + " GW:" + gw + " DNS:" + dns +
                        " PXE:" + px_enable + " PXS:" + px_server + " PXP:" + px_port + " PXSP:" + px_socksport;             
            return true;
        }
        
        if ( command.compareTo("SET") == 0)
        {
            int eth_nr = pt.GetLong( "IF:" ).intValue();
            String ip = pt.GetString( "IP:" );
            String mask = pt.GetString( "MASK:" );
            boolean dhcp = pt.GetBoolean( "DHCP:" );
            String gw = pt.GetString( "GW:" );
            String dns = pt.GetString( "DNS:" );
            String px_enable = pt.GetString( "PXE:" );
            String px_server = pt.GetString( "PXS:" );
            String px_port = pt.GetString( "PXP:" );
            String px_socksport = pt.GetString( "PXSP:" );
            
            
            if (set_ipconfig( eth_nr, dhcp, ip, mask, gw, dns ))
            {
                // NICHT DIE NUMMER �BERNEHMEN, DAS IST ZU RISKANT
                //Main.set_long_prop( Preferences.NETINTERFACE, eth_nr );
                Main.set_prop( Preferences.IP, ip, eth_nr );
                Main.set_prop( Preferences.MASK, mask, eth_nr );
                Main.set_prop( Preferences.DHCP, dhcp? "1":"0", eth_nr );
                Main.set_prop( Preferences.GW, gw, eth_nr );
                Main.set_prop( Preferences.DNS, dns, eth_nr );

                Main.set_prop( Preferences.PXENABLE, px_enable );
                Main.set_prop( Preferences.PXSERVER, px_server );
                Main.set_prop( Preferences.PXPORT, px_port );
                Main.set_prop( Preferences.PXSOCKSPORT, px_socksport );
                
                if (Main.is_proxy_enabled() && Main.get_long_prop(Preferences.PXSOCKSPORT) > 0)
                {
                    System.setProperty("proxyPort",Main.get_prop(Preferences.PXSOCKSPORT));
                    System.setProperty("proxyHost",Main.get_prop(Preferences.PXSERVER));        
                }
                else
                {
                    System.setProperty("proxyPort","");
                    System.setProperty("proxyHost","");        
                }
                
                SetStation set_station = new SetStation();
                
                // REWRITE VPN CONFIG
                set_station.write_new_vpn_conf( Main.get_station_id() );
                                                
                if (Main.write_prefs())
                    return true;
            }
        }        
        if ( command.compareTo("REAL") == 0)
        {

            // TRY TO GET VPN IP
            String cmd1[]  = {"ifconfig"  };
            CmdExecutor exe = new CmdExecutor( cmd1 );        
            int retcode = exe.exec();
            if (retcode == 0)
            {
                answer = "IP:'" + exe.get_out_text() + "'";
            }
            else
            {
                answer = "IP:'not existant: " + exe.get_err_text() + "'";
            }            
            String cmd2[]  = {"route" };
            exe = new CmdExecutor( cmd2 );        
            retcode = exe.exec();
            if (retcode == 0)
            {
                answer += " RT:'" + exe.get_out_text() + "'";
            }
            else
            {
                answer += " RT:'failed: " + exe.get_err_text() + "'";
            }            
            return true;
        }
        
        
        if (answer.length() == 0)
            answer = WRONG_ARGS;
        
        return false;
    }

    public boolean set_ipconfig(int eth_nr, boolean dhcp, String ip, String mask, String gw, String dns)
    {
        
        if (System.getProperty("os.name").startsWith("Windows"))
        {
            System.out.println("Ignoring netconfig on windows");
            return true;
        }
        
        File conf_dir = new File("/etc/conf.d");
        if (conf_dir.exists())
        {
            return set_ipconfig_gentoo( eth_nr, dhcp, ip, mask, gw, dns );
        }
        
        conf_dir = new File("/etc/network");
        if (conf_dir.exists())
        {
            return set_ipconfig_debian(eth_nr, dhcp, ip, mask, gw, dns );
        }
        
        System.out.println("Unknown netconfig");
        return false;
    }
    
    public boolean set_ipconfig_debian(int eth_nr, boolean dhcp, String ip, String mask, String gw, String dns)
    {
        
        String conf_file = "/etc/network/interfaces";
        
        String lo_str = "# The loopback network interface\nauto lo tap0 eth0\niface lo inet loopback\n"; //iface tap0 inet manual";
        
        try
        {
            if (dhcp)
            {
                String config_str = "allow-hotplug eth0\niface eth0 inet dhcp\n";              
                FileWriter fw = new FileWriter( conf_file ); 
                fw.write(lo_str + config_str);
                fw.close();
                
                
            }
            else
            {
                // ONLY CLASS C!!!
                int last_point_idx = ip.lastIndexOf(".");
                String broadcast = ip.substring(0, last_point_idx) + ".255";
                
                String config_str = "iface eth0 inet static\n\taddress " + ip + "\n\tnetmask " + mask + "\n\tbroadcast " + broadcast + "\n\tgateway "+gw + "\n";

              
                FileWriter fw = new FileWriter( conf_file ); 
                fw.write(lo_str + config_str);
                fw.close();
                
                fw = new FileWriter( "/etc/resolv.conf"  );
                fw.write( "nameserver " + dns + "\n" );
                fw.close();                                
            }
        }
        catch (Exception exc)
        {
            answer = "INET_NOK";
            Main.err_log( "Writing Inet config failed: " + exc.getMessage() );
            return false;
        }
                
         CmdExecutor exe;
        
        // JETZT NETZWERK NEU STARTEN
        File startup_file = new File("/etc/init.d/networking");
            
        if (startup_file.exists())
        {
            String cmd1[] = {startup_file.getAbsolutePath(), "restart" };

            exe = new CmdExecutor( cmd1 );       
            exe.exec();
        }
        
        String real_ip = get_ip_for_if( "eth" + eth_nr );
        if (real_ip != null)
        {
            Main.debug_msg( 0, "Got real IP <" + real_ip + ">" );
        }
        else
        {
            Main.err_log( "Cannot detect valid IP, setting to fallback IP: 192.168.201.201");
            
            if (dhcp)
            {
                set_ipconfig( eth_nr, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
                answer = "INET_NOK";
                return false;
            }
        }
        
        if (!is_route_ok())
        {
            Main.err_log( "Invalid routing detected, setting to fallback IP: 192.168.201.201");

            set_ipconfig( eth_nr, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
            answer = "INET_NOK";
            return false;
        }
                
        

        // TRY TO GET A PING
        String cmd3[]  = {"ping", "-c", "1", Main.get_prop(Preferences.SERVER, Main.DEFAULTSERVER) };
        
        exe = new CmdExecutor( cmd3 );
        
        int retcode = exe.exec();
        answer = (retcode == 0) ? "INET_OK": "INET_NOK";
        
       
        
        String vpn_ip = get_ip_for_if( "tap0" );
        if (vpn_ip != null)
        {
            Main.debug_msg( 0, "Got vpn IP <" + vpn_ip + ">" );
            //Main.get_control().get_sql_worker().set_boxdata( vpn_ip );
        }
        
        
        answer = (retcode == 0) ? "INET_OK": "INET_NOK";
        
        return true;
        
    }    

    public boolean set_ipconfig_gentoo(int eth_nr, boolean dhcp, String ip, String mask, String gw, String dns)
    {
        
        String conf_file = "/etc/conf.d/net";
        
        // VORBEDINGUNGEN PR�FEN:
                
                
        try
        {
            if (dhcp)
            {
                String config_str = "config_eth" + eth_nr + "=( \"dhcp\"  )\n" +
                        "tuntap_tap0=\"tap\"\nconfig_tap0=(\"null\")\n";

                FileWriter fw = new FileWriter( conf_file ); 
                fw.write(config_str);
                fw.close();
                
                
            }
            else
            {
                // ONLY CLASS C!!!
                int last_point_idx = ip.lastIndexOf(".");
                String broadcast = ip.substring(0, last_point_idx) + ".255";

                String config_str = "config_eth" + eth_nr + "=( \"" + ip + " netmask " + mask + " brd " + broadcast + "\" )\nroutes_eth" + eth_nr + "=( \"default via " + gw + "\" )\n" +
                        "tuntap_tap0=\"tap\"\nconfig_tap0=(\"null\")\n";
                
                FileWriter fw = new FileWriter( conf_file ); 
                fw.write(config_str);
                fw.close();
                
                fw = new FileWriter( "/etc/resolv.conf"  );
                fw.write( "nameserver " + dns + "\n" );
                fw.close();                                
            }
        }
        catch (Exception exc)
        {
            answer = "INET_NOK";
            Main.err_log( "Writing Inet config failed: " + exc.getMessage() );
            return false;
        }
                
        
        
        CmdExecutor exe;
        
        // JETZT NETZWERK NEU STARTEN
        File startup_file = new File("/etc/init.d/net.eth"  + eth_nr);
        if (!startup_file.exists())
            startup_file = new File("/etc/init.d/net");
        if (!startup_file.exists())
            startup_file = new File("/etc/init.d/networking");
            
        if (startup_file.exists())
        {
            String cmd1[] = {startup_file.getAbsolutePath(), "restart" };

            exe = new CmdExecutor( cmd1 );       
            exe.exec();
        }
        
        String real_ip = get_ip_for_if( "eth" + eth_nr );
        if (real_ip != null)
        {
            Main.debug_msg( 0, "Got real IP <" + real_ip + ">" );
        }
        else
        {
            Main.err_log( "Cannot detect valid IP, setting to fallback IP: 192.168.201.201");
            
            if (dhcp)
            {
                set_ipconfig( eth_nr, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
                answer = "INET_NOK";
                return false;
            }
        }
        
        if (!is_route_ok())
        {
            Main.err_log( "Invalid routing detected, setting to fallback IP: 192.168.201.201");

            set_ipconfig( eth_nr, /*dhcp*/ false, "192.168.201.201", "255.255.255.0", "192.168.201.202", "192.168.201.201");
            answer = "INET_NOK";
            return false;
        }
                
        

        // TRY TO GET A PING
        String cmd3[]  = {"ping", "-c", "1", Main.get_prop(Preferences.SERVER, Main.DEFAULTSERVER) };
        
        exe = new CmdExecutor( cmd3 );
        
        int retcode = exe.exec();
        answer = (retcode == 0) ? "INET_OK": "INET_NOK";
        
       
        
        String vpn_ip = get_ip_for_if( "tap0" );
        if (vpn_ip != null)
        {
            Main.debug_msg( 0, "Got vpn IP <" + vpn_ip + ">" );
            //Main.get_control().get_sql_worker().set_boxdata( vpn_ip );
        }
        
        
        answer = (retcode == 0) ? "INET_OK": "INET_NOK";
        
        return true;
    }

    public String get_ifconfig_val( String ip_if, String token )
    {
        String val = null;
        
        // TRY TO GET VPN IP
        String cmd4[]  = {"ifconfig " + ip_if + " | grep '" + token + "'"  };
        CmdExecutor exe = new CmdExecutor( cmd4 );        
        int retcode = exe.exec();
        if (retcode == 0)
        {
            
            try
            {
                int ip_idx = exe.get_out_text().indexOf( token + ":");
                if (ip_idx >= 0)
                {
                    String ip_txt = exe.get_out_text().substring( ip_idx + 10 );
                    
                    int end_idx = ip_txt.indexOf(" ");
                    if (end_idx > 0)
                        val = ip_txt.substring(0, end_idx );
                }
            }
            catch (Exception exc)
            {
            }
        }
        return val;
    }
    
    public String get_ip_for_if( String ip_if )
    {
        String ip = get_ifconfig_val( ip_if, "inet addr" );
        return ip;
    }
    public String get_mask_for_if( String ip_if )
    {
        String mask = get_ifconfig_val( ip_if, "Mask" );
        return mask;
    }
      
    // CHECKS FOR FLAG UG (UP GATEWAY) IN ROUTE-OUTPUT
    public boolean is_route_ok( )
    {
        boolean ret = false;
        
        // TRY TO GET VPN IP
        String cmd4[]  = {"route -n | grep 'UG'"  };
        CmdExecutor exe = new CmdExecutor( cmd4 );        
        int retcode = exe.exec();
        if (retcode == 0)
        {            
            try
            {
                int flag_idx = exe.get_out_text().indexOf("UG");
                if (flag_idx >= 0)
                {
                    ret = true;
                }
            }
            catch (Exception exc)
            {
            }
        }
        return ret;
    }
    
    public boolean set_programmed_ipconfig()
    {
        int eth_nr = (int)Main.get_long_prop(Preferences.NETINTERFACE );
        String ip = Main.get_prop( Preferences.IP, eth_nr );
        String mask = Main.get_prop( Preferences.MASK, eth_nr );
        boolean dhcp = Main.get_long_prop( Preferences.DHCP, eth_nr ) > 0 ? true : false;
        String gw = Main.get_prop( Preferences.GW, eth_nr );
        String dns = Main.get_prop( Preferences.DNS, eth_nr );
        if (ip == null || gw == null || dns == null)
        {            
            return false;                
        }
                
        
        if (set_ipconfig( eth_nr, dhcp, ip, mask, gw, dns ))
        {

            if (Main.is_proxy_enabled() && Main.get_long_prop(Preferences.PXSOCKSPORT) > 0)
            {
                System.setProperty("proxyPort",Main.get_prop(Preferences.PXSOCKSPORT));
                System.setProperty("proxyHost",Main.get_prop(Preferences.PXSERVER));        
            }
            else
            {
                System.setProperty("proxyPort","");
                System.setProperty("proxyHost","");        
            }

            SetStation set_station = new SetStation();

            // REWRITE VPN CONFIG
            if (set_station.write_new_vpn_conf( Main.get_station_id() ))            
            {
                // ONLY RETURN TRUE IF EVERYTHING WORKS
                return true;
            }
        }
        return false;
        
    }
    
    
}
