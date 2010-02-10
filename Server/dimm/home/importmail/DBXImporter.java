/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

enum dbx_type_t
{

    DBX_TYPE_UNKNOWN,
    DBX_TYPE_EMAIL,
    DBX_TYPE_OE4,
    DBX_TYPE_FOLDER
};

class dbx_info_s
{

    public static final int DBX_MAX_FILENAME = 128;
    int index;
    String filename;
    int valid;
    int message_index;
    int flags;
    long send_create_time;
    int body_lines;
    int message_address;
    String original_subject;
    long save_time;
    String message_id;
    String subject;
    String sender_address_and_name;
    String message_id_replied_to;
    String server_newsgroup_message_number;
    String server;
    String sender_name;
    String sender_address;
    int message_priority;
    int message_size;
    long receive_create_time;
    String receiver_name;
    String receiver_address;
    String account_name;
    String account_registry_key;
};

class dbx_s
{

    RandomAccessFile file;
    long file_size;
    dbx_type_t type;
    int message_count;
    int capacity;
    dbx_info_s[] info;
};

/**
 *
 * @author mw
 */
public class DBXImporter extends WorkerParentChild implements MultipleMailImporter
{

    public static final int DBX_MASK_INDEX = 0x01;
    public static final int DBX_MASK_FLAGS = 0x02;
    public static final int DBX_MASK_BODYLINES = 0x04;
    public static final int DBX_MASK_MSGADDR = 0x08;
    public static final int DBX_MASK_MSGPRIO = 0x10;
    public static final int DBX_MASK_MSGSIZE = 0x20;
    private static final int BUFFSIZE = 128 * 1024;
    private static final String FROM = "From ";
    private static final byte[] BFROM =
    {
        'F', 'r', 'o', 'm', ' '
    };
    File dbx_file;
    dbx_s dbx;

    public DBXImporter( String p ) throws Exception
    {
        dbx_file = new File(p);
        if (!dbx_file.exists())
        {
            throw new Exception("Message file does not exist");
        }
    }

    @Override
    public void idle_check()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public void run_loop()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public File get_msg_file()
    {
        return dbx_file;
    }

    @Override
    public Message get_message( int idx ) throws ExtractionException, MessagingException, IOException
    {
        byte[] buff = dbx_message(dbx, idx);

        int msg_len = buff.length;

        ByteArrayInputStream byais = new ByteArrayInputStream(buff, 0, msg_len);
        Properties props = new Properties();
        Session sess = Session.getDefaultInstance(props);
        MimeMessage msg = new MimeMessage(sess, byais);


        return msg;
    }

    @Override
    public int get_message_count()
    {
        return dbx.message_count;
    }

    @Override
    public void open() throws ExtractionException, FileNotFoundException, IOException
    {
        dbx = dbx_open();      
    }

    @Override
    public void close()
    {
        dbx_close( dbx );
    }
    private static final int INDEX_POINTER = 0xE4;
    private static final int ITEM_COUNT = 0xC4;

    static int _dbx_info_cmp( dbx_info_s ia, dbx_info_s ib )
    {
        int res = ia.filename.compareTo(ib.filename);
        if (res == 0)
        {
            res = ia.index - ib.index;
        }
        return res;
    }

    static String _dbx_read_string( RandomAccessFile file, int offset ) throws IOException
    {
        byte[] c = new byte[256];
        StringBuffer sb = new StringBuffer();

        file.seek(offset);

        c[255] = '\0';
        int rlen = 0;
        do
        {
            rlen = file.read(c, 0, 255);
            String s = new String(c, 0, rlen, "Cp1250");
            if (s.indexOf('\0') < 255)
            {
                sb.append(s.substring(0, s.indexOf('\0')));
                break;
            }
            sb.append(s);
        }
        while (rlen != -1);

        return sb.toString();
    }

    static long dbx_read_date( RandomAccessFile file ) throws IOException
    {
        long v = 0;
        byte[] b = new byte[8];
        file.read(b);
        for (int i = b.length - 1; i >= 0; i--)
        {
            v <<= 8;
            v += (int)(b[i] & 0xFF);
        }
        return v;
    }

    static long dbx_read_date( RandomAccessFile file, long offset ) throws IOException
    {
        file.seek(offset);
        return dbx_read_date(file);
    }

