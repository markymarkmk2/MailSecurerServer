/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import home.shared.hibernate.ImapFetcher;
import dimm.home.importmail.MailBoxFetcher;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.util.ArrayList;
import javax.swing.Timer;




/**
 *
 * @author Administrator
 */
public class MailBoxFetcherServer extends WorkerParent
{
    
    
    public static final String NAME = "MailBoxFetcherServer";

    
    Timer timer;
    final ArrayList<MailBoxFetcher> fetcher_list;
    SwingWorker idle_worker;

    boolean m_Stop = false;
    
    
    /** Creates a new instance of StatusDisplay */
    public MailBoxFetcherServer()
    {        
        super(NAME);
        fetcher_list = new ArrayList<MailBoxFetcher>();
    }
    
    @Override
    public boolean initialize()
    {
        return true;
    }

    public void set_fetcher_list(ImapFetcher[] if_array) throws Exception
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        // TODO:
        // STOP OLD PROCESSES, RESTART NEW

        fetcher_list.clear();
        for (int i = 0; i < if_array.length; i++)
        {
            ImapFetcher fetcher = if_array[i];
            fetcher_list.add( MailBoxFetcher.mailbox_fetcher_factory(fetcher) );
        }
    }
    public void add_fetcher(ImapFetcher fetcher)
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        // TODO:
        // STOP OLD PROCESSES, RESTART NEW

        fetcher_list.add( MailBoxFetcher.mailbox_fetcher_factory(fetcher) );
    }
    public void remove_fetcher( ImapFetcher imap_fetcher )
    {
        for (int i = 0; i < fetcher_list.size(); i++)
        {
            MailBoxFetcher if_entry = fetcher_list.get(i);
            if (if_entry.get_imap_fetcher() == imap_fetcher)
            {
                if_entry.finish();
                fetcher_list.remove(if_entry);
                break;
            }
        }
    }


    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting " + fetcher_list.size() + " MailBoxImport tasks" );

        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }

        m_Stop = false;

        for (int i = 0; i < fetcher_list.size(); i++)
        {
            final MailBoxFetcher hf = fetcher_list.get(i);

            SwingWorker worker = new SwingWorker()
            {
                @Override
                public Object construct()
                {

                    hf.run_loop();

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
            synchronized(fetcher_list)
            {
                if ( m_Stop && fetcher_list.isEmpty())
                    break;


                for (int i = 0; i < fetcher_list.size(); i++)
                {
                    MailBoxFetcher hf = fetcher_list.get(i);
                    hf.idle_check();
                }
                if (this.isGoodState())
                {
                     this.setStatusTxt("");
                }
            }
        }
        for (int i = 0; i < fetcher_list.size(); i++)
        {
            MailBoxFetcher hf = fetcher_list.get(i);
            hf.finish();
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
        for (int i = 0; i < fetcher_list.size(); i++)
        {
            MailBoxFetcher hf = fetcher_list.get(i);

            stb.append("HFST"); stb.append(i); stb.append(":"); stb.append(hf.get_status_txt() );
/*            stb.append(" PXPR"); stb.append(i); stb.append(":"); stb.append(pe.getRemotePort() );
            stb.append(" PXPL"); stb.append(i); stb.append(":"); stb.append(pe.getLocalPort() );
            stb.append(" PXIN"); stb.append(i); stb.append(":"); stb.append(pe.getInstanceCnt()  );
            stb.append(" PXHO"); stb.append(i); stb.append(":'"); stb.append(pe.getRemoteServer() + "' " );*/
        }

        return stb.toString();
    }



}
