/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author mw
 */
public class MBoxImporter implements WorkerParentChild, MultipleMailImporter
{

    Long[] message_positions;

    private static final int BUFFSIZE = 128*1024;

    private static final String FROM = "From ";
    private static final byte[] BFROM = {'F','r','o','m',' '};
    boolean last_buff_end_was_crlf;

    File msg_file;
    RandomAccessFile raf = null;

    public MBoxImporter( String p ) throws Exception
    {
        msg_file = new File(p);
        if ( !msg_file.exists())
            throw new Exception( "Message file does not exist" );
    }

    @Override
    public void idle_check()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void finish()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void run_loop()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

 
    long check_mbox_start( byte[] buff, int rpos, int rlen, long act_pos )
    {
        int i = 0;
        for (i = 0; i < BFROM.length; i++)
        {
            if (buff[rpos + i] != BFROM[i])
                break;
        }
        if (i != BFROM.length)
            return -1;

        // DETECT BOL
        if (rpos > 0)
        {
            if (buff[rpos -1 ] != '\r' && buff[rpos -1 ] != '\n')
                return -1;
        }
        else
        {
            if (!last_buff_end_was_crlf)
                return -1;
        }

        return act_pos + rpos;
    }

    @Override
    public int get_message_count()
    {
        try
        {
            get_message_positions();
            return message_positions.length;
        }
        catch (ExtractionException extractionException)
        {
            return 0;
        }
        
    }
    @Override
    public File get_msg_file()
    {
        return msg_file;
    }


    Long[] get_message_positions() throws ExtractionException
    {

        if (message_positions == null)
        {
            byte[] buff = new byte[BUFFSIZE];
            long act_pos = 0;

            PushbackInputStream is = null;
            BufferedInputStream bis = null;

            List<Long> posList = null;
            try
            {
                bis = new BufferedInputStream(new FileInputStream(msg_file), BUFFSIZE);
                is = new PushbackInputStream(bis, BFROM.length + 1 );

                // FIRST ENTRY IS ON FIRST LINE
                last_buff_end_was_crlf = true;

                posList = new ArrayList<Long>();

                while (true)
                {

                    int rlen = is.read(buff);
                    if (rlen == -1)
                    {
                        break;
                    }
                    for (int i = 0; i < rlen; i++)
                    {
                        if (buff[i] == 'F')
                        {
                            int rest = rlen - i;
                            if (rest < rlen && rest < BFROM.length + 1)
                            {
                                byte[] rb = new byte[rest];
                                for (int j = 0; j < rest; j++)
                                {
                                    rb[j] = buff[i + j];
                                }
                                is.unread(rb);
                                rlen -= rest;

                                break; // NEXT READ
                            }

                            long pos = check_mbox_start(buff, i, rlen, act_pos);
                            if (pos != -1)
                            {
                                posList.add(pos);
                            }
                        }
                    }
                    act_pos += rlen;
                    if (rlen > 0)
                    {
                        if (buff[rlen - 1] == '\r' || buff[rlen - 1] == '\n')
                        {
                            last_buff_end_was_crlf = true;
                        }
                        else
                        {
                            last_buff_end_was_crlf = false;
                        }
                    }
                }
            }
            catch (IOException iOException)
            {
                throw new ExtractionException( iOException.getMessage());
            }
            finally
            {
                try
                {
                    if (is != null)
                    {
                        is.close();
                    }
                    if (bis != null)
                    {
                        bis.close();
                    }
                }
                catch (IOException iOException)
                {
                }
            }

            message_positions = posList.toArray(new Long[posList.size()]);
        }
        return message_positions;
    }

    @Override
    public void open() throws FileNotFoundException
    {
         raf = new RandomAccessFile( msg_file, "r");
    }
    @Override
    public void close() throws IOException
    {
         raf.close();
    }