    static int dbx_read_int( RandomAccessFile file ) throws IOException
    {
        int v = 0;
        byte[] b = new byte[4];
        file.read(b);
        for (int i = b.length - 1; i >= 0; i--)
        {
            v <<= 8;
            v += (int)(b[i] & 0xFF);
            
        }
        return v;
    }

    static short dbx_read_short( RandomAccessFile file ) throws IOException
    {
        short v = 0;
        byte[] b = new byte[2];
        file.read(b);
        for (int i = b.length - 1; i >= 0; i--)
        {
            v <<= 8;
            v += (int)(b[i] & 0xFF);
        }
        return v;
    }

    static int dbx_read_int( RandomAccessFile file, int offset, int value ) throws IOException
    {
        int v = value;
        if (offset > 0)
        {
            file.seek(offset);
            v = dbx_read_int(file);
        }
        return v;
    }

    static short dbx_read_short( RandomAccessFile file, int offset, short value ) throws IOException
    {
        short v = value;
        if (offset > 0)
        {
            file.seek(offset);
            v = dbx_read_short(file);
        }
        return v;
    }

    static int dbx_read_int3b( RandomAccessFile file ) throws IOException
    {
        int v = 0;
        byte[] b = new byte[3];
        file.read(b);
        for (int i = b.length - 1; i >= 0; i--)
        {
            v <<= 8;
            v += (int)(b[i] & 0xFF);
        }
        return v;
    }

    static void _dbx_read_info( dbx_s dbx ) throws IOException
    {
        int i;

        for (i = 0; i < dbx.message_count; i++)
        {
            int j;
            int size;
            int count = 0;
            int index = dbx.info[i].index;
            int offset = 0;
            int pos = index + 12;


            dbx.file.seek(index + 4);
            size = dbx_read_int(dbx.file);
            dbx.file.skipBytes(2);
            count = dbx.file.read();
            dbx.file.skipBytes(1);

            dbx.info[i].valid = 0;

            for (j = 0; j < count; j++)
            {
                int type = 0;
                int value = 0;

                dbx.file.seek(pos);
                type = dbx.file.read();
                value = dbx_read_int3b(dbx.file);

                /* msb means direct storage */
                offset = ((type & 0x80) == 0x80) ? 0 : (index + 12 + 4 * count + value);

                /* dirt ugly code follows ... */
                switch (type & 0x7f)
                {
                    case 0x00:
                        dbx.info[i].message_index = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_INDEX;
                        break;
                    case 0x01:
                        dbx.info[i].flags = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_FLAGS;
                        break;
                    case 0x02:
                        dbx.info[i].send_create_time = dbx_read_date(dbx.file, offset);
                        break;
                    case 0x03:
                        dbx.info[i].body_lines = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_BODYLINES;
                        break;
                    case 0x04:
                        dbx.info[i].message_address = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_MSGADDR;
                        break;
                    case 0x05:
                        dbx.info[i].original_subject = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x06:
                        dbx.info[i].save_time = dbx_read_date(dbx.file, offset);
                        break;
                    case 0x07:
                        dbx.info[i].message_id = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x08:
                        dbx.info[i].subject = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x09:
                        dbx.info[i].sender_address_and_name = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x0A:
                        dbx.info[i].message_id_replied_to = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x0B:
                        dbx.info[i].server_newsgroup_message_number = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x0C:
                        dbx.info[i].server = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x0D:
                        dbx.info[i].sender_name = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x0E:
                        dbx.info[i].sender_address = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x10:
                        dbx.info[i].message_priority = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_MSGPRIO;
                        break;
                    case 0x11:
                        dbx.info[i].message_size = dbx_read_int(dbx.file, offset, value);
                        dbx.info[i].valid |= DBX_MASK_MSGSIZE;
                        break;
                    case 0x12:
                        dbx.info[i].receive_create_time = dbx_read_date(dbx.file, offset);
                        break;
                    case 0x13:
                        dbx.info[i].receiver_name = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x14:
                        dbx.info[i].receiver_address = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x1A:
                        dbx.info[i].account_name = _dbx_read_string(dbx.file, offset);
                        break;
                    case 0x1B:
                        dbx.info[i].account_registry_key = _dbx_read_string(dbx.file, offset);
                        break;
                }
                pos += 4;
            }
        }
    }

