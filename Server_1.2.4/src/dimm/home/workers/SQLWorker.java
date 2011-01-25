/*
 * SQLWorker.java
 *
 * Created on 10. Oktober 2007, 12:28
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;


import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import dimm.home.mailarchiv.WorkerParent;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.tool.hbm2ddl.SchemaExport;



/**
 *
 * @author Administrator
 */
public class SQLWorker extends WorkerParent
{
    

    //private static final String db_server="thales.ebiz-webhosting.de";
   //private static final String db_server="localhost";
    private static String db_user="APP";
    private static final String db_pwd="";
    //private static final String postgres_connect = "jdbc:postgresql://localhost/datavault/";
    private static final String derby_connect = "jdbc:derby:MailArchiv";
    //private static final String local_connect = "jdbc:hsqldb:file:" + Main.DATABASEPATH;
    private static final String SQL_UPD_PATH = "sql_update";
    
    ArrayList<String> stmt_list;
    Semaphore sql_mtx;
    
    
    
    public static final String NAME = "SQLWorker";
    
            
    public static final int DB_UPDATE_CYCLE_S = 10;
    public static final int MIN_TS_DB_CYCLE   = 30; 
    
    
    static SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss");    

    String last_vpn_ip;
    String last_real_ip;

    private static final int VPN_UPD_TIME_S = 60;
    
    long last_ts_db_changed = 0;  // MERKER FUER DB-AENDERUNGEN
    static boolean driver_loaded = false;
    int conn_timeout_s = 10;
    private String PARAM_DB = "param_db";

    private static String connect = derby_connect;

    public static void set_to_derby_db()
    {
        connect = derby_connect;
        db_user = "APP";
    }

    public static String get_db_connect_string()
    {
        return connect;
    }

    
    /** Creates a new instance of SQLWorker */
    public SQLWorker()
    {
        super(NAME);
        stmt_list = new ArrayList<String>();
        sql_mtx = new Semaphore(1);
        last_vpn_ip = null;
        last_real_ip = null;   
        
        conn_timeout_s = (int)Main.get_long_prop(GeneralPreferences.SQL_CONN_TIMEOUT, (long)4);        
    }
    
