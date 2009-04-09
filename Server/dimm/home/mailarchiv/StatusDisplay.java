/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.RandomAccessFile;
import javax.swing.Timer;




/**
 *
 * @author Administrator
 */
public class StatusDisplay extends WorkerParent implements ActionListener
{
    boolean flash_blink;
//    boolean sync_blink;    
    boolean blinker;
    boolean server_ok;
    boolean router_ok;
    boolean dl_active;
    
    
    // BITPOS AUF LPT-PORT
    byte download_status = 0x01;
	byte router_conn_status = 0x02;
	byte server_conn_status = 0x04;
    public static final String NAME = "StatusDisplay";

    
    Timer timer;
    
    int[] portlist = { 0x378, 0x3bc, 0x278 };
    
    /** Creates a new instance of StatusDisplay */
    public StatusDisplay()
    {        
        super(NAME);
        timer = new Timer( 500, this );                      
    }
    
    public boolean initialize()
    {
        flash_blink = false;
        dl_active = false;    
        blinker = false;
        server_ok = false;
        router_ok = false;        
        
        return true;
    }
    
    public boolean start_run_loop()
    {
        update_status_txt();   
        return true;
    }
    
    void set_server_ok( boolean b )
    {
        server_ok = b;
        set_status_led_port();
        update_status_txt();                        
    }
    void set_router_ok( boolean b )
    {
        router_ok = b;
        set_status_led_port();
        update_status_txt();                        
        
    }
                
    
    void set_download_active( boolean b )
    {
        dl_active = b;
        set_status_led_port();
        update_status_txt();                        
    }
    
    void update_status_txt()
    {
        StringBuffer sb = new StringBuffer();
        if (flash_blink)
            sb.append("Flash blink!");
        else 
        {
            if (dl_active)
                sb.append("Downloading ");
            if (server_ok)    
                sb.append("Connected ");
            if (router_ok)    
                sb.append("Internet ");
        }

        this.setStatusTxt( sb.toString() );
    }
    
    public void set_flash_led( boolean b)
    {
        flash_blink = b;
        if (b)
        {
            timer.start();
        }
        else
        {
            timer.stop();
            set_flash_led_port( false );
            set_status_led_port();
        }
        update_status_txt();                        
    }

    public void actionPerformed(ActionEvent actionEvent)
    {
        set_flash_led_port( blinker );
        
        blinker = !blinker;
    }

   
    private void set_status_led_port()
    {
        }
    
    private void set_flash_led_port(boolean b)
    {
    }    

    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }


}
