/*  Pop3Get implementation
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
package dimm.home.index.IMAP.pop3;
import dimm.home.index.IMAP.jimap.MailKonto;
import dimm.home.index.IMAP.jimap.MailMessage;
import dimm.home.index.IMAP.jimap.MailQueue;
import dimm.home.index.IMAP.util.ObjectCollector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


public class Pop3Get extends Thread implements MailQueue
{
    boolean trace  = true; //socket mittracen 
    MailKonto konto;
    int port = 110;
    String host = "localhost";
    String user = "";
    String pass = "";
    String outbox = "INBOX";
    PrintWriter out;
    int tm = 600; //10 Minutes , 0 means only once
    boolean delete = false; //delete Mails in POP3 Account , which are marked as deleted 

    public void set(MailKonto konto)
    {
        this.konto = konto;
    }
    public void init(ObjectCollector data)
    {
        if(data != null)
        {
            ObjectCollector d[] = data.resetandgetArray();
            for(int i = 0;i < d.length;i++)
            {
                String n = d[i].getName();
                if(n.equals("server"))       host = d[i].getValue();
                if(n.equals("port"))         port    = Integer.parseInt(d[i].getValue());
                if(n.equals("user"))         user    = d[i].getValue();
                if(n.equals("pass"))         pass    = d[i].getValue();
                if(n.equals("out"))          outbox  = d[i].getValue();
                if(n.equals("period"))       tm      = Integer.parseInt(d[i].getValue());
                if(n.equals("delete") && d[i].getValue().toLowerCase().equals("true"))  delete = true;     
            }
        }
        this.start();
    }
  
    public void add(MailMessage m) { /* not supported */ }
    int mesgnr[] = new int[4096];
    int mesgsize[] = new int[4096];
    int delnr[] = new int[4096];
 
    private void write(String line)
    {
        if(trace) System.out.println(line);
        out.write(line+"\r\n");
        out.flush();                            
    }
    boolean sleep = false;
    public void raise(String messageid,String tag,String mfid)
    {
        if(tag.equals(MailKonto.FLAGS))
        {
            String flags = konto.get(tag,mfid,messageid).toLowerCase();
            if(flags.indexOf("deleted") >= 0)
            {
                //konto.log("real delete "+messageid);
                if(!mfid.equals("Pop3Get")) //Loop verhindern
                    konto.add(MailKonto.FLAGS,"Pop3Get",messageid,"\\Deleted");
                if(sleep) interrupt();
            }
        }
    }
    public void run()
    {
        while(true)
        {
            try
            {
                konto.log("Get Mail's from "+host+":"+port);
                int anzmesgnr = 0;
                int anzdelmesg = 0;
                int aktmesg   = 0;
                int delmesg   = -1;
                int state = 0;
                Socket s = new Socket(host,port);
                BufferedReader in = 
                    new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter( s.getOutputStream(), true ); 
                
                String line;
                boolean readline = true;
                while(readline && (line = in.readLine()) != null)
                {
                    if(trace) System.out.println(line);
                    if(!line.startsWith("+OK")) throw new Exception(line);
                    switch(state)
                    {
                    case 0:
                        write("USER "+user);
                        state++;
                        break;
                    case 1:
                        write("PASS "+pass);
                        state++;
                        break;
                    case 2:
                        //Now we can get List 
                        write("LIST");
                        state++;
                        break;
                    case 3:
                        while((line = in.readLine()) != null)
                        {
                            //1 9447
                            //2 962203
                            //.
                            if(trace) System.out.println(line);
                            if(line.startsWith(".")) 
                            {
                                write("NOOP");
                                state = 11; //readtop
                                break;
                            }
                            
                            int i = line.indexOf(" ");
                            if(i < 0) break;
                            mesgnr[anzmesgnr] = Integer.parseInt(line.substring(0,i));
                            //size ermitteln
			    line = line.substring(i+1);
                            mesgsize[anzmesgnr] = Integer.parseInt(line);
			    anzmesgnr++;
                        }
                        if(state == 11) continue;
                        throw new Exception("error while getting list"); 
                    case 4://read message 
			// int i = line.indexOf(" ");
			// line = line.substring(i+1).trim();
			// i = line.indexOf(" ");
                        int size = mesgsize[aktmesg];//Integer.parseInt(line.substring(0,i));
                        MailMessage m = new MailMessage(konto);
                        
                        if(trace) System.out.println("size to read "+size);
                        int orgsize = size;
                        line = "";
                        while(true)
                        {
                            int r = in.read();
                            if(r == -1) throw new Exception("read -1");
                            size--;
                            if(size < 0) throw new Exception("size < 0");
                                
                            switch((char)r)
                            {
                            case '\n':
                                break; //Zeilenende 
                            default:
                                line += (char)r;
                            case '\r':
                                if(size == 0) break;
                                continue; //Weiterlesen
                            }
                            if(trace) System.out.println(line);
                            m.add(line);
                            
                            if(size == 0) 
                            {
                                System.out.println("message append complied");
                                konto.add(outbox,m);
                                konto.log("Read new Mail from:"+m.get(MailMessage.FROM)+" size "+orgsize);
                               
                                //noch bis zum ende lesen 
                                while((line = in.readLine()) != null)
                                {
                                    if(line.trim().equals("")) continue;
                                    if(line.startsWith(".")) break;
                                    System.out.println("wrongLine "+line);
                                }
                                break;
                            }
                            line = "";
                        }
			aktmesg++;
                        state = 11; //next top lesen
                        write("NOOP");
                        break;
                        //if(!line.startsWith(".")) throw new Exception(line);
                    case 5:
                        if(aktmesg < anzmesgnr)
                        {
                            write("RETR "+mesgnr[aktmesg]);
                            state = 4;
                            break;
                        }
                        state = 15;
                        write("NOOP");
                        break;
                    case 10: //read top 
                        String header = "";
                        String messageid = "";
                        while(true)
                        {
                            line = in.readLine();
                            if(trace) System.out.println(line);
                            if(line.startsWith(".")) break;
                            header += line;
                            String mid = MailMessage.getMessageId(line);
                            if(mid != null) messageid = mid;
                        }
                        if(messageid == null)
                        {
                            throw new Exception("no Messageid in "+header);
                            //eventuell hier mal die Messageid aus header berechnen
                            //z.B. checksum , date , oder ...
                        }
                        if(!konto.exists(messageid))
                        {
                            //existiert noch nicht, dann laden
                            state = 5; //laden
                            write("NOOP");
                            break;
                        }
                        String flags = konto.get(MailKonto.FLAGS,"Pop3Get",messageid).toLowerCase();
                        if(flags.indexOf("deleted") >= 0)
                        {
                            //konto.log("real delete "+messageid);
                            delnr[anzdelmesg++] = mesgnr[aktmesg];
                        }
                        aktmesg++;
                    case 11: //read top
                        if(aktmesg < anzmesgnr)
                        {
                            write("TOP "+mesgnr[aktmesg]+" 0");
                            state = 10;
                            break;
                        }
                        state = 15;
                        write("NOOP");
                        break;
                        
                    case 15:
                        if(delmesg == -1)
                            delmesg = anzdelmesg;
                        if(delmesg > 0)
                        {
                            delmesg--;
                            if(delete)
                            {
                                write("DELE "+delnr[delmesg]);
                                konto.log("Delete "+delnr[delmesg]);
                            }
                            else
                            {
                                write("NOOP");
                                konto.log("Mesg "+delnr[delmesg]+" marked as deleted");
                            }
                            break;
                        }
                        state = 99;
                    case 20:
                        write("QUIT");
                        break;


                    case 99:
                        //Ende ohne Fehler
                        readline = true;
                        break;
                    default:
                        throw new Exception("unknown state "+state); 
                    }
                    
                }
                out.close();
                in.close();
                s.close();
            } 
            catch(Exception e)
            {
                konto.log(e);
            }
            if(tm == 0) break;
            konto.log("Waiting "+tm+" seconds");
            try
            {
                sleep = true;
                Thread.sleep(1000 * tm);
            }
            catch(InterruptedException ie) 
            {
                konto.log(ie);
            }
            sleep = false;
        } // while(true) ...
    }
}
