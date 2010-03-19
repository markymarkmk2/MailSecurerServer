/**
 * The Server file is responsible for accepting connections and providing the server user interface...
 */
package rssImapServer;

import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCMimeMail;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.ini4j.Ini;

/**
 * @author Richard Johnson <rssimap@rjohnson.id.au>
 */
public class Server implements Runnable
{
    // some constants

    public static final String CONFIG_FILE = "config.ini";
    public static int PORT_NUMBER;
    public static int POLL_TIME;
    public static String PATH;
    // a collection of all the messages for every user...
    private Map<String, Map<String,List>> messages = new HashMap<String, Map<String,List>>();
    // all of the connections we have made to clients...
    private Set<ClientHandler> connections = new HashSet<ClientHandler>();

    /**
     * Entry point, just call the constructor of this class...
     * @param args
     */
    public static void main( String[] args )
    {
        // setup the environment...
        // this is so the print writer for the socket uses the correct line endings
        // regardless of the platform that we are on.
        System.setProperty("line.separator", "\r\n");

        // read in the configuration file...
        Ini ini = new Ini();
        try
        {
            ini.load(new FileReader(CONFIG_FILE));
            Map<String, String> serverOptions = ini.get("server");
            Server.PORT_NUMBER = Integer.parseInt(serverOptions.get("port"));
            Server.POLL_TIME = Integer.parseInt(serverOptions.get("poll_time"));
            Server.PATH = serverOptions.get("feed_directory");
            ClientHandler.UIDVALIDITY = Integer.parseInt(serverOptions.get("uidvalidity"));
        }
        catch (Exception err)
        {
            System.err.println("Unable to start server, could not load config file config.ini");
            err.printStackTrace();
            System.exit(1);
        }

        new Server();
    }

    /**
     * Constructor, initalise some things and wait for user input...
     */
    public Server()
    {
        // try and get a listing of all the logins we serve...
        File configDir = new java.io.File(Server.PATH);
        if (!configDir.isDirectory())
        {
            System.err.println("feed_directory is invalid, please check your config.ini");
            System.exit(1);
        }
        // load all of the files into an array...
        File[] files = configDir.listFiles(new FilenameFilter()
        {

            public boolean accept( File theFile, String fileName )
            {
                int len = fileName.length();
                if (len < 5)
                {
                    return false;
                }
                String suffix = fileName.substring(len - 4, len);
                String prefix = fileName.substring(0, 4);
                if (suffix.equals(".txt") &&
                        !fileName.equals("uids.txt") &&
                        !prefix.equals("uids"))
                {
                    return true;
                }
                return false;
            }
        });

        if (files.length == 0)
        {
            System.err.println("No feed files found!  Please ensure your feed_directory has at least 1 login.txt feed config file.");
            System.exit(1);
        }

        // start up a reader for each login...
    /*    Map<String, RssReader> readers = new HashMap<String, RssReader>();
        File configFile;
        for (int x = 0; x < files.length; x++)
        {
            configFile = files[x];
            String username = "test"; //configFile.getName().substring(0, configFile.getName().length()-4);
            System.out.println("Loading messages for " + username);
            RssReader temp = new RssReader(files[x]);
            temp.loadMessages();
            readers.put(username, temp);
            this.messages.put(username, temp.getMessages());
            //System.out.println("Added "+username+" with "+t)
        }
*/
        // get the user input and wait for it...
        try
        {
            // start the listener thread...
            Thread serverListener = new Thread(this);
            serverListener.start();

            messages.put("test",  generate_messages("test") );

            while (true)
            {

            Iterator iterator;
                // sleep for the poll time...
                // get a new listing of messages...
                                /*
                Set tempSet = readers.keySet();
                iterator = tempSet.iterator();
                while (iterator.hasNext())
                {
                String key = (String)iterator.next();
                RssReader temp = (RssReader)readers.get(key);
                temp.loadMessages();
                this.messages.put(key, temp.getMessages());
                }
                 * */

                System.out.println("Messages Loaded");
                // update all of the client handlers with the new message list...
                iterator = this.connections.iterator();
                while (iterator.hasNext())
                {
                    ClientHandler handler = (ClientHandler) iterator.next();
                    if (handler.isConnected())
                    {
                        Map<String, List> mmap = messages.get(handler.getUsername());
                        handler.setMessages(mmap);
                    }
                }


                Thread.sleep(Server.POLL_TIME);

            }

            /*BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line = "";
            while (!line.equals("q"))
            {
            System.out.println(line);
            line = in.readLine();
            }
            // q was pressed, die
            System.exit(0);*/
        }
        catch (Exception err)
        {
            err.printStackTrace();
        }
    }

