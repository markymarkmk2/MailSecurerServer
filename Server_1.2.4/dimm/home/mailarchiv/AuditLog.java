/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.SQL.OptCBEntry;
import home.shared.SQL.UserSSOEntry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author mw
 */

 /* Audit-Log (sichtbar für Admin und Auditor)

     * Start Stop
     * Zyklische Standsmeldung
     * Parametrierung
     * Alle Clientcalls IP, Uhrzeit, Benutzer, Aktion,
     * - Exportieren (ist auf Client, muss gemeldet werden)
     *

     */

public class AuditLog
{
    public static final String AUDIT_DB = "AuditDB";
    public static String ADMIN_TOKEN = "Admin";
    public static String AUDIT_TOKEN = "Auditor";
    public static String USER_TOKEN = "User";

    private static final String DEFAULT_DERBY_CONNECT = "jdbc:derby:AuditDB";

    private static final String CREATE_AUDIT_DB = "create table audit_log " +
            "(ma_id int, " +
            "ts varchar(40), " +
            "usertype varchar(80), " +
            "username varchar(80), " +
            "role_name varchar(80), " +
            "cmd varchar(1024), " +
            "args varchar(1024), " +
            "answer varchar(2048) )";

    private static final String[] CREATE_INDICES =
    {
        "create index ma_idx on audit_log(ma_id)",
        "create index ts_idx on audit_log(ts)",
        "create index ut_idx on audit_log(usertype)",
        "create index un_idx on audit_log(username)",
        "create index ro_idx on audit_log(role_name)"
    };

    static String[] no_log_cmds =
    {
        "RMX_open",
        "RMX_close",
        "RMX_executeQuery",
        "RMX_createStatement",
        "RMX_getMetaData",
        "RMX_getSQLArrayResult",
        "RMX_getSQLFirstRowField",
        "RMX_OpenOutStream",
        "RMX_CloseOutStream",
        "RMX_CloseDeleteOutStream",
        "RMX_WriteOutStream",
        "RMX_WriteOut",
        "RMX_OpenInStream",
        "RMX_OpenInStream",
        "RMX_CloseInStream",
        "RMX_ReadInStream",
        "RMX_ReadIn",
        "SearchMail CMD:open_filter CMD:get CMD:close CMD:open",
        "ListVaultData"
    };

    static AuditLog self;
    String db_connect_string;
    final ReentrantLock lock;

    
    public static AuditLog getInstance()
    {
        if (self == null)
        {
            self = new AuditLog();
        }
        return self;        
    }

    private AuditLog()
    {
        db_connect_string = Main.get_prop(GeneralPreferences.AUDIT_DB_CONNECT, DEFAULT_DERBY_CONNECT );
        lock = new ReentrantLock();
        
        init_db();
    }
    void Take()
    {
        lock.lock();


    }
    void Release()
    {
        lock.unlock();
    }



    void init_db()
    {
        try
        {
            Connection conn = open_db();
            close_db(conn);
            if (check_db_changes( "select count(*) from audit_log where ma_id=-2", true, CREATE_AUDIT_DB, null))
            {
                create_indices();
            }

            // FURTHER ADDINGS TO COME
        }
        catch (SQLException sQLException)
        {
            LogManager.err_log("Cannot create new audit database", sQLException);
        }
    }

    public Connection open_db() throws SQLException
    {
        try
        {
            return DriverManager.getConnection(db_connect_string, "APP", "");
        }
        catch (SQLException sQLException)
        {
            LogManager.info_msg("Creating new audit database: " + sQLException.getMessage());
            return create_db();
        }
    }

    Connection create_db() throws SQLException
    {
        Connection conn = DriverManager.getConnection(db_connect_string  + ";create=true", "APP", "" );
        return conn;
    }
    void close_db(Connection c) throws SQLException
    {
        if (c != null)
        {
            c.close();
        }
    }

    static void init()
    {}


    boolean no_log( String cmd, String args )
    {
        for (int i = 0; i < no_log_cmds.length; i++)
        {
            String[] list = no_log_cmds[i].split(" ");

            if (cmd.compareToIgnoreCase( list[0]) == 0)
            {
                if (list.length > 1)
                {
                    // FILTER COMMAND WITH ARG COMPONENT
                    for (int j = 1; j < list.length; j++)
                    {
                        String arg = list[j];
                        if (args.indexOf( arg ) >= 0)
                        {
                            //System.out.println("AUDIT Ignore : " + cmd + " "+ args);
                            return true;
                        }
                    }
                }
                else
                {
                   // System.out.println("AUDIT Ignore : " + cmd + " "+ args);
                    return true;
                }
            }
        }
        return false;
    }
    
