/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;



import dimm.home.index.IMAP.jimap.ImapServer;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.ArrayList;





/**
 *
 * @author mw
 */



public class IMAPBrowser implements WorkerParentChild
{

    ServerSocket sock;
    MandantContext m_ctx;
    final ArrayList<ImapServer> srv_list;

    public IMAPBrowser( MandantContext m_ctx, String host, int port) throws IOException
    {
        this.m_ctx = m_ctx;
        log_debug(Main.Txt("Opening_socket"));

        sock = new ServerSocket(port, 0, InetAddress.getByName(host));

        do_finish = false;
        srv_list = new ArrayList<ImapServer>();

    }
    
    private void log_debug( String s )
    {
        LogManager.debug_msg( s );
    }
    private void log_debug( String s, Exception e )
    {
        LogManager.debug_msg( s, e );
    }



    boolean do_finish;

    @Override
    public void finish()
    {
        do_finish = true;
        try
        {
            sock.close();
            synchronized( srv_list )
            {
                for (int i = 0; i < srv_list.size(); i++)
                {
                    ImapServer imapServer = srv_list.get(i);
                    imapServer.close();
                }
            }
        }
        catch (IOException ex)
        {
        }
    }

    @Override
    public void run_loop()
    {
        while (!do_finish)
        {           
            try
            {
                log_debug(Main.Txt("Going_to_accept"));
                Socket cl = sock.accept();

                ImapServer srv = new ImapServer(m_ctx, cl, false);
                srv.start();
                synchronized( srv_list )
                {
                    srv_list.add(srv);
                }
            }
            catch (IOException e)
            {
                log_debug(Main.Txt("Unexpected_exception"), e);
            }
        }
    }
    @Override
    public void idle_check()
    {
        synchronized(srv_list)
        {
            for (int i = 0; i < srv_list.size(); i++)
            {
                ImapServer sr = srv_list.get(i);
                if (sr.isAlive())
                {
                    srv_list.remove(i);
                    i = -1;
                    continue;
                }
            }
        }
    }
    
    public int getInstanceCnt()
    {
        int r = 0;
        synchronized(srv_list)
        {
            r = srv_list.size();
        }
        return r;
    }
}

