package dimm.home.httpd;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.Commands.AbstractCommand;
import dimm.home.mailarchiv.Communicator;
import dimm.home.mailarchiv.LogicControl;
import home.shared.SQL.SQLArrayResult;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.workers.SQLWorker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.StringTokenizer;
import javax.jws.WebMethod;
import javax.jws.WebParam;

class ConnEntry
{
    public Connection conn;
    public int id;
    
    ConnEntry( Connection _c, int _id )
    {
        conn = _c;
        id = _id;
    }
}
class StatementEntry
{
    public Statement sta;
    public int id;
    public ConnEntry ce;

    StatementEntry( ConnEntry _ce, Statement _c, int _id )
    {
        sta = _c;
        id = _id;
        ce = _ce;
    }
}
class ResultEntry
{
    public ResultSet rs;
    public int id;
    public ConnEntry ce;

    ResultEntry( ConnEntry _ce, ResultSet _c, int _id )
    {
        rs = _c;
        id = _id;
        ce = _ce;
    }
}

class OutputStreamEntry
{
    public OutputStream os;
    public int id;
    public File file;

    OutputStreamEntry( OutputStream _os, File _file, int _id )
    {
        os = _os;
        file = _file;
        id = _id;
    }
}
class InputStreamEntry
{
    public InputStream is;
    public int id;
    public File file;

    InputStreamEntry( InputStream _is, File _file, int _id )
    {
        is = _is;
        file = _file;
        id = _id;
    }
}

public class TCPCallConnect extends WorkerParent
{
    static ArrayList<ConnEntry> conn_list = new ArrayList<ConnEntry>();
    static ArrayList<StatementEntry> sta_list = new ArrayList<StatementEntry>();
    static ArrayList<ResultEntry> rs_list = new ArrayList<ResultEntry>();

    static ArrayList<InputStreamEntry> istream_list = new ArrayList<InputStreamEntry>();
    static ArrayList<OutputStreamEntry> ostream_list = new ArrayList<OutputStreamEntry>();

    private static final int TCPCMDBUFF_LEN = 80;
    byte[] tcp_cmd_buff;
    private static final int TCP_LEN = 32;
    private static final int TCP_SERVER_PORT = 11210;
    public static final String HELLO_CMD = "HELLO";

    ServerSocket tcp_s;

    public TCPCallConnect()
    {
        super("TCPCallConnect");
        tcp_cmd_buff = new byte[TCPCMDBUFF_LEN];
    }


    ConnEntry get_conn( int id )
    {
        for (int i = 0; i < conn_list.size(); i++)
        {
            ConnEntry connEntry = conn_list.get(i);
            if (connEntry.id == id)
                return connEntry;
        }
        return null;
    }
    StatementEntry get_sta( int id )
    {
        for (int i = 0; i < sta_list.size(); i++)
        {
            StatementEntry staEntry = sta_list.get(i);
            if (staEntry.id == id)
                return staEntry;
        }
        return null;
    }
    ResultEntry get_rs( int id )
    {
        for (int i = 0; i < rs_list.size(); i++)
        {
            ResultEntry rsEntry = rs_list.get(i);
            if (rsEntry.id == id)
                return rsEntry;
        }
        return null;
    }
    InputStreamEntry get_istream( int id )
    {
        for (int i = 0; i < istream_list.size(); i++)
        {
            InputStreamEntry rsEntry = istream_list.get(i);
            if (rsEntry.id == id)
                return rsEntry;
        }
        return null;
    }
    OutputStreamEntry get_ostream( int id )
    {
        for (int i = 0; i < ostream_list.size(); i++)
        {
            OutputStreamEntry rsEntry = ostream_list.get(i);
            if (rsEntry.id == id)
                return rsEntry;
        }
        return null;
    }