    @Override
    public Message get_message( int idx ) throws ExtractionException, IOException, MessagingException
    {
        if (idx >= get_message_count())
            throw new ExtractionException("Invalid Message id " + idx + " during extraction of mbox <" + msg_file.getAbsolutePath() + ">");

        long start = message_positions[idx];
        long end = 0;

        if (idx == message_positions.length - 1)
            end = msg_file.length();
        else
            end = message_positions[idx + 1];


        raf.seek(start);

        // GET RID OF MBOX HEADER
        raf.readLine();
        start = raf.getFilePointer();

        int msg_len = (int)(end - start);
        byte[] buff = new byte[msg_len];
        int rest_len = msg_len;
        int start_pos = 0;
        while(rest_len > 0)
        {
            int rlen = raf.read(buff, start_pos, rest_len);
            if (rlen < 0)
                break;

            start_pos += rlen;
            rest_len -= rlen;
        }

        if (rest_len > 0)
            throw new ExtractionException("Cannot read message during extraction of mbox <" + msg_file.getAbsolutePath() + "> at pos " + start + ": only " + (msg_len - rest_len) + " of " + msg_len + " were read");

        while (buff[msg_len - 1] == '\r' || buff[msg_len - 1] == '\n')
            msg_len--;
 
        ByteArrayInputStream byais = new ByteArrayInputStream(buff, 0, msg_len);
        Properties props = new Properties();
        Session sess = Session.getDefaultInstance(props);
        MimeMessage msg = new MimeMessage( sess, byais);
        String str_msg = new String(buff, "UTF-8" );


        return msg;
    }

   

    
   
    

    public static void main(String[] args)
    {
        String arg = "Z:\\Mail_lokales_Konto\\Thunderbird\\Profiles\\p27621i7.default\\Mail\\localhost\\InBox";
        if (args.length > 0)
            arg = args[0];

        final String cs_token = "charset=";
        String type;

        try
        {
            MBoxImporter mbi = new MBoxImporter(arg);

            int cnt = mbi.get_message_count();
            mbi.open();

            for (int i = 0; i < cnt; i++)
            {
                Message msg = mbi.get_message(i);
                System.out.println("Betreff: " + msg.getSubject());
                type = msg.getContentType();
                System.out.println("Type   : " + type);
                String charset = "ISO-8859-1";
                int idx = type.toLowerCase().indexOf( cs_token);
                if (idx > 0)
                {
                    StringTokenizer str = new StringTokenizer( type.substring(idx + cs_token.length()), "\"\';\n\r,");
                    if (str.hasMoreElements())
                    {
                        charset = mbi.detect_charset( str.nextToken() );
                    }
                    else
                    {
                        System.out.println("Chrset Syntax: " + type);
                    }
                }

                DataHandler dh = msg.getDataHandler();
                DataSource ds = dh.getDataSource();
                InputStream is = ds.getInputStream();
                

                if (is != null && is instanceof InputStream)
                {
                    InputStreamReader istr = new InputStreamReader( is, charset);
                    BufferedReader br = new BufferedReader(istr);

                    System.out.println("Msgcntn: ");
                    while (true)
                    {
                        String txt = br.readLine();
                        if (txt == null)
                            break;
                        System.out.println(txt);
                        break;
                    }
                    istr.close();
                    br.close();
                }

                msg = null;
                //System.gc();

            }
            mbi.close();
        }
        catch (Exception iOException)
        {
            iOException.printStackTrace();
        }
    }

    String detect_charset( String cs )
    {
        cs = cs.toUpperCase();
        if (cs.startsWith("ISO-8859-15"))
            return "ISO-8859-15";
        if (cs.startsWith("ISO-8859-1"))
            return "ISO-8859-1";
        if (cs.startsWith("UTF-8"))
            return "UTF-8";
        if (cs.startsWith("MACINTOSH"))
            return "UTF-8";
        if (cs.startsWith("US-ASCII"))
            return "US-ASCII";

        return "ISO-8859-1";
    }

    @Override
    public void delete()
    {
        msg_file.delete();
    }
  

    

}
