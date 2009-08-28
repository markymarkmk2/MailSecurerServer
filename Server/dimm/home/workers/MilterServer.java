/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;

import home.shared.hibernate.Milter;
import dimm.home.importmail.MilterImporter;
import dimm.home.mailarchiv.*;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.Timer;




/**
 *
 * @author Administrator
 */
public class MilterServer extends WorkerParent
{
    
    
    public static final String NAME = "MilterServer";

    
    Timer timer;
    final ArrayList<MilterImporter> milter_list;
    SwingWorker idle_worker;

    boolean m_Stop = false;
    
    
    /** Creates a new instance of StatusDisplay */
    public MilterServer()
    {        
        super(NAME);
        milter_list = new ArrayList<MilterImporter>();
    }
    
    @Override
    public boolean initialize()
    {
        return true;
    }

    public void set_milter_list(Milter[] milter_array) throws IOException
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        // TODO:
        // STOP OLD PROCESSES, RESTART NEW

        milter_list.clear();
        for (int i = 0; i < milter_array.length; i++)
        {
            milter_list.add( new MilterImporter( milter_array[i] ));
        }
    }
    public void add_milter(Milter milter) throws IOException
    {
        // FORMAT Protokoll (POP3/SMTP/IMAP) Localport Server  Remoteport
        // TODO:
        // STOP OLD PROCESSES, RESTART NEW

        milter_list.add( new MilterImporter( milter ));
    }

    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting " + milter_list.size() + " milter tasks" );

        if (!Main.get_control().is_licensed())
        {
            this.setStatusTxt("Not licensed");
            this.setGoodState(false);
            return false;
        }

        m_Stop = false;

        for (int i = 0; i < milter_list.size(); i++)
        {
            final MilterImporter pe = milter_list.get(i);

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
            synchronized(milter_list)
            {
                if ( m_Stop && milter_list.isEmpty())
                    break;


                for (int i = 0; i < milter_list.size(); i++)
                {
                    MilterImporter m = milter_list.get(i);
                    m.idle_check();
                }
                if (this.isGoodState())
                {
                     this.setStatusTxt("");
                }
            }
        }
        for (int i = 0; i < milter_list.size(); i++)
        {
            MilterImporter m = milter_list.get(i);
            m.finish();
        }

    }

    public String get_milter_status_txt()
    {
        StringBuffer stb = new StringBuffer();

        // CLEAN UP LIST OF FINISHED CONNECTIONS
        for (int i = 0; i < milter_list.size(); i++)
        {
            MilterImporter pe = milter_list.get(i);
  /*          stb.append("PXPT"); stb.append(i); stb.append(":"); stb.append(pe.getProtokoll());
            stb.append(" PXPR"); stb.append(i); stb.append(":"); stb.append(pe.getRemotePort() );
            stb.append(" PXPL"); stb.append(i); stb.append(":"); stb.append(pe.getLocalPort() );
            stb.append(" PXIN"); stb.append(i); stb.append(":"); stb.append(pe.getInstanceCnt()  );
            stb.append(" PXHO"); stb.append(i); stb.append(":'"); stb.append(pe.getRemoteServer() + "' " );*/
        }

        return stb.toString();
    }


    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }


}