    String get_user_type( UserSSOEntry ssoc )
    {
        if (ssoc.is_admin())
            return ADMIN_TOKEN;
        
        if (ssoc.role_has_option(OptCBEntry.AUDIT))
            return AUDIT_TOKEN;
        
        return USER_TOKEN;
    }

    void log( UserSSOEntry ssoc, String cmd, String args, String answer )
    {
        if (ssoc != null)
        {
            String role = "";
            if (ssoc.getRole() != null)
            {
                role = ssoc.getRole().getName();
            }
            log( ssoc.get_ma_id(), get_user_type(ssoc), ssoc.getUser(), role, cmd, args, answer );
        }
        else
            log(  cmd, args, answer );
    }

    void log( int ma_id, String usertype, String user, String role_name, String cmd, String args, String answer )
    {
       /* cmd = escape_quote( cmd );
        args = escape_quote( args );
        answer = escape_quote( answer );*/

        if (cmd.length() >= 1024)
        {
            cmd = cmd.substring(0, 1023);
        }
        if (args.length() >= 1024)
        {
            args = args.substring(0, 1023);
        }
        if (answer.length() >= 1024)
        {
            answer = answer.substring(0, 1023);
        }
        answer = filter_pwd( answer );
        args = filter_pwd( args );

//        System.out.println("AUDIT: " + ma_id + " UT:" + usertype + " US:" + user + " RO:" + role_name + " CM:" + cmd + " AR:" + args + " AN:" + answer);
        long ts = System.currentTimeMillis();

        String sql_cmd = "insert into audit_log ( ma_id, ts, usertype, username, role_name, cmd, args, answer ) values (?,?,?,?,?,?,?,?)";

        Take();


        Connection conn = null;
        PreparedStatement pst = null;
        try
        {
            conn = open_db();

            pst = conn.prepareStatement(sql_cmd);
            pst.setInt(1, ma_id);
            pst.setString( 2, Long.toString(ts));
            pst.setString( 3, usertype );
            pst.setString( 4, user );
            pst.setString( 5, role_name );
            pst.setString( 6, cmd );
            pst.setString( 7, args );
            pst.setString( 8, answer );

            pst.execute();
        }
        catch (SQLException sQLException)
        {
            LogManager.err_log("Cannot add to audit_log", sQLException);
        }
        finally
        {
            try
            {
                if (pst != null)
                {
                    pst.close();

                }
                if (conn != null)
                {
                    close_db(conn);

                }
            }
            catch (SQLException sQLException)
            {
            }
            Release();
        }
    }
    
    void log( String cmd, String args, String answer )
    {
        log( -1, "system", "system", "", cmd, args, answer);
    }