    void set_conn_timeout()
    {
        DriverManager.setLoginTimeout(conn_timeout_s);
    }
    

    
    Connection openSQLConnection( String database_str  ) throws SQLException
    {
        load_jdbc_drivers();

        // TRY ONLINE CONNECT
        try
        {
            set_conn_timeout();
            Connection conn = DriverManager.getConnection(get_connect_str(database_str), get_db_user(), get_db_pwd() );
                
            return conn;
        }
        catch (Exception exc)
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Cannot connect to local database, i am lost");
            throw new SQLException( "OpenSQLConnect failed: " + exc.getMessage() );
        }   
    }
    
    public Connection open_db_connect()  throws SQLException
    {
        return openSQLConnection( PARAM_DB );
    }

     
    
    void load_jdbc_drivers()
    {
        // LOAD LOCAL AND REMOTE DB
        try
        {
            if (!driver_loaded)
            {
                //Class.forName("com.mysql.jdbc.Driver").newInstance();
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
                /*String hsql_db_class_name = Main.get_prop(GeneralPreferences.DB_CLASSNAME, "org.hsqldb.jdbcDriver");
                Class.forName(hsql_db_class_name).newInstance();*/

                driver_loaded = true;
            }

        }
        catch (Exception exception)
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Cannot load jdbc drivers: " + exception.getMessage());
        }
    }
    
    
    public String get_connect_str( String database_str)
    {
        String s = get_connect_str(database_str, false);
        return s;
    }

    public String get_connect_str( String database_str, boolean offline)
    {
        return get_connect_str(database_str, 0, offline);
    }
    
    public String get_connect_str( String database_str, int server_idx, boolean offline)
    {
            return connect;
    }
    
    public String get_db_conn_str( boolean offline)
    {
        return get_connect_str( PARAM_DB, offline );
    }
    public String get_db_user( int server_idx, boolean offline)
    {
        if (offline)
            return "sa";

        
        return Main.get_prop( GeneralPreferences.DB_USER, db_user );
        
    }

    public String get_db_user( boolean offline)
    {
        return get_db_user( 0, offline );
    }
    
    public String get_db_pwd( int server_idx, boolean offline)
    {
        if (offline)
            return "";
        

        return Main.get_prop( GeneralPreferences.DB_PWD, db_pwd );
    }
    public String get_db_pwd( boolean offline)
    {
        return get_db_pwd( 0, offline );
    }
    

    boolean use_offline_connect()
    {
        return false;
    }
    
    public String get_db_user()
    {
        if (use_offline_connect())
            return "sa";
        
        return Main.get_prop( GeneralPreferences.DB_USER, db_user );
    }
    public String get_db_pwd()
    {
        if (use_offline_connect())
            return "";

        return Main.get_prop( GeneralPreferences.DB_PWD, db_pwd );
    }
    public boolean check_online()
    {
        boolean ret = false;
        try
        {
            set_conn_timeout();
            Connection conn = DriverManager.getConnection(get_connect_str(PARAM_DB, false), get_db_user(false), get_db_pwd(false));
            conn.close();
            ret = true;

        }
        catch (SQLException sQLException)
        {
            ret = false;
        }     
        return ret;
    }
        
    
    public boolean lock_list()
    {
        try
        {
            sql_mtx.acquire();
            
            return true;
        } catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
        System.out.println("lock failed");
        return false;
    }

    public void unlock_list()
    {
        sql_mtx.release();
    }    
    
    
    public synchronized void add_statement( String stmt )
    {
        lock_list();
        
        stmt_list.add( stmt );
        
        unlock_list();
    }
    public boolean work_sql_update( File f )
    {
        LogManager.msg_system( LogManager.LVL_INFO, "Calling SQL statement updates");

        Connection c = null;
        Statement sta = null;
        boolean  has_err = false;
        try
        {
            c = open_db_connect();
            sta = c.createStatement();

            int len = (int)f.length();

            char[] line_buf = new char[len];

            FileReader fr = new FileReader(f);
            fr.read(line_buf);
            fr.close();

            String line = new String(line_buf);

            StringTokenizer str = new StringTokenizer(line, "\n\r");
            while (str.hasMoreTokens())
            {
                String sql_st = str.nextToken().trim();
                if (sql_st.length() == 0)
                    continue;
                if (sql_st.startsWith("#"))
                    continue;

                // FORMAT : CHECK-STATEMENT;[ONOK|ONFAIL];DO-STATEMENT
                StringTokenizer line_str = new StringTokenizer(sql_st, ";");
                String check_sql_st = null;
                String condition =    null;
                String do_sql_st =    null;

                try
                {
                    check_sql_st = line_str.nextToken().trim();
                    condition = line_str.nextToken().trim().toLowerCase();
                    do_sql_st = line_str.nextToken().trim();
                }
                catch (Exception e)
                {
                    throw new Exception("Syntax error in sql update file " + f.getAbsolutePath() );
                }
                if (condition.compareTo("onok") != 0 && condition.compareTo("onfail") != 0 )
                {
                    throw new Exception("Syntax error in sql update file " + f.getAbsolutePath() );
                }


                try
                {
                    // CHECK IF WE FAIL ON FIRST STATEMENT WITH EXCEPTION
                    sta.execute(check_sql_st);
                    if (condition.compareTo("onok") == 0)
                    {
                        LogManager.msg_system( LogManager.LVL_INFO, "Calling statement: " + check_sql_st);
                        sta.execute(do_sql_st);
                    }
                    else
                    {
                        LogManager.msg_system( LogManager.LVL_ERR, "Statement <" + check_sql_st + "> passed, skipping <" + do_sql_st + ">");
                    }
                }
                catch (Exception e)
                {
                    // THIS IS REGULAR ON UPDATE DB
                    try
                    {
                        if (condition.compareTo("onfail") == 0)
                        {
                            LogManager.msg_system( LogManager.LVL_INFO, "Calling statement: " + check_sql_st);
                            sta.execute(do_sql_st);
                        }
                        else
                        {
                            LogManager.msg_system( LogManager.LVL_ERR, "Statement <" + check_sql_st + "> failed, skipping <" + do_sql_st + ">");
                        }
                    }
                    catch (Exception _exc)
                    {
                        LogManager.msg_system( LogManager.LVL_ERR, "Statement <" + sql_st + "> gave exception: " + _exc.getMessage());
                        has_err = true;
                    }
                }
            }

            if (!has_err)
            {
                File tmp = new File( f.getAbsolutePath() + "_ok");
                while (tmp.exists())
                {
                    tmp = new File( tmp.getAbsolutePath() + "x");
                }

                f.renameTo(tmp);
            }
            else
            {
                File tmp = new File( f.getAbsolutePath() + "_err");
                while (tmp.exists())
                {
                    tmp = new File( tmp.getAbsolutePath() + "x");
                }
                LogManager.msg_system( LogManager.LVL_ERR, "Saving sql update file to <" + tmp.getPath() + ">");
                f.renameTo(tmp);
            }

            return true;
        }
        catch (Exception exc)
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Error occured while updating database with " + f.getName() + ": " + exc.getMessage());
        }
        finally
        {
            try
            {
                if (sta != null)
                {
                    sta.close();

                }
                if (c != null)
                {
                    c.close();

                }
            }
            catch (SQLException sQLException)
            {
            }
        }
        return false;
    }


    @Override
    public boolean initialize()
    {                        
        try
        {
            // LOAD LOCAL AND REMOTE DB
            load_jdbc_drivers();            
            
            // READ STATIONNAME
            Connection c = open_db_connect();
            Statement sta = c.createStatement();
            ResultSet rs = sta.executeQuery("select id, name from mandant" );
            while (rs.next() == true)
            {
                String id = rs.getString(1);
                String name = rs.getString(2);
                LogManager.msg_system( LogManager.LVL_INFO, "Mandant " + id + " is " + name );
            }
                        
            rs.close();
            sta.close();
            c.close();                                            
        } 
        catch (Exception ex)
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Cannot initialize SQL-Worker: " + ex.getMessage() );
            ex.printStackTrace();            
            return false;
        }

        File sql_upd = new File(SQL_UPD_PATH);

        if (sql_upd.exists())
        {
            File[] sql_files = sql_upd.listFiles(new FileFilter()
            {

                @Override
                public boolean accept( File pathname )
                {
                    return pathname.getName().endsWith(".sql");
                }
            });

            for ( int i = 0; i < sql_files.length; i++)
            {
                work_sql_update( sql_files[i] );
            }
        }
                
        return true;
    }


    void sql_worker()    
    {
        
        while( !isShutdown() )
        {            
            // SOMETHING TO DO ?
            lock_list();
            
            int st_cnt = stmt_list.size();
            
            unlock_list();
            
            int cycle_tyme = (int)Main.get_long_prop( GeneralPreferences.SQL_CYCLETIME, (long)15 );
            
            long now = System.currentTimeMillis();
            
            
           
            LogicControl.sleep(1 * 1000);                        
        }
        finished = true;
    }
    
    public static String html_to_native( String txt )
    {
        if (txt.indexOf('&') == -1)
            return txt;
        
        txt = txt.replaceAll("&amp;", "&");
        txt = txt.replaceAll("&auml;", "ä");
        txt = txt.replaceAll("&ouml;", "ö");
        txt = txt.replaceAll("&uuml;", "ü");
        txt = txt.replaceAll("&Auml;", "Ä");
        txt = txt.replaceAll("&Ouml;", "Ö");
        txt = txt.replaceAll("&Uuml;", "Ü");
        txt = txt.replaceAll("&quot;", "\"");
        txt = txt.replaceAll("&lt;", "<");
        txt = txt.replaceAll("&gt;", ">");
        txt = txt.replaceAll("&ccedil;", "c");
        txt = txt.replaceAll("&eacute;", "é");
        txt = txt.replaceAll("&egrave;", "è");
        txt = txt.replaceAll("&aacute;", "á");
        txt = txt.replaceAll("&agrave;", "à");
        txt = txt.replaceAll("&ugrave;", "ù");
        txt = txt.replaceAll("&Egrave;", "È");
        txt = txt.replaceAll("&Agrave;", "À");
        txt = txt.replaceAll("&acute;", "á");
        txt = txt.replaceAll("&szlig;", "ß");
        txt = txt.replaceAll("&euml;", "e");
        txt = txt.replaceAll("&atilde;", "a" );
        txt = txt.replaceAll("&aring;", "a" );
                
      
        return txt;
    }        




    @Override
    public boolean start_run_loop()
    {
        if (!is_started)
        {
            BackgroundWorker worker = new BackgroundWorker(NAME)
            {
                @Override
                public Object construct()
                {
                    int ret = -1;
                    try
                    {
                        sql_worker();
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
            is_started = true;
        }
        return true;
    }

    @Override
    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    private void flush_sql_error_buffer( File[] f, Statement sql_stm )
    {
        LogManager.msg_system( LogManager.LVL_INFO, "Flushing SQL statement buffer (" + f.length + " entries left)");
                        
        int errs_in_a_row = 0;
        
        StringBuffer sb = new StringBuffer();
    
        int i = 0;
        
        // NOT MORE THAN 100 PER CYCLE
        int max = 100;
        if (max > f.length)
            max = f.length;
        
        for ( i = 0; i < max; i++)
        {
            try
            {
                int len = (int)f[i].length();

                char[] line_buf = new char[len];

                FileReader fr = new FileReader(f[i]);
                fr.read(line_buf);
                fr.close();
                f[i].delete();

                String line = new String(line_buf);

                StringTokenizer str = new StringTokenizer(line, "\n");
                while (str.hasMoreTokens())
                {
                    String sql_st = str.nextToken();
                    if (sql_st.charAt(0) == 0)
                        break;
                    sql_stm.execute(sql_st);
                }
                errs_in_a_row = 0;
            }                        
            catch (Exception exc)
            {
                LogManager.msg_system( LogManager.LVL_ERR, "Error occured while flushing file " + f[i].getName() + ": " + exc.getMessage());
                errs_in_a_row++;
            }
            if (errs_in_a_row > 3)
            {
                LogManager.msg_system( LogManager.LVL_ERR, "Too many errors while flushing statement buffer, aborting");
                break;
            }
        }        
        if (i == f.length)
            LogManager.msg_system( LogManager.LVL_INFO, "SQL statement buffer was flushed completely");

    }


    public static void build_hibernate_tables()
    {
        SQLWorker sql = new SQLWorker();
        if (sql.initialize())
        {
            LogManager.msg_system( LogManager.LVL_ERR, "Database is valid, cannot rebuild");
            return;
        }

        try
        {
            AnnotationConfiguration config = new AnnotationConfiguration().configure("/dimm/home/hibernate/hibernate.cfg.xml");
            new SchemaExport(config).create(true, true);
        }
        catch (HibernateException hibernateException)
        {
            hibernateException.printStackTrace();
        }
    }

    @Override
    public String get_task_status()
    {
        StringBuilder stb = new StringBuilder();

        stb.append("TC:" + stmt_list.size());

        return stb.toString();
    }

    @Override
    public String get_task_status( int ma_id )
    {
        return get_task_status();
    }



 
    
}



