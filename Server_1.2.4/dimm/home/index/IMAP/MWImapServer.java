/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import java.net.*;
import java.io.*;
import java.util.*;




public class MWImapServer extends Thread
{
    private final static String RESTAG = "* ";
    boolean shutdown = false;

    
   void add( ImapCmd cmd )
   {
        if (cmd_map == null)
        {
            cmd_map = new HashMap<String, ImapCmd>();
        }
        cmd_map.put(cmd.getCmd(), cmd);
    }

    PrintWriter out;
    BufferedReader in;
    Socket s = null;
    private MailKonto konto = null;
    private MailFolder mailfolder = null;
    boolean trace = false;
    int con = 0;
    MandantContext m_ctx;
    private boolean has_searched = false;
    IMAPBrowser parent;

    static HashMap<String, ImapCmd> cmd_map;

    public MWImapServer( IMAPBrowser parent, MandantContext m_ctx, Socket s, boolean trace )
    {
        super("ImapServer for " + s.getRemoteSocketAddress().toString());

        this.parent = parent;
        this.s = s;
        this.m_ctx = m_ctx;
        this.trace = trace;

        add( new Capability() );
        add( new Check() );
        add( new Examine());
        add( new Close());
        add( new Fetch());
        add( new Idle());
        add( new List());
        add( new Login());
        add( new Logout());
        add( new Lsub());
        add( new Noop());
        add( new Select());
        add( new Status());
        add( new Subscribe());
        add( new Uid());
        add( new Unsubscribe());
    }
    public MailKonto get_konto()
    {
        return konto;
    }
    void set_konto( MailKonto mailKonto )
    {
        if (konto != null)
            konto.close();
        
        konto = mailKonto;
    }

    public MailFolder get_selected_folder()
    {
        return mailfolder;
    }
    void set_selected_folder( MailFolder folder )
    {
        mailfolder = folder;
    }

    public IMAPBrowser get_parent()
    {
        return parent;
    }

    public boolean has_searched()
    {
        return has_searched;
    }

    public void set_has_searched( boolean has_searched )
    {
        this.has_searched = has_searched;
    }

    

    void write( String message )
    {
        if (trace)
        {
            System.out.println( "Out: " + message);
        }
        message += "\r\n";
        out.write(message);
        out.flush();
    }

    void rawwrite( String message )
    {
        if (trace)
        {
            System.out.print( message);
        }
        out.write(message);
        out.flush();
    }

   void rawwrite( byte[] data, int start, int len ) throws IOException
    {
        if (trace)
        {
            System.out.print( new String( data, start, len ));
        }
        s.getOutputStream().write( data, start, len);
        s.getOutputStream().flush();
    }

    void response( String message )
    {
        write(RESTAG + message);
    }

    void response( String sid, boolean ok, String message )
    {
        String res = "";
        if (sid == null)
        {
            res += "* ";
        }
        else
        {
            res += sid + " ";
        }
        if (ok)
        {
            res += "OK ";
        }
        else
        {
            res += "NO ";
        }
        write(res + message);
    }

    private int techno( String line ) throws IOException
    {
        line = line.trim();

        if (trace)
            System.out.println( "In: " + line );

        int i = line.indexOf(" ");
        if (i > 0)
        {
            String cmd = "";
            String par = "";
            String sid = line.substring(0, i);
            String rest = line.substring(i + 1).trim();
            i = rest.indexOf(" ");
            if (i < 0)
            {
                cmd = rest.toLowerCase();
            }
            else
            {
                cmd = rest.substring(0, i).trim().toLowerCase();
                par = rest.substring(i + 1).trim();
            }

            int result = -1;
            //System.out.println("In: [" + sid + "]  "+cmd);
            ImapCmd imapCmd = cmd_map.get( cmd );
            if (imapCmd != null)
            {
                result = imapCmd.action(this, sid, par);
            }
            else
            {
                response(sid, false, "unknown command");
                result = 1;
            }
            return result;
        }
        return 1;
    }

    @Override
    public void run()
    {
        try
        {
            out = new PrintWriter(s.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            
            response(null, true, "MailSecurer IMAP4 V" + Main.get_version_str());

            while (!shutdown)
            {
                String line = null;

                line = in.readLine();

                if (line == null)
                {
                    break;
                }

                line = line.trim();
                if (!line.equals(""))
                {
                    techno(line);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            close();
        }
    }


    public static String cleanup( String name )
    {
        char org[] = name.toCharArray();
        int i, j;
        //clean from spaces etc
        for (i = 0, j = 0; i < org.length; i++)
        {
            if (('a' <= org[i] && org[i] <= 'z') || ('A' <= org[i] && org[i] <= 'Z') || ('0' <= org[i] && org[i] <= '9') || '_' == org[i])
            {
                if (j < i)
                {
                    org[j] = org[i];
                }
                j++;
            }
        }
        return new String(org, 0, j);
    }
    public void close()
    {
        if (konto != null)
        {
            parent.clear_cache( konto.get_username() );
            konto.close();
            konto = null;
        }
        if (mailfolder != null)
        {
            mailfolder.close();
            mailfolder = null;
        }

        if (out != null)
        {
            out.close();
            out = null;
        }
        if (in != null)
        {
            try
            {
                in.close();
            }
            catch (IOException iOException)
            {
            }
            in = null;
        }
        if (s != null)
        {
            try
            {
                s.close();
            }
            catch (IOException iOException)
            {
            }
            s = null;
        }
    }




}
