/*  ImapServer implementation
 *  Copyright (C) 2006 Dirk friedenberger <projekte@frittenburger.de>
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package dimm.home.index.IMAP.jimap;

import dimm.home.index.IMAP.util.ObjectCollector;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import java.net.*;
import java.io.*;
import java.util.*;


class IMAPKonto
{

    void log( Exception e )
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
class IMAPFile
{

    int anzMessages()
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    MailInfo getInfo( int i )
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
public class ImapServer extends Thread
{

    PrintWriter out;
    BufferedReader in;
    Socket s = null;
    MailKonto konto = null;
    MailFile mailfile = null;
    boolean trace = false;
    int con = 0;
    MandantContext m_ctx;

    public ImapServer( MandantContext m_ctx, Socket s, boolean trace )
    {
        this.s = s;
        this.m_ctx = m_ctx;
        this.trace = trace;
    }

    private void write( String message )
    {
        if (trace)
        {
            System.out.println( message);
        }
        message += "\r\n";
        out.write(message);
        out.flush();
    }

    private void rawwrite( String message )
    {
        if (trace)
        {
            System.out.print(message);
        }
        out.write(message);
        out.flush();
    }
    private final static String RESTAG = "* ";

    private void response( String message )
    {
        write(RESTAG + message);
    }

    private void response( String sid, boolean ok, String message )
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

    private int techno( String line )
    {
        line = line.trim();

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
            System.out.println("In: [" + sid + "]  "+cmd);


            if (cmd.equals("capability"))
            {
                return capability(sid, par);
            }
            if (cmd.equals("login"))
            {
                return login(sid, par);
            }
            if (cmd.equals("logout"))
            {
                return logout(sid, par);
            }
            if (cmd.equals("lsub"))
            {
                return lsub(sid, par);
            }
            if (cmd.equals("list"))
            {
                return list(sid, par);
            }
            
            if (cmd.equals("select") || cmd.equals("examine"))
            {
                return select(sid, par);
            }
            if (cmd.equals("uid"))
            {
                return uid(sid, par);
            }
            
            if (cmd.equals("noop"))
            {
                response(sid, true, "NOOP completed");
                return 0;
            }
            if (cmd.equals("check"))
            {
                response(sid, true, "CHECK completed");
                return 0;
            }
            if (cmd.equals("idle"))
            {
                if (konto == null || mailfile == null)
                {
                    response(sid, false, "IDLE failed");
                    return 1;
                }
                write("+ Waiting for done");
                //Idle schleife 
                long last = 0;
                int manz = mailfile.anzMessages();
                while (true)
                {

                    try
                    {
                        if (in.ready())
                        {
                            String rline = in.readLine();
                            if (rline.toLowerCase().startsWith("done"))
                            {
                                response(sid, true, "IDLE completed");
                                return 0;
                            }
                            throw new Exception(rline);
                        }
                    }
                    catch (Exception e)
                    {
                        konto.log(e);
                        response(sid, false, "IDLE failed");
                        return 1;
                    }

                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e)
                    {
                        konto.log(e);
                    }
                    if (manz != mailfile.anzMessages())
                    {
                        response(mailfile.anzMessages() + " EXISTS");
                        response("0 RECENT");
                    }
                }
            }
            response(sid, false, "unknown command");
            //BAD COMANND / login firdst
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
            response(null, true, "localhost IMAP4 31.08.2006");
            while (true)
            {
                String line = in.readLine();
                if (line == null)
                {
                    break;
                }
                if (trace)
                {
                    System.out.println("[" + con + "] " + line);
                }
                line = line.trim();
                if (!line.equals(""))
                {
                    techno(line);
                }
            }
            out.close();
            in.close();
            s.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    
    /* Hilfe funktion */
    /* getKonto */

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
    private static String maildb = "";
    private static Hashtable kontos = new Hashtable();



    private static MailKonto getKonto( String user, String passwd )
    {

        String k = cleanup(user);
        MailKonto m = (MailKonto) kontos.get(k);
        
        if (m != null && m.authenticate(user, passwd))
        {
            return m;
        }
        return null;
    }
    /*
     * Split line to arguments 
     * "user" "passwd"
     * 
     */

    public static String[] imapsplit( String line )
    {

        Vector v = new Vector();
        while (true)
        {
            line = line.trim();
            String tr = " ";
            if (line.startsWith("\""))
            {
                line = line.substring(1);
                tr = "\"";
            }
            if (line.startsWith("("))
            {
                line = line.substring(1);
                tr = ")";
            }
            if (line.startsWith("{"))
            {
                line = line.substring(1);
                tr = "}";
            }
            if (line.trim().equals(""))
            {
                break;
            }
            int i = line.indexOf(tr);
            if (i < 0)
            {
                v.add(line);
                break;
            }
            v.add(line.substring(0, i));
            line = line.substring(i + 1);
        }
        String part[] = new String[v.size()];
        for (int x = 0; x < part.length; x++)
        {
            part[x] = (String) v.elementAt(x);
        }
        return part;
    }

    public static String[] pathsplit( String line )
    {
        Vector v = new Vector();
        while (true)
        {
            line = line.trim();
            String tr = "/";
            int i = line.indexOf(tr);
            if (i < 0)
            {
                v.add(line);
                break;
            }
            v.add(line.substring(0, i));
            line = line.substring(i + 1);
        }
        String part[] = new String[v.size()];
        for (int x = 0; x < part.length; x++)
        {
            part[x] = (String) v.elementAt(x);
        }
        return part;
    }


    /* Funktionalitaet
     *
     * Funktionalitaet des ImapServers wird abgefragt 
     */
    private int capability( String sid, String par )
    {
        response("CAPABILITY IMAP4 LOGIN");
//        response("CAPABILITY IMAP4 IDLE LOGIN");
        response(sid, true, "CAPABILITY completed");
        return 0;
    }


    static String storefunc[] = new String[]
    {
        "+flags", "-flags"
    };

   

    boolean fetch( int min, int max, int offset, String part[] )
    {
        int i, x;
        boolean flags = false;
        boolean header = false;
        boolean mesg = false;
        boolean mesgsize = false;

        for (i = offset; i < part.length; i++)
        {
            String tags[] = imapsplit(part[i]);
            for (x = 0; x < tags.length; x++)
            {
                if (tags[x].toLowerCase().trim().equals("flags"))
                {
                    flags = true;
                }
                if (tags[x].toLowerCase().trim().equals("rfc822.header"))
                {
                    header = true;
                }
                if (tags[x].toLowerCase().trim().equals("rfc822.size"))
                {
                    mesgsize = true;
                }
                if (tags[x].toLowerCase().trim().equals("rfc822"))
                {
                    mesg = true;
                }
            }
        }

        int zaehler = 1;
        for (i = 0; i < mailfile.anzMessages(); i++)
        {
            MailInfo msginfo = mailfile.getInfo(i);
            if (msginfo == null)
            {
                continue;
            }
            int uid = msginfo.getUID();
            if (uid < min)
            {
                continue;
            }
            if (uid > max && max > -1)
            {
                continue;
            }

            String sflags = "(" + msginfo.getFlags() + ")"; //(\\Seen)

            rawwrite(RESTAG + (zaehler++) + " FETCH (UID " + uid);


            int size = msginfo.getRFC822size();
            if (mesgsize)
            {
                rawwrite(" RFC822.SIZE " + size);
            }

            if (header)
            {
                //header
                String theader = msginfo.getRFC822header();
                rawwrite(" RFC822.HEADER {" + theader.length() + "}\r\n");
                rawwrite(theader);
            }
            else if (mesg)
            {
                MailMessage msg = mailfile.getMesg(i);
                //message
                String theader = msg.getRFC822header();
                Vector tmesg = msg.getRFC822body();
                int tsize = msg.getRFC822size();
                rawwrite(" RFC822 {" + size + "}\r\n");
                rawwrite(theader);
                for (int ei = 0; ei < tmesg.size(); ei++)
                {
                    rawwrite((String) tmesg.elementAt(ei));
                }
            }
            if (flags)
            {
                rawwrite(" FLAGS " + sflags);
            }
            rawwrite(")\r\n");
        }
        return true;
    }

    private int uid( String sid, String par )
    {
        if (konto != null && mailfile != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                String command = part[0];
                String range = part[1];
                boolean success = true;
                while (!range.equals(""))
                {
                    /* uid range could be 34:38,42:43,45 */
                    String bereich = "";
                    int i = range.indexOf(",");
                    if (i < 0)
                    {
                        bereich = range;
                        range = "";
                    }
                    else
                    {
                        bereich = range.substring(0, i);
                        range = range.substring(i + 1);
                    }
                    int min = -1;
                    int max = -1;
                    i = bereich.indexOf(":");
                    if (i < 0)
                    {
                        try
                        {
                            min = max = Integer.parseInt(bereich);
                        }
                        catch (Exception e)
                        {
                        }
                    }
                    else
                    {
                        try
                        {
                            min = Integer.parseInt(bereich.substring(0, i));
                        }
                        catch (Exception e)
                        {
                        }
                        
                        try
                        {
                            max = Integer.parseInt(bereich.substring(i + 1));
                        }
                        catch (Exception e)
                        {
                        }
                        
                        if (min > 100000)
                        {
                            min = 0; //Mop: ob das wohl richtig ist
                        }
                        if (max == 0)
                        {
                            max = -1;
                        }
                    }
                    //debug
                    //System.out.println("call "+command+" from:"+min+" to:"+max);

                    if (command.equals("fetch"))
                    {
                        success &= fetch(min, max, 2, part);
                    }
                    else
                    {
                        //Unknown Command
                        success = false;
                        break;
                    }

                }
                response(sid, success, "UID " + command.toUpperCase() + " " + (success ? "completed" : "failed"));
                return success ? 0 : 1;
            }
        }
        response(sid, false, "UID failed");
        return 1;
    }

    private int select( String sid, String par )
    {
        if (konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                mailfile = konto.select(part[0]);
                if (mailfile != null)
                {
                    response(mailfile.anzMessages() + " EXISTS");
                    response("0 RECENT");
                    response("FLAGS (Junk NonJunk \\* \\Answered \\Flagged \\Deleted \\Draft \\Seen)");
                    response("OK [PERMANENTFLAGS (Junk  NonJunk \\* \\Answered \\Flagged \\Deleted \\Draft \\Seen)] Permanent flags)");
                }
                response(sid, true, "[READ-ONLY] SELECT completed");
                return 0;
            }
        }
        response(sid, false, "SELECT failed");
        return 1;
    }

   

   
    private int list( String sid, String par )
    {
        int h;
        int anz;
        if (konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                String dirlist[] = konto.getDirlist(".");
                String req[] = pathsplit(part[1]);
                for (int i = 0; i < dirlist.length; i++)
                {
                    String qreq[] = pathsplit(dirlist[i]);
                    if (req.length != qreq.length)
                    {
                        continue;
                    }
                    for (h = 0; h < req.length; h++)
                    {
                        if (req[h].equals("%"))
                        {
                            continue;
                        }
                        if (req[h].equals(qreq[h]))
                        {
                            continue;
                        }
                        break;
                    }
                    if (h < req.length)
                    {
                        continue;
                    }

                    response("LIST (" + "" + ") \"/\" " + dirlist[i]);  // \\NoInferiors \\HasNoChildren
                }
                response(sid, true, "LIST completed");
                return 0;
            }
        }
        response(sid, false, "LIST failed");
        return 1;
    }

   
    private int lsub( String sid, String par )
    {
        if (konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                String dirlist[] = konto.getDirlist(part[0]);
                for (int i = 0; i < dirlist.length; i++)
                {
                    boolean filter = true;
                    //filtern der directories
                    if (dirlist[i].startsWith(part[1]))
                    {
                        filter = false;
                    }
                    if (part[1].equals("*"))
                    {
                        filter = false;
                    }

                    if (filter)
                    {
                        continue;
                    }
                    response("LSUB () \"/\" " + dirlist[i]);
                }
                response(sid, true, "LSUB completed");
                return 0;
            }
        }
        response(sid, false, "LSUB failed");
        return 1;
    }

    private int login( String sid, String par )
    {
        String auth[] = imapsplit(par);
        if (auth != null && auth.length >= 2)
        {
            // TODO: MAIL USER AUTH
            m_ctx = Main.get_control().get_mandant_by_id(1);

            if (m_ctx != null)
            {
                //Alles Ok
                response(sid, true, "User " + m_ctx.getMandant().getName() + " logged in");
                return 0;
            }
        }

        response(sid, false, "LOGIN failed");
        return 1;
    }

    private int logout( String sid, String par )
    {
        if (m_ctx != null)
        {
            //Alles Ok
            response(sid, true, "User " + m_ctx.getMandant().getName() + " logged out");
            m_ctx = null;
            return 0;
        }
        response(sid, false, "LOGIN failed");
        return 1;
    }

    public void close() throws IOException
    {
        s.close();
    }
}