    static int _dbx_read_index( dbx_s dbx, int pos ) throws IOException
    {
        int i;
        int next_table = 0;
        byte ptr_count = 0;
        int index_count = 0;

        if (pos <= 0 || dbx.file_size <= pos)
        {
            return 0;
        }

        next_table = dbx_read_int(dbx.file, pos + 8, 0);
        dbx.file.skipBytes(5);
        ptr_count = (byte) dbx.file.read();
        dbx.file.skipBytes(2);
        index_count = dbx_read_int(dbx.file);

        if (index_count > 0)
        {
            if (_dbx_read_index(dbx, next_table) == 0)
            {
                return 0;
            }
        }

        pos += 24;

        dbx.info = new dbx_info_s[dbx.capacity + ptr_count];
        dbx.capacity += ptr_count;

        for (i = 0; i < ptr_count; i++)
        {
            int index_ptr = dbx_read_int(dbx.file, pos, 0);

            next_table = dbx_read_int(dbx.file);
            index_count = dbx_read_int(dbx.file);

            dbx.info[dbx.message_count] = new dbx_info_s();
            dbx.info[dbx.message_count].index = index_ptr;
            dbx.message_count++;

            pos += 12;
            if (index_count > 0)
            {
                if (_dbx_read_index(dbx, next_table) == 0)
                {
                    return 0;
                }
            }
        }

        return 1;
    }

    static int _dbx_read_indexes( dbx_s dbx ) throws IOException
    {
        int index_ptr;
        int item_count;


        index_ptr = dbx_read_int(dbx.file, INDEX_POINTER, 0);


        item_count = dbx_read_int(dbx.file, ITEM_COUNT, 0);

        if (item_count > 0)
        {
            return _dbx_read_index(dbx, index_ptr);
        }
        else
        {
            return 0;
        }
    }

    static void _dbx_init( dbx_s dbx ) throws IOException
    {
        int[] signature = new int[4];

        dbx.message_count = 0;
        dbx.capacity = 0;
        dbx.info = null;

        for (int i = 0; i < signature.length; i++)
        {
            signature[i] = dbx_read_int(dbx.file);
        }

        if (signature[0] == 0xFE12ADCF &&
                signature[1] == 0x6F74FDC5 &&
                signature[2] == 0x11D1E366 &&
                signature[3] == 0xC0004E9A)
        {
            dbx.type = dbx_type_t.DBX_TYPE_EMAIL;
        }
        else if (signature[0] == 0x36464D4A &&
                signature[1] == 0x00010003)
        {
            dbx.type = dbx_type_t.DBX_TYPE_OE4;
        }
        else if (signature[0] == 0xFE12ADCF &&
                signature[1] == 0x6F74FDC6 &&
                signature[2] == 0x11D1E366 &&
                signature[3] == 0xC0004E9A)
        {
            dbx.type = dbx_type_t.DBX_TYPE_FOLDER;
        }
        else
        {
            dbx.type = dbx_type_t.DBX_TYPE_UNKNOWN;
        }

        if (dbx.type == dbx_type_t.DBX_TYPE_EMAIL)
        {
            _dbx_read_indexes(dbx);
            _dbx_read_info(dbx);
        }
    }

    dbx_s dbx_open( /*String filename*/ ) throws FileNotFoundException, IOException
    {
        String filename = dbx_file.getAbsolutePath();
        
        dbx = new dbx_s();

        File file = new File(filename);
        if (!file.exists())
        {
            return null;
        }

        dbx.file = new RandomAccessFile(filename, "r");
        dbx.file_size = file.length();
        _dbx_init(dbx);



        return dbx;
    }

    void dbx_close( dbx_s dbx )
    {
        int i;


        if (dbx.file != null)
        {
            try
            {
                dbx.file.close();
                dbx.file = null;
            }
            catch (IOException ex)
            {
                LogManager.log(Level.SEVERE, null, ex);
            }
        }
    }
    int psize;