    public void start(String[] params )
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < params.length; i++)
        {
            if (i > 0)
                sb.append(" ");
            sb.append( params[i] );
        }

        log( "startup", sb.toString(), "" );
    }
    public void stop()
    {
        log( "shutdown", "", "" );
    }

    public void sql_call( UserSSOEntry ssoc, String cmd )
    {
        log( ssoc, "sql_call", cmd, "" );
    }

    public void object_delete( UserSSOEntry ssoc, Object o )
    {
        log( ssoc, "delete", o.getClass().getSimpleName(), o.toString() );
    }

    public void call_function( UserSSOEntry ssoc, String cmd, ArrayList<Object> args )
    {

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < args.size(); i++)
        {
            if (i > 0)
                sb.append(" ");

            Object o = args.get(i);
            sb.append( o.toString() );
        }

        String args_array = sb.toString();

        if (no_log(cmd, args_array))
            return;

        log( ssoc, cmd, args_array, "");
    }



    public void call_function( UserSSOEntry sso_entry, String cmd, ArrayList<String> params, String _answer )
    {

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < params.size(); i++)
        {
            if (i > 0)
                sb.append(" ");
            
            sb.append( params.get(i) );
        }

        String args_array = sb.toString();

        if (no_log(cmd, args_array))
            return;

        log( sso_entry, cmd, sb.toString(), _answer);
    }

    public void call_function( UserSSOEntry sso_entry, String cmd, ArrayList<String> params, Object ret )
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < params.size(); i++)
        {
            sb.append( params.get(i) );
        }
        String args_array = sb.toString();

        if (no_log(cmd, args_array))
            return;

        String answer = "";
        if (ret != null)
            answer = ret.toString();

        log( sso_entry, cmd, sb.toString(), answer);
    }

    boolean check_db_changes(String check_qry, boolean on_fail, String alter_cmd, String fill_cmd)
    {

        boolean failed = false;
        boolean changed = false;

        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;


        try
        {
            conn = open_db();
        }
        catch (SQLException sQLException)
        {
            LogManager.err_log_fatal("Cannot connect to audit table" , sQLException);
            return false;
        }
        try
        {
            st = conn.createStatement();
            rs = st.executeQuery(check_qry);

            if (!rs.next())
                throw new Exception( "Missing field" );
        }
        catch (Exception exc)
        {
            failed = true;
        }
        finally
        {
            if (rs != null)
            {
                try
                {
                    rs.close();
                }
                catch (SQLException sQLException)
                {
                }
                rs = null;
            }
        }


        if ((failed && on_fail) || (!failed && !on_fail))
        {
            LogManager.info_msg("Performing database update: " + alter_cmd);
            try
            {
                st.executeUpdate(alter_cmd);
                changed = true;
            }
            catch (Exception exc)
            {
                LogManager.err_log_fatal("Cannot change table struct " +  alter_cmd, exc);
                try
                {
                    st.close();
                    conn.close();
                }
                catch (SQLException sQLException)
                {
                }
                return false;
            }
            if (fill_cmd != null)
            {
                try
                {
                    st.execute(fill_cmd);
                }
                catch (Exception exc)
                {
                    LogManager.err_log_fatal("Cannot fill changed table struct " +  fill_cmd, exc);
                    return changed;
                }
            }
        }
        try
        {
            if (st != null)
                st.close();
            if (conn != null)
                conn.close();
        }
        catch (SQLException sQLException)
        {
        }


        return changed;
    }
    public static void main( String[] args)
    {

        AuditLog alog = AuditLog.getInstance();

        alog.log( -1, "Testtype", "Testuster", "testrole", "TestCmd", "Testargs", "Testanswer");
        

    }

    private void create_indices()
    {
        Take();

        Connection conn = null;
        Statement st = null;
        try
        {
            conn = open_db();

            st = conn.createStatement();

            for (int i = 0; i < CREATE_INDICES.length; i++)
            {
                String sql_cmd = CREATE_INDICES[i];
                st.execute(sql_cmd);
            }
        }
        catch (SQLException sQLException)
        {
            LogManager.err_log("Cannot create indices in audit_log", sQLException);
        }
        finally
        {
            try
            {
                if (st != null)
                {
                    st.close();
                }
                if (conn != null)
                {
                    close_db(conn);
                }
            }
            catch (SQLException sQLException)
            {
            }
            Release();
        }
    }

    private String escape_quote( String cmd )
    {
        return cmd.replaceAll("'", " ");
    }
    private String filter_pwd( String answer)
    {
        answer = filter_pwd(answer, "PW:");
        answer = filter_pwd(answer, "PWD:");
        return answer;
    }


    private String filter_pwd( String txt, String pw_token )
    {
        int idx = txt.indexOf(pw_token);
        if (idx == -1)
        {
            return txt;
        }
        StringBuffer sb = new StringBuffer();
        sb.append( txt.substring(0, idx ) );
        sb.append(pw_token);
        sb.append("********");

        // APPEND REST OF ARGS
        idx = txt.indexOf(' ', idx);
        if (idx > 0)
            sb.append( txt.substring( idx ) );

        return sb.toString();

    }

    public void close_db()
    {
        
        Take();

        // IS CLOSED LOGICALLY, NOW WE CLOSE PHYSICALLY
        try
        {
            DriverManager.getConnection(DEFAULT_DERBY_CONNECT + ";shutdown=true");
        }
        catch (SQLException sQLException)
        {
        }

    }

    public void reopen_db()
    {
        Release();
    }


}