    void drop_conn( int id )
    {
        ConnEntry c = get_conn(id);
        conn_list.remove(c);
    }
    void drop_sta( int id )
    {
        StatementEntry c = get_sta(id);
        sta_list.remove(c);
    }
    void drop_rs( int id )
    {
        ResultEntry c = get_rs(id);
        rs_list.remove(c);
    }
    void drop_istream( int id )
    {
        InputStreamEntry c = get_istream(id);
        istream_list.remove(c);
    }
    void drop_ostream( int id )
    {
        OutputStreamEntry c = get_ostream(id);
        ostream_list.remove(c);
    }

    Object call_method( Socket s, String cmf, ArrayList<String> params ) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        Method[] list = this.getClass().getDeclaredMethods();
        String str="";
        ArrayList<String> args = new ArrayList<String>();

        for (int i = 0; i < list.length; i++)
        {
            Method method = list[i];
            if (!method.getName().startsWith("RMX_"))
                continue;

            if (method.getName().substring(4).equals(cmf))
            {
                if (method.getParameterTypes().length - 1 >= params.size())
                {
                    Class[] paramc = method.getParameterTypes();

                    boolean with_socket = false;
                    if (paramc.length >= 1 && paramc[0] == tcp_s.getClass())
                        with_socket = true;

                    int j =  0;
                    for (j = 0; j < paramc.length; j++)
                    {
                        if (with_socket && i == 0)
                            continue;


                        Class class1 = paramc[j];
                        if (!(class1 != str.getClass()))
                            break;

                        String arg = null;
                        if (params.size() > j)
                            arg = params.get(j);

                        args.add( arg );
                    }

                    Object ret = null;
                    if (j == paramc.length)
                    {
                        if (with_socket)
                        {
                            switch (paramc.length)
                            {
                                case 1: ret = method.invoke( this, s); break;
                                case 2: ret = method.invoke( this, s, args.get(0)); break;
                                case 3: ret = method.invoke( this, s, args.get(0), args.get(1)); break;
                                case 4: ret = method.invoke( this, s, args.get(0), args.get(1), args.get(2)); break;
                                case 5: ret = method.invoke( this, s, args.get(0), args.get(1), args.get(2), args.get(3)); break;
                                case 6: ret = method.invoke( this, s, args.get(0), args.get(1), args.get(2), args.get(3), args.get(4)); break;
                                default: ret =  null;
                            }
                        }
                        else
                        {
                            switch (paramc.length)
                            {
                                case 0: ret = method.invoke( this ); break;
                                case 1: ret = method.invoke( this, args.get(0)); break;
                                case 2: ret = method.invoke( this, args.get(0), args.get(1)); break;
                                case 3: ret = method.invoke( this, args.get(0), args.get(1), args.get(2)); break;
                                case 4: ret = method.invoke( this, args.get(0), args.get(1), args.get(2), args.get(3)); break;
                                case 5: ret = method.invoke( this, args.get(0), args.get(1), args.get(2), args.get(3), args.get(4)); break;
                                default: ret =  null;
                            }
                        }
                    }

                    return ret;
                }
            }
        }
        return null;
    }

   private boolean handle_ip_command(Socket s) throws IOException
   {
        InputStream in = s.getInputStream();

        OutputStream out = s.getOutputStream();

        // TCP-PACKET HAS AT LEAST TCP_LEN BYTE
        byte[] buff = new byte[TCP_LEN];

        in.read( buff );
        if (buff[0] == 0)
            return false;

        String data = new String( buff, "UTF-8");
        ParseToken pt = new ParseToken( data );
        String cmd = pt.GetString("CMD:");
        int len = pt.GetLong("LEN:").intValue();
        byte[] add_data = null;


        if (len > 0)
        {
            add_data = new byte[len];
            int rlen = 0;
            while (rlen < len)
            {
                int llen = in.read( add_data, rlen, len - rlen );
                rlen += llen;
            }
        }

        dispatch_tcp_command( s, cmd, add_data, out );

        return true;
    }

    void write_tcp_answer( boolean ok, String ret, OutputStream out ) throws IOException
    {
        StringBuffer answer = new StringBuffer();

        if (ok)
            answer.append( "OK:LEN:");
        else
            answer.append( "NOK:LEN:");

        int alen = 0;

        if (ret != null)
        {
            alen = ret.getBytes("UTF-8").length;
        }

        answer.append(  Integer.toString(alen) );
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes() );

        if (alen > 0)
            out.write( ret.getBytes("UTF-8") );

        out.flush();
    }

    void write_tcp_answer( boolean ok, long alen, InputStream in, OutputStream out ) throws IOException
    {
        StringBuffer answer = new StringBuffer();

        if (ok)
            answer.append( "OK:LEN:");
        else
            answer.append( "NOK:LEN:");


        answer.append(  Long.toString(alen) );
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes("UTF-8"), 0, TCP_LEN );

        // PUSH DATA OVER BUFFER
        int buff_len = 8192;
        byte[] buff = new byte[buff_len];

        while (alen > 0)
        {
            long blen = alen;
            if (blen > buff_len)
                blen = buff_len;

            int rlen = in.read( buff, 0, (int)blen );
            out.write( buff, 0, rlen );
            alen -= rlen;
        }

        in.close();

        out.flush();
    }

    void write_tcp_answer( boolean ok, InputStream is, long len , OutputStream out ) throws IOException
    {
        write_tcp_answer( ok, len, is, out );
    }
    void write_tcp_answer( boolean ok, String result, OutputStream out, long len ) throws IOException
    {
            write_tcp_answer( ok, result, out );
    }

    public static String decode_pipe( String s )
    {
        int idx = s.indexOf("^");
        if (idx == -1)
            return s;

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (ch != '^')
            {
                sb.append(ch);
                continue;
            }
            if (i +2 < s.length())
            {
                if (s.charAt(i + 1) == '7' && s.charAt(i + 2) == 'C' )
                {
                    sb.append('|');
                    i+= 2;
                }
                if (s.charAt(i + 1) == '5' && s.charAt(i + 2) == 'E' )
                {
                    sb.append('^');
                    i+= 2;
                }
            }
        }
        return sb.toString();
    }
    public static String encode_pipe( String s )
    {
        int idx = s.indexOf("|");
        if (idx == -1)
            return s;

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (ch != '^' && ch != '|')
            {
                sb.append(ch);
                continue;
            }
            sb.append('^');
            if (ch == '^')
            {
                sb.append("5E");
            }
            else if (ch == '|')
            {
                sb.append("7C");
            }
        }
        return sb.toString();
    }


    void dispatch_tcp_command( Socket s, String cmd, byte[] add_data, OutputStream out ) throws IOException
    {
        Main.debug_msg( 5, "Received ip command <" + cmd + "> " );

        if (cmd.equals("?") || cmd.equals("help") )
        {
            String answer = "Help!";

            write_tcp_answer( true, answer, out );
            return;
        }
        else
        {
            String add_str = new String ( add_data, "UTF-8" );
            StringTokenizer str = new StringTokenizer( add_str, "|");
            ArrayList<String> params = new ArrayList<String>();
            while ( str.hasMoreTokens())
            {
                String arg = str.nextToken();
                arg = decode_pipe( arg );
                params.add(arg);
            }


            try
            {
                Object ret = call_method(s, cmd, params);

                write_tcp_answer( true, ret.toString(), out );
            }
            catch (Exception exc)
            {
                write_tcp_answer( false, exc.getMessage(), out );
            }
        }
    }

    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting communicator tasks" );

        start_tcpip_task();

        SwingWorker worker = new SwingWorker()
        {
            public Object construct()
            {
                run_loop();

                return null;
            }
        };

        worker.start();
        return true;


    }

    private void run_loop()
    {
        int fallback_cnt = 0;
        while (true)
        {
            LogicControl.sleep(1000);


            // TRY EVERY MINUTE TO CHANGE FROM FALLBACK TO VALID IP
/*            if (using_fallback)
            {
                fallback_cnt++;
                if (fallback_cnt == 60)
                {
                    fallback_cnt = 0;
                    if (set_ipconfig())
                    {
                        using_fallback = false;
                    }
                }
            }*/
        }

    }

    boolean start_tcpip_task()
    {
        SwingWorker worker = new SwingWorker()
        {
            @Override
            public Object construct()
            {
                int ret = -1;
                try
                {
                    tcpip_listener();
                }
                catch (Exception err)
                {
                    err.printStackTrace();
                    ret = -2;
                }
                Integer iret = new Integer(ret);
                return iret;
            }
        };

        worker.start();
        return true;
    }


    void tcpip_listener()
    {
        while (!isShutdown())
        {
             try
             {
                 this.setStatusTxt("");
                 this.setGoodState( true );
                 tcp_s = new ServerSocket(TCP_SERVER_PORT);
                 tcp_s.setReuseAddress(true );
                 tcp_s.setReceiveBufferSize( 60000 );

                 final Socket s = tcp_s.accept();
                 s.setTcpNoDelay( true );

                 SwingWorker work = new SwingWorker()
                 {

                     @Override
                     public Object construct()
                     {
                         try
                         {

                             while( !isShutdown() )
                             {
                                 // HELLO CLIENT...
   
                                 if (!handle_ip_command( s ))
                                 {
                                     //System.out.println("Closing socket" );
                                     //if (in.available() == 0)
                                     {
                                         s.close();
                                         break;
                                     }
                                 }
                             }
                         }
                         catch ( Exception exc )
                         {
                             try
                             {
                                 if (!s.isClosed())
                                     s.close();
                             }
                            catch ( Exception lexc )
                            {}

                             if (!isShutdown())
                             {
                                 exc.printStackTrace();
                                 setStatusTxt( "Communication aborted: "  + exc.getMessage());
                                 setGoodState( false );
                                 Main.err_log( getStatusTxt() );
                             }
                         }
                         return null;
                     }

                 };

                 work.start();

             }
             catch ( Exception exc )
             {
                 if (!isShutdown())
                 {
                     exc.printStackTrace();
                     Main.err_log("Kommunikationsport geschlossen: "  + exc.getMessage() );
                     this.setStatusTxt( "Communication is closed (2 processes?): "  + exc.getMessage());
                     this.setGoodState( false );
                     LogicControl.sleep(5000);
                 }
             }
             if (tcp_s != null)
             {
                try
                {
                    tcp_s.close();
                } catch (IOException ex)
                {
                    ex.printStackTrace();
                }
             }
        }
    }

    @Override
    public void setShutdown(boolean shutdown)
    {


        try
        {
            tcp_s.close();
        }
        catch (Exception exc)
        {}
    }







    public String RMX_open( String db_name )
    {
        try
        {
            SQLWorker sql = Main.get_control().get_sql_worker();
            Connection con = sql.open_db_connect();

            int id = conn_list.size();
            conn_list.add( new ConnEntry( con, id ) );

            return "0: c" + id;
        }
        catch (Exception exception)
        {
            return "1: " + exception.getMessage();
        }
    }
    int get_id( String s )
    {
        try
        {
            return Integer.parseInt(s.substring(1));
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return -1;
    }
    char get_type( String s )
    {
        return s.charAt(0);
    }

    public String RMX_createStatement( String conn_id )
    {
        try
        {
            ConnEntry conn = get_conn( get_id(conn_id) );
            Statement sta = conn.conn.createStatement();
            
            int id = conn_list.size();

            sta_list.add( new StatementEntry( conn, sta, id ) );

            return "0: s" + id;
        }
        catch (Exception exception)
        {
            return "1: " + exception.getMessage();
        }
    }

    public String RMX_close( String conn_txt )
    {
        try
        {
            int id = get_id( conn_txt);
            int type = get_type( conn_txt );

            if (type == 'c')
            {
                ConnEntry conn = get_conn( id );
                if (conn != null)
                    conn.conn.close();

                drop_conn(id);

                // DROP ALL CONNECTED CONTENTS
                for (int i = 0; i < sta_list.size(); i++)
                {
                    StatementEntry ste = sta_list.get(i);
                    if (ste.ce == conn)
                    {
                        if (!ste.sta.isClosed())
                            ste.sta.close();
                        drop_sta(ste.id);
                        i--;
                    }
                }
                for (int i = 0; i < rs_list.size(); i++)
                {
                    ResultEntry rse = rs_list.get(i);
                    if (rse.ce == conn)
                    {
                        if (!rse.rs.isClosed())
                            rse.rs.close();
                        drop_rs(rse.id);
                        i--;
                    }
                }
            }
            
            if (type == 's')
            {
                StatementEntry sta = get_sta( id );
                if (sta != null)
                    sta.sta.close();

                drop_sta(id);
            }
            if (type == 'r')
            {
                ResultEntry rs = get_rs( id );
                if (rs != null)
                    rs.rs.close();

                drop_rs(id);
            }

            return "0: okay";
        }
        catch (Exception exception)
        {
            return "1: " + exception.getMessage();
        }
    }

    public String RMX_execute( String stmt_txt, String cmd )
    {
        try
        {
            Statement sta = get_sta( get_id(stmt_txt) ).sta;

            boolean ret = sta.execute(cmd);


            return "0: " + ((ret) ? "1" : "0");
        }
        catch (Exception exc)
        {
            Main.err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    public String RMX_executeUpdate( String stmt_txt, String cmd )
    {
        try
        {
            Statement sta = get_sta( get_id(stmt_txt) ).sta;

            int ret = sta.executeUpdate(cmd);


            return "0: " + ret;
        }
        catch (Exception exc)
        {
            Main.err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    public String RMX_executeQuery( String stmt_txt, String cmd )
    {
        try
        {
            StatementEntry ste = get_sta( get_id(stmt_txt) );

            ResultSet rs = ste.sta.executeQuery(cmd);

            int id = rs_list.size();
            rs_list.add( new ResultEntry( ste.ce, rs, id ) );

            return "0: r" + id;
        }
        catch (Exception exc)
        {
            Main.err_log("Call of query <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    public String RMX_getMetaData( String resultset )
    {
        try
        {
            ResultSet rs = get_rs( get_id(resultset) ).rs;

            XStream xstream = new XStream();
            String xml = xstream.toXML(rs.getMetaData());

            return "0: " + xml;
        }
        catch (Exception exc)
        {
            Main.err_log("Call of getMetaData gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    public String RMX_getSQLArrayResult( String resultset )
    {
        SQLArrayResult result = new SQLArrayResult("");
        ArrayList<ArrayList> list = new ArrayList<ArrayList>();

        try
        {
            ResultSet rs = get_rs( get_id(resultset) ).rs;

            while (rs.next() == true)
            {
                int cnt = rs.getMetaData().getColumnCount();

                if (result.getFieldList() == null)
                {
                    ArrayList<String> fieldList = new ArrayList<String>(cnt);
                    ArrayList<String> fieldTypeList = new ArrayList<String>(cnt);

                    for (int i = 1; i <= cnt; i++)
                    {
                        String name = rs.getMetaData().getColumnName(i);
                        String type = rs.getMetaData().getColumnTypeName(i);
                        fieldList.add(name);
                        fieldTypeList.add(type);

                    }
                    result.setFieldList(fieldList);
                    result.setFieldTypeList(fieldTypeList);
                }


                ArrayList<String> field_list = new ArrayList<String>(cnt);

                for (int i = 1; i <= cnt; i++)
                {
                    field_list.add(rs.getString(i));
                }
                list.add(field_list);
            }
            result.setResultList(list);


            XStream xstream = new XStream();
            String xml = xstream.toXML(result);

            return "0: " + xml;
        }
        catch (Exception exc)
        {
            Main.err_log("Call of getMetaData gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    public String RMX_getSQLFirstRowField( String conn_text, String qry, int field  )
    {
        Statement sta = null;
        ResultSet rs = null;
        try
        {
            ConnEntry conn = get_conn( get_id(conn_text) );
            sta = conn.conn.createStatement();

            rs = sta.executeQuery(qry);
            if (rs.next())
            {

                String ret = rs.getString(field + 1);
                return "0: " + ret;
            }
        }
        catch (Exception exc)
        {
            Main.err_log("Call of getSQLFirstRowField <" + qry + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
        finally
        {
            try
            {
                if (rs != null)
                    rs.close();
                if (sta != null)
                    sta.close();
            }
            catch (SQLException sQLException)
            {
            }

        }
        return "2: no data";
    }

    public String RMX_TXTFunctionCall( String func_name, String args  )
    {
        Communicator comm = Main.get_control().get_communicator();
        ArrayList<AbstractCommand> cmd_list = comm.get_cmd_array();

        // STRIP OFF STATION ID, ONLY REST TO FUNCS
        for (int i = 0; i < cmd_list.size(); i++)
        {
            AbstractCommand cmd = cmd_list.get(i);
            if (cmd.is_cmd( func_name ))
            {
                boolean ok = cmd.do_command( args );

                if (ok)
                {
                    return "0: " + cmd.get_answer();
                }
                else
                {
                    return "1: " + cmd.get_answer();
                }
            }
       }
       return "2: unknown function";
    }

    public String RMX_OpenOutStream( String stream_name, String args  )
    {
        try
        {
            File f = null;
            String filename = null;
            if (args.length() == 0)
            {
                filename = stream_name;                
            }
            if (filename.compareTo("TEMP") == 0)
            {
                f = File.createTempFile("os_", ".dat");
                f.deleteOnExit();
            }
            else
            {
                f = new File(filename);
            }
            FileOutputStream fos = new FileOutputStream(f);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1024*1024);

            int id = ostream_list.size();
            ostream_list.add(new OutputStreamEntry(bos, f, id));

            return "0: o" + id;
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

    public String RMX_CloseOutStream( String stream_id  )
    {
        try
        {
            OutputStream os = get_ostream( get_id(stream_id) ).os;

            os.close();

            return "0: ";
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        finally
        {
            drop_ostream(get_id(stream_id));
        }
    }

    public String RMX_WriteOutStream( Socket s, String stream_id, String slen  )
    {
        try
        {
            long len = Long.parseLong(slen);

            OutputStream os = get_ostream( get_id(stream_id) ).os;


            byte[] buff = new byte[64*1024];

            while( len > 0)
            {
                int rlen = buff.length;
                if (len < rlen)
                    rlen = (int)len;

                int rrlen = s.getInputStream().read(buff, 0, rlen);
                os.write(buff, 0, rrlen);

                len -= rrlen;
            }


            return "0: ";
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

   
    public String RMX_OpenInStream( String stream_name, String args  )
    {
        try
        {
            File f = null;
            String filename = null;
            if (args.length() == 0)
            {
                filename = stream_name;
            }
            f = new File(filename);
            if (!f.exists())
            {
                return "2: not exists";
            }
            if (!f.isFile())
            {
                return "2: not isfile";
            }
            long len = f.length();

            FileInputStream fis = new FileInputStream(f);
            BufferedInputStream bis = new BufferedInputStream(fis);

            int id = istream_list.size();
            istream_list.add(new InputStreamEntry(bis, f, id));

            return "0: i" + id + " LEN:" + len;
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

    public String RMX_CloseInStream( String stream_id  )
    {
        try
        {
            InputStream is = get_istream( get_id(stream_id) ).is;

            is.close();

            return "0: ";
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        finally
        {
            drop_istream(get_id(stream_id));
        }
    }

    public String RMX_ReadInStream( Socket s, String stream_id, String slen )
    {
        try
        {
            InputStream is = get_istream( get_id(stream_id) ).is;

            long len = Long.parseLong(slen);

            byte[] buff = new byte[64*1024];


            while( len > 0)
            {
                int rlen = buff.length;
                if (len < rlen)
                    rlen = (int)len;

                int rrlen = is.read(buff, 0, rlen);
                s.getOutputStream().write(buff, 0, rrlen);

                len -= rrlen;
            }
            return "0: ";
        }
        catch (IOException iOException)
        {
            return new String("2: Exception: " + iOException.getMessage());
        }
    }

    @Override
    public boolean initialize()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public boolean check_requirements( StringBuffer sb )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    

}