    public Map<String,List> getMessages( String login )
    {
        if (this.messages.containsKey(login))
        {
            return this.messages.get(login);
        }
        return null;
    }

    //////////////////////////////
    /**
     * entry point for new thread that can wait for connections on the
     * server socket...
     */
    public void run()
    {
        try
        {
            // let's setup a server socket quickly...
            ServerSocket serverSocket = new ServerSocket(PORT_NUMBER);
            System.out.println("Server socket waiting for connections on port number " + PORT_NUMBER);
            while (true)
            {
                Socket client = serverSocket.accept();
                ClientHandler temp = new ClientHandler(client, this);
                this.connections.add(temp);
            }
        }
        catch (Exception err)
        {
            err.printStackTrace();
        }
    }
    int uid = 6;

    private Map<String,List> generate_messages( String username ) throws FileNotFoundException, MessagingException, IOException
    {
        Map<Integer, Message> out = new HashMap<Integer, Message>();
        
        Map<String,List> ret = new HashMap<String,List>();

        File mailfile = new File("Z:\\Mailtest\\test.eml");
        RFCFileMail mf = new RFCFileMail( mailfile, false);
        RFCMimeMail mimemessage = new RFCMimeMail();
        mimemessage.parse(mf);
        int cnt = mimemessage.get_attachment_cnt();
        String txt = mimemessage.get_text_content();
        String html = mimemessage.get_html_content();

        MimeMessage mm = mimemessage.getMsg();

        int seq = 1;
        Message msg = new Message();

        msg.setHeader("Subject", mm.getSubject());

        Address[] from = mm.getFrom();
        msg.setHeader("From", from[0].toString());

        

        String enc = mm.getHeader("Content-Transfer-Encoding", ";");

        
        if (enc != null)
            msg.setHeader("Content-Transfer-Encoding", enc );

        // dublin core uses a different date format...
        SimpleDateFormat simpleFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
        SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date tempDate = new Date();

        if (mm.getSentDate() != null)
            tempDate = mm.getSentDate();
        if (mm.getReceivedDate() != null)
            tempDate = mm.getReceivedDate();

        msg.setHeader("Date", simpleFormat.format(tempDate));
        msg.setInternalDate(simpleFormat.format(tempDate));
        msg.setMsgId(idFormat.format(tempDate) + "@localhost");


    /*    byte[] buff = new byte[ (int)mailfile.length() ];
        FileInputStream fis = new FileInputStream(mailfile);
        fis.read(buff);
        fis.close();
        String full_body = new String(buff);

        msg.setText(full_body);*/

        // WE ARE utf8 BECAUSE OF JAVA STRING IMPL
        if (html != null)
        {
            msg.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
            msg.setText(html);
        }
        else
        {
            msg.setHeader("Content-Type", "text/plain; charset=\"utf-8\"");
            msg.setText(txt);
        }
  

        msg.setSEQ(seq++);
        msg.updateEnvelope();

        // okay, see if we have seen this message before and if so, set the id...
        String hash = msg.getHash();


        msg.setUID(this.uid);
        //msg.clearFlags();
        // increment for the next message...
        this.uid++;


        out.put(new Integer(msg.getUID()), msg);

        List keyList = new ArrayList(out.keySet());
        List<Message> output = new ArrayList<Message>();
        Collections.sort(keyList);
        Iterator iterator = keyList.iterator();
        seq = 0;
        while (iterator.hasNext())
        {
            Integer key = (Integer) iterator.next();
            Message _msg = (Message) out.get(key);
            seq++;
            msg.setSEQ(seq);
            output.add(_msg);
        }

        ret.put(username, output);

        return ret;

    }
}
