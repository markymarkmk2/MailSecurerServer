/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import dimm.home.index.IMAP.IMAPBrowser;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.Timer;




/**
 *
 * @author Administrator
 */
public class IMAPBrowserServer extends WorkerParent
{
    public static final String NAME = "IMAPBrowserServer";
    
    Timer timer;
    final ArrayList<IMAPBrowser> browser_list;
    SwingWorker idle_worker;

    boolean m_Stop = false;
    
    
    /** Creates a new instance of StatusDisplay */
    public IMAPBrowserServer()
    {        
        super(NAME);
        browser_list = new ArrayList<IMAPBrowser>();
    }
    
    @Override
    public boolean initialize()
    {
        return true;
    }

   
    public void add_browser(MandantContext m_ctx, String host, int port) throws IOException
    {
        IMAPBrowser brw = new IMAPBrowser( m_ctx, host,  port);
        browser_list.add( brw );
    }

    public void remove_browser( MandantContext ctx, String host, int imap_port )
    {
        for (int i = 0; i < browser_list.size(); i++)
        {
            IMAPBrowser brw = browser_list.get(i);
            if (brw.getHost() == host && brw.get_ctx() == ctx && brw.getPort() == imap_port)
            {
                brw.finish();
                browser_list.remove(brw);
                break;
            }
        }
    }


    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting " + browser_list.size() + " milter tasks" );

        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }

        m_Stop = false;

        for (int i = 0; i < browser_list.size(); i++)
        {
            final IMAPBrowser pe = browser_list.get(i);

            SwingWorker worker = new SwingWorker()
            {
                @Override
                public Object construct()
                {

                    pe.run_loop();

                    return null;
                }
            };

            worker.start();
        }

        idle_worker = new SwingWorker()
        {
            @Override
            public Object construct()
            {
                do_idle();

                return null;
            }
        };

        idle_worker.start();

       this.setStatusTxt("Running");
       this.setGoodState(true);
       return true;
    }

    void do_idle()
    {
        while (!this.isShutdown())
        {
            LogicControl.sleep(1000);

            // CLEAN UP LIST OF FINISHED CONNECTIONS
            synchronized(browser_list)
            {
                if ( m_Stop && browser_list.isEmpty())
                    break;


                for (int i = 0; i < browser_list.size(); i++)
                {
                    IMAPBrowser m = browser_list.get(i);
                    m.idle_check();
                }
                if (this.isGoodState())
                {
                     this.setStatusTxt("");
                }
            }
        }
        for (int i = 0; i < browser_list.size(); i++)
        {
            IMAPBrowser m = browser_list.get(i);
            m.finish();
        }

    }



    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    @Override
    public String get_task_status()
    {
        StringBuffer stb = new StringBuffer();

        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < browser_list.size(); i++)
        {
            IMAPBrowser pe = browser_list.get(i);
  /*          stb.append("PXPT"); stb.append(i); stb.append(":"); stb.append(pe.getProtokoll());
            stb.append(" PXPR"); stb.append(i); stb.append(":"); stb.append(pe.getRemotePort() );
            stb.append(" PXPL"); stb.append(i); stb.append(":"); stb.append(pe.getLocalPort() );
            stb.append(" PXIN"); stb.append(i); stb.append(":"); stb.append(pe.getInstanceCnt()  );
            stb.append(" PXHO"); stb.append(i); stb.append(":'"); stb.append(pe.getRemoteServer() + "' " );*/
        }

        return stb.toString();
    }



}
