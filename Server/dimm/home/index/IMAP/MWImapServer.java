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
 *  You should have received a copy of the GNU General Public License_sp
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
    MailKonto konto = null;
    MailFolder mailfolder = null;
    boolean trace = false;
    int con = 0;
    MandantContext m_ctx;
    boolean has_searched = false;

    static HashMap<String, ImapCmd> cmd_map;

    public MWImapServer( MandantContext m_ctx, Socket s, boolean trace )
    {
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
    public MailFolder get_folder()
    {
        return mailfolder;
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

    private int techno( String line )
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
            out.close();
            in.close();
            s.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
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
    public void close() throws IOException
    {
        s.close();
    }


           /* if (cmd.equals("capability"))
            {
                return capability(sid, par);
            }
            * */
      /*      if (cmd.equals("login"))
            {
                return login(sid, par);
            }
            if (cmd.equals("fetch"))
            {
                return raw_fetch( sid, par );
            }
            if (cmd.equals("logout"))
            {
                return logout(sid, par);
            }
            if (cmd.equals("close"))
            {
                response(sid, true, "CLOSE completed");
                return 0;
            }
            if (cmd.equals("lsub"))
            {
                return lsub(sid, par);
            }
            if (cmd.equals("list"))
            {
                return list(sid, par);
            }
            if (cmd.equals("status"))
            {
                return status(sid, par);
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
                if (has_searched)
                {
                    for ( int m = 0; m < mailfolder.lastanzMessages(); m++)
                    {
                        MailInfo msginfo = mailfolder.get_last_mail_message(m);
                        response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
                    }
                    for ( int m = 0; m < mailfolder.anzMessages(); m++)
                    {
                        MailInfo msginfo = mailfolder.get_mail_message(m);
                        response(Integer.toString(msginfo.getUID()) + " RECENT");
                    }
                    has_searched = false;
                }

                response(sid, true, "NOOP completed");
                return 0;
            }
            if (cmd.equals("check"))
            {
                response(sid, true, "CHECK completed");
                return 0;
            }
            if (cmd.equals("subscribe"))
            {
                response(sid, true, "SUBSCRIBE completed");
                return 0;
            }
            if (cmd.equals("unsubscribe"))
            {
                response(sid, true, "UNSUBSCRIBE completed");
                return 0;
            }
            if (cmd.equals("idle"))
            {
                if (konto == null)
                {
                    response(sid, false, "IDLE failed");
                    return 1;
                }

                write("+ Waiting for done");

                if (mailfolder != null && has_searched)
                {
                    for ( int m = 0; m < mailfolder.lastanzMessages(); m++)
                    {
                        MailInfo msginfo = mailfolder.get_last_mail_message(m);
                        response(Integer.toString(msginfo.getUID()) + " EXPUNGE");
                    }
                    for ( int m = 0; m < mailfolder.anzMessages(); m++)
                    {
                        MailInfo msginfo = mailfolder.get_mail_message(m);
                        response(Integer.toString(msginfo.getUID()) + " RECENT");
                    }
                    has_searched = false;
                }
                    

                //Idle schleife 
                long last = 0;
                int manz = 0;
                if (mailfolder != null)
                        manz = mailfolder.anzMessages();
                while (true)
                {
                    try
                    {
                        if (in.ready())
                        {
                            String rline = in.readLine();
                            if (rline.toLowerCase().startsWith("done")
                                    || rline.toLowerCase().endsWith("close")
                                     || rline.toLowerCase().endsWith("logout") )
                            {
                                response(sid, true, "IDLE completed");
                                return 0;
                            }
                            if (!rline.toLowerCase().endsWith("noop"))
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
                    if ( (mailfolder != null) && manz != mailfolder.anzMessages())
                    {
                        response(mailfolder.anzMessages() + " EXISTS");
                        response("0 RECENT");
                    }
                }
            }
            response(sid, false, "unknown command");
            //BAD COMANND / login firdst
        }
        return 1;
    }*/

  
    //private static Hashtable kontos = new Hashtable();


/*
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
 * */
    /*
     * Split line to arguments 
     * "user" "passwd"
     * 
     */
/*
    public static String[] imapsplit( String line )
    {

        Vector<String> v = new Vector<String>();
        while (true)
        {
            line = line.trim();
            char tr = ' ';
            if (line.startsWith("\""))
            {
                line = line.substring(1);
                tr = '\"';
            }
            if (line.startsWith("("))
            {
                line = line.substring(1);
                tr = ')';
            }
            if (line.startsWith("{"))
            {
                line = line.substring(1);
                tr = '}';
            }
            if (line.trim().equals(""))
            {
                break;
            }
            int i = -1;
            if (tr == ' ')
                i = line.indexOf(tr);
            else
                i = line.lastIndexOf(tr);


            if (line.startsWith("BODY"))
            {
                int klauf = line.indexOf('[');
                if (klauf > 0 && i > 0 && klauf < i)
                {
                    int klzu = line.indexOf(']');
                    if (klzu > 0)
                    {
                        i = klzu + 1;
                        if (i >= line.length())
                            i = -1;
                    }

                }
            }
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
            part[x] =  v.elementAt(x);
        }
        return part;
    }

    public static String[] pathsplit( String line )
    {
        Vector<String> v = new Vector<String>();
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
            part[x] = v.elementAt(x);
        }
        return part;
    }

*/
    /* Funktionalitaet
     *
     * Funktionalitaet des ImapServers wird abgefragt 
     */
   /* private int capability( String sid, String par )
    {
        response("CAPABILITY IMAP4 LOGIN IDLE");
//        response("CAPABILITY IMAP4 IDLE LOGIN");
        response(sid, true, "CAPABILITY completed");
        return 0;
    }
*/

   /* static String storefunc[] = new String[]
    {
        "+flags", "-flags"
    };
*/
  /*  boolean search( int min, int max, int offset, String part[] )
    {        
        mailfolder.search( min, max, offset, part) ;

        String result = "SEARCH";
        for ( int i = 0; i < mailfolder.anzMessages(); i++)
        {
            MailInfo msginfo = mailfolder.get_mail_message(i);
            result += " " +  msginfo.getUID();
        }
        response( result );
        has_searched = true;
        return true;
    }*/
/*
    boolean fetch( int min, int max, int offset, boolean is_uid, String part[] )
    {
        int zaehler = 1;
        for (int i = 0; i < mailfolder.anzMessages(); i++)
        {
            MailInfo msginfo = mailfolder.get_mail_message(i);
            if (msginfo == null)
            {
                continue;
            }
            int uid = msginfo.getUID();

            if (is_uid)
            {
                if (uid < min)
                {
                    continue;
                }
                if (uid > max && max > -1)
                {
                    continue;
                }
            }
            else
            {
                if (max > 0)
                    if (i > max)
                        break;
            }

            rawwrite(RESTAG + (zaehler++) + " FETCH (");

            int size = msginfo.getRFC822size();
            String sflags = "(" + msginfo.getFlags() + ")"; //(\\Seen)

            boolean had_uid = false;
            boolean needs_space = false;

            for (int p = offset; p < part.length; p++)
            {
                String tags[] = imapsplit(part[p]);
                for (int x = 0; x < tags.length; x++)
                {

                    String orig_tag = tags[x].trim();
                    String tag = orig_tag.toLowerCase();


                    if (tag.equals("envelope"))
                    {
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;

                        String envelope = msginfo.getEnvelope();

                        rawwrite("ENVELOPE (" + envelope + ")");
                    }
                    if (tag.equals("flags"))
                    {
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        rawwrite("FLAGS " + sflags);
                    }
                    else if (tag.equals("rfc822.header"))
                    {
                        //header
                        String theader = msginfo.getRFC822header();
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        rawwrite("RFC822.HEADER {" + theader.length() + "}\r\n");
                        System.out.println("Writing Message header...");
                        try
                        {
                            s.getOutputStream().write(theader.getBytes());
                            System.out.print( theader );
                        }
                        catch (IOException iOException)
                        {
                        }
                    }
                    else if (tag.equals("rfc822.size"))
                    {
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        rawwrite("RFC822.SIZE " + size);
                    }
                    else if (tag.equals("uid"))
                    {
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        rawwrite("UID " + uid);
                        had_uid = true;
                    }
                    else if (tag.startsWith("body.peek[header.fields"))
                    {
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        
                        MWMailMessage msg = mailfolder.getMesg(i);
                        //message
                        String header = msg.get_header_fields( tag );
                        
                        rawwrite(orig_tag + " {" + header.length() + "}\r\n");
                        rawwrite( header );
                    }

                    else if (tag.equals("rfc822") || tag.equals("rfc822.peek") || tag.equals("body.peek[]"))
                    {
                        MWMailMessage msg = mailfolder.getMesg(i);
                        //message
                        int tsize = msg.getRFC822size();
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        

                        ByteArrayOutputStream byas = new ByteArrayOutputStream();

                        try
                        {
//                            System.out.println("Writing Message body...");
                            //msg.getRFC822body(s.getOutputStream());
                            msg.getRFC822body(byas);
                            byte[] mdata = byas.toByteArray();
                            tsize = mdata.length;
                            rawwrite("" + tag.toUpperCase() + " {" + tsize + "}\r\n");
                            s.getOutputStream().write( mdata );
                            s.getOutputStream().flush();

                            
                            System.out.print(byas.toString());
                        }
                        catch (IOException iOException)
                        {
                        }
                        catch (MessagingException messagingException)
                        {
                        }
                    }
                    else if (tag.equals("internaldate"))
                    {
                        MWMailMessage msg = mailfolder.getMesg(i);
                        if (needs_space)
                        {
                            rawwrite(" ");
                        }
                        needs_space = true;
                        rawwrite("INTERNALDATE \"" + msg.get_internaldate() + "\"");
                    }
                }
            }
            if (!had_uid)
            {
                rawwrite(" UID " + uid);
                had_uid = true;
            }

            rawwrite(")\r\n");
        }
        
        return true;
    }
*//*
    private int uid( String sid, String par )
    {
        if (konto != null && mailfolder != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                String command = part[0];
                String range = part[1];
                boolean success = true;
                while (!range.equals(""))
                {
                    
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

                    if (command.toLowerCase().equals("search"))
                    {
                        success &= search(min, max, 2, part);
                    }
                    else if (command.toLowerCase().equals("fetch"))
                    {
                        success &= fetch(min, max, 2,  true, part);
                    }
                    else
                    {
                        //Unknown Command
                        success = false;
                        break;
                    }

                }
//                response(sid, success, command.toUpperCase() + " " + (success ? "completed" : "failed"));
                response(sid, success, "UID " + command.toUpperCase() + " " + (success ? "completed" : "failed"));
                return success ? 0 : 1;
            }
        }
        response(sid, false, "UID failed");
        return 1;
    }
*/
    /*
    private int select( String sid, String par )
    {
        if (konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 1)
            {
                mailfolder = konto.select(part[0]);
                if (mailfolder != null)
                {
                    response("" + mailfolder.anzMessages() + " EXISTS" );
                    response("" + mailfolder.anzMessages() + " RECENT" );

                    response("FLAGS (\\Answered \\Flagged \\Deleted \\Draft \\Seen)");
                    response("OK [UIDVALIDITY " + mailfolder.get_uid_validity() + "] UIDVALIDITY value" );
                    response("OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Draft \\Seen)] Permanent flags)");
                }
                response(sid, true, "[READ-ONLY] SELECT completed");
                return 0;
            }
        }
        response(sid, false, "SELECT failed");
        return 1;
    }
*/
   
/*
   
    private int list( String sid, String par )
    {
        int h;
        int anz;


        if (konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                if (part[1].length() == 0)
                {
                    response("LIST  \"/\" ");  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].compareTo("*") == 0)
                {
                    response("LIST (\\HasNoChildren) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    response("LIST (\\HasNoChildren) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith("INBOX"))
                {
                    response("LIST (\\HasNoChildren) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith( MailFolder.QRYTOKEN ))
                {
                    response("LIST (\\HasNoChildren) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }

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
*/
 /*   private int status( String sid, String par )
    {
        int h;
        int anz;


        if (konto != null)
        {
            boolean with_uidnext = false;
            boolean with_unseen = false;
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                if (part.length > 2 )
                {
                    if (part[2].toLowerCase().contains("UIDNEXT"))
                    {
                        with_uidnext = true;
                    }
                    if (part[2].toLowerCase().contains("UNSEEN"))
                    {
                        with_unseen = true;
                    }
                }

                if (part[1].startsWith("INBOX"))
                {
                    response("STATUS INBOX (\\HasNoChildren) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith( MailFolder.QRYTOKEN ))
                {
                    response("LIST (\\HasNoChildren) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    response(sid, true, "LIST completed");
                    return 0;
                }

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
*/
   
 /*
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
                    int l = part[1].length();
                    if (l > 1)
                    {
                        if (part[1].charAt(l-1) == '*')
                            part[1] = part[1].substring(0, l-2);
                    }
                    
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
*/
   /*
    private int login( String sid, String par )
    {
        String auth[] = imapsplit(par);
        if (auth != null && auth.length >= 2)
        {
            String user = auth[0];
            String pwd = auth[1];
            
            try
            {
                if (m_ctx.authenticate_user(user, pwd))
                {
                    //Alles Ok
                    konto = new MailKonto(user, pwd);

                    response(sid, true, "User " + m_ctx.getMandant().getName() + " logged in");
                    return 0;
                }
            }
            catch (AuthException authException)
            {
                LogManager.err_log("IMAP Login failed", authException);
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
*/

/*
      int  raw_fetch( String sid, String par )
      {
            String part[] = imapsplit(par);
            String range = part[0];
            boolean success = true;
            while (!range.equals(""))
            {
                
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


               success &= fetch(min, max, 1, false, part);
            }

            response(sid, success, "FETCH " + (success ? "completed" : "failed"));
            return success ? 0 : 1;
      }
*/

}
