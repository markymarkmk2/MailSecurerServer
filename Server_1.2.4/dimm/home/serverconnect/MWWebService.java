package dimm.home.serverconnect;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.Commands.AbstractCommand;
import home.shared.SQL.SQLArrayResult;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.workers.SQLWorker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;





@WebService
public class MWWebService
{
    static ArrayList<ConnEntry> conn_list = new ArrayList<ConnEntry>();
    static ArrayList<StatementEntry> sta_list = new ArrayList<StatementEntry>();
    static ArrayList<ResultEntry> rs_list = new ArrayList<ResultEntry>();

    static ArrayList<InputStreamEntry> istream_list = new ArrayList<InputStreamEntry>();
    static ArrayList<OutputStreamEntry> ostream_list = new ArrayList<OutputStreamEntry>();


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

    @WebMethod(operationName = "open")
    public String open( @WebParam(name = "db_name") String db_name )
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

    @WebMethod(operationName = "createStatement")
    public String createStatement( @WebParam(name = "conn_id") String conn_id )
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

    @WebMethod(operationName = "close")
    public String close( @WebParam(name = "conn_txt") String conn_txt )
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
    void err_log( String s )
    {
        LogManager.msg_comm( LogManager.LVL_ERR, s );
    }

    @WebMethod(operationName = "execute")
    public String execute( @WebParam(name = "statement") String stmt_txt, @WebParam(name = "cmd") String cmd )
    {
        try
        {
            Statement sta = get_sta( get_id(stmt_txt) ).sta;

            boolean ret = sta.execute(cmd);


            return "0: " + ((ret) ? "1" : "0");
        }
        catch (Exception exc)
        {
            err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    @WebMethod(operationName = "executeUpdate")
    public String executeUpdate( @WebParam(name = "statement") String stmt_txt, @WebParam(name = "cmd") String cmd )
    {
        try
        {
            Statement sta = get_sta( get_id(stmt_txt) ).sta;

            int ret = sta.executeUpdate(cmd);


            return "0: " + ret;
        }
        catch (Exception exc)
        {
            err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    @WebMethod(operationName = "executeQuery")
    public String executeQuery( @WebParam(name = "statement") String stmt_txt, @WebParam(name = "cmd") String cmd )
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
            err_log("Call of query <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    @WebMethod(operationName = "getMetaData")
    public String getMetaData( @WebParam(name = "resultset") String resultset )
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
            err_log("Call of getMetaData gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }

    @WebMethod(operationName = "getSQLArrayResult")
    public String getSQLArrayResult( @WebParam(name = "resultset") String resultset )
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
            err_log("Call of getMetaData gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }
    @WebMethod(operationName = "getSQLFirstRowField")
    public String getSQLFirstRowField( @WebParam(name = "conn_text") String conn_text, @WebParam(name = "qry") String qry, @WebParam(name = "field") int field  )
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
            err_log("Call of getSQLFirstRowField <" + qry + "> gave :" + exc.getMessage());
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
    @WebMethod(operationName = "TXTFunctionCall")
    public String TXTFunctionCall( @WebParam(name = "func_name") String func_name, @WebParam(name = "args") String args  )
    {
        TCPCallConnect comm = Main.get_control().get_tcp_connect();
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

    @WebMethod(operationName = "OpenOutStream")
    public String OpenOutStream( @WebParam(name = "stream_name") String stream_name, @WebParam(name = "args") String args  )
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
            
            int id = ostream_list.size();
            ostream_list.add(new OutputStreamEntry(fos, f, id));

            return "0: o" + id;
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

    @WebMethod(operationName = "CloseOutStream")
    public String CloseOutStream( @WebParam(name = "stream_id") String stream_id  )
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

    @WebMethod(operationName = "WriteOutStream")
    public String WriteOutStream( @WebParam(name = "stream_id") String stream_id, byte[] data  )
    {
        try
        {
            OutputStream os = get_ostream( get_id(stream_id) ).os;


            if (!(os instanceof BufferedOutputStream) && data.length > 1024)
            {

                BufferedOutputStream bos = new BufferedOutputStream(os, data.length);
                get_ostream( get_id(stream_id) ).os = bos;
                os = bos;
            }

            os.write(data);

            return "0: ";
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

   
   

    @WebMethod(operationName = "OpenInStream")
    public String OpenInStream( @WebParam(name = "stream_name") String stream_name, @WebParam(name = "args") String args  )
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

    @WebMethod(operationName = "CloseInStream")
    public String CloseInStream( @WebParam(name = "stream_id") String stream_id  )
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

    @WebMethod(operationName = "ReadInStream")
    public byte[] ReadInStream( @WebParam(name = "stream_id") String stream_id, int len )
    {
        try
        {
            InputStream is = get_istream( get_id(stream_id) ).is;

            byte [] data = new byte[len + 3];
            int rlen = is.read( data, 3, len );
            if (rlen == -1)
            {
                return new String("1: eof").getBytes();
            }

            data[0] = '0';
            data[1] = ':';
            data[2] = ' ';

            if (rlen != len)
            {
                byte [] rdata = new byte[rlen + 3];
                System.arraycopy(data, 0, rdata, 0, rlen + 3);
                return rdata;
            }
            return data;
        }
        catch (IOException iOException)
        {
            return new String("2: Exception: " + iOException.getMessage()).getBytes();
        }
    }

    

}