    byte[] dbx_message( dbx_s dbx, int msg_number/*, unsigned  int *psize*/ ) throws IOException
    {
        int index = 0;
        int size = 0;
        int count = 0;
        int msg_offset = 0;
        int msg_offset_ptr = 0;
        int value = 0;
        int type = 0;
        int total_size = 0;
        short block_size = 0;
        int i = 0;
        byte[] buf = null;

        psize = 0;

        if (msg_number >= dbx.message_count)
        {
            return null;
        }

        index = dbx.info[msg_number].index;

        size = dbx_read_int(dbx.file, index + 4, 0);
        dbx.file.skipBytes(2);
        count = dbx.file.read();
        dbx.file.skipBytes(1);

        for (i = 0; i < count; i++)
        {
            type = dbx.file.read();
            value = dbx_read_int3b(dbx.file);

            if (type == 0x84)
            {
                msg_offset = value;
                break;
            }
            if (type == 0x04)
            {
                msg_offset_ptr = index + 12 + value + 4 * count;
                break;
            }
        }

        if (msg_offset == 0 && msg_offset_ptr != 0)
        {
            msg_offset = dbx_read_int(dbx.file, msg_offset_ptr, 0);
        }

        int message_size = dbx.info[msg_number].message_size;
        buf = new byte[message_size];
        i = msg_offset;
        total_size = 0;


        while (i != 0)
        {
            block_size = dbx_read_short(dbx.file, i + 8, (short) 0);
            dbx.file.skipBytes(2);
            i = dbx_read_int(dbx.file);
            total_size += block_size;
            if (total_size > message_size)
            {
                break;
            }
            /*byte [] tmp = buf;
            buf = new byte[total_size + 1];
            if (tmp != null)
            {
                System.arraycopy(tmp, 0, buf, 0, tmp.length);
            }*/
            dbx.file.read(buf, total_size - block_size, block_size);
        }

       /* if (buf != null)
        {
            buf[total_size] = '\0';
        }*/

        psize = total_size;

        return buf;
    }

    public static void main( String[] args ) throws Exception
    {
        String arg = "C:\\tmp\\MS_TMP_mandant11\\mailimp_localhost_1250780534531.dbx";
      /*  if (args.length > 0)
        {
            arg = args[0];
        }
        */
        DBXImporter dbx = new DBXImporter( arg );


        final String cs_token = "charset=";
        String type;

        try
        {

            dbx.open();

            int cnt = dbx.get_message_count();

            for (int i = 0; i < cnt; i++)
            {
                Message msg = dbx.get_message(i);
                System.out.println("Betreff: " + msg.getSubject());
                type = msg.getContentType();
                System.out.println("Type   : " + type);
                String charset = "ISO-8859-1";
                int idx = type.toLowerCase().indexOf(cs_token);
                if (idx > 0)
                {
                    StringTokenizer str = new StringTokenizer(type.substring(idx + cs_token.length()), "\"\';\n\r,");
                    if (str.hasMoreElements())
                    {
                        charset = dbx.detect_charset(str.nextToken());
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
                    InputStreamReader istr = new InputStreamReader(is, charset);
                    BufferedReader br = new BufferedReader(istr);

                    System.out.println("Msgcntn: ");
                    while (true)
                    {
                        String txt = br.readLine();
                        if (txt == null)
                        {
                            break;
                        }
                        System.out.println(txt);
                        break;
                    }
                    istr.close();
                    br.close();
                }

                msg = null;
                //System.gc();

            }
            dbx.close();
        }
        catch (Exception iOException)
        {
            iOException.printStackTrace();
            iOException.printStackTrace();
        }
    }

    String detect_charset( String cs )
    {
        cs = cs.toUpperCase();
        if (cs.startsWith("ISO-8859-15"))
        {
            return "ISO-8859-15";
        }
        if (cs.startsWith("ISO-8859-1"))
        {
            return "ISO-8859-1";
        }
        if (cs.startsWith("UTF-8"))
        {
            return "UTF-8";
        }
        if (cs.startsWith("MACINTOSH"))
        {
            return "UTF-8";
        }
        if (cs.startsWith("US-ASCII"))
        {
            return "US-ASCII";
        }

        return "ISO-8859-1";
    }

    @Override
    public void delete()
    {
        dbx_file.delete();
    }

 
    @Override
    public Object get_db_object()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

 
    @Override
    public String get_name()
    {
        return "DBXImporter";
    }

}
