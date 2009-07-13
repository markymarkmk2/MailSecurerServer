package dimm.home.Httpd;

import com.thoughtworks.xstream.XStream;
import dimm.general.SQL.SQLArrayResult;
import dimm.home.mailarchiv.Main;
import dimm.home.workers.SQLWorker;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;

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

@WebService
public class MWWebService
{
    static ArrayList<ConnEntry> conn_list = new ArrayList<ConnEntry>();
    static ArrayList<StatementEntry> sta_list = new ArrayList<StatementEntry>();
    static ArrayList<ResultEntry> rs_list = new ArrayList<ResultEntry>();


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
            Main.err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
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
            Main.err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
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
            Main.err_log("Call of query <" + cmd + "> gave :" + exc.getMessage());
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
            Main.err_log("Call of getMetaData gave :" + exc.getMessage());
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
            Main.err_log("Call of getMetaData gave :" + exc.getMessage());
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

}