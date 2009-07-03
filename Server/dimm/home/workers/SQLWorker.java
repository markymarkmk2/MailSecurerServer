/*
 * SQLWorker.java
 *
 * Created on 10. Oktober 2007, 12:28
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.workers;


import dimm.home.mailarchiv.Commands.IPConfig;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;



/**
 *
 * @author Administrator
 */
public class SQLWorker extends WorkerParent
{
    
/*
Hallo Herr Schenck,
hier die erforderlichen Daten:
thales
SSH: root / 92Wn5Yi6t0	
MySQL root /eKmIklz37T

 */
    //private static final String db_server="thales.ebiz-webhosting.de";
    private static final String db_server="localhost";
    private static final String db_user="postgres";
    private static final String db_pwd="12345";
    private static final String connect = "jdbc:postgresql://localhost/datavault/";
    private static final String local_connect = "jdbc:hsqldb:file:" + Main.DATABASEPATH;
    
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
                    Main.err_log_fatal("Cannot connect to local database, i am lost");
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
                Class.forName("com.mysql.jdbc.Driver").newInstance();
                String hsql_db_class_name = Main.get_prop(GeneralPreferences.DB_CLASSNAME, "org.hsqldb.jdbcDriver");
                Class.forName(hsql_db_class_name);

                driver_loaded = true;
            }

        }
        catch (Exception exception)
        {
            Main.err_log_fatal("Cannot load jdbc drivers: " + exception.getMessage());
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

                System.out.println("Mandant " + id + " is " + name );
                
            }
                        
            rs.close();
            sta.close();
            c.close();                                
            
        } 
        catch (Exception ex)
        {
            Main.err_log_fatal("Cannot initialize SQL-Worker: " + ex.getMessage() );
            ex.printStackTrace();            
            return false;
        }        
                
        return true;
    }


    void sql_worker()    
    {
        
        while( true )
        {            
            // SOMETHING TO DO ?
            lock_list();
            
            int st_cnt = stmt_list.size();
            
            unlock_list();
            
            int cycle_tyme = (int)Main.get_long_prop( GeneralPreferences.SQL_CYCLETIME, (long)15 );
            
            long now = System.currentTimeMillis();
            
            
           
            LogicControl.sleep(1 * 1000);                        
        }
    }
    
    public static String html_to_native( String txt )
    {
        if (txt.indexOf('&') == -1)
            return txt;
        
        txt = txt.replaceAll("&amp;", "&");
        txt = txt.replaceAll("&auml;", "�");
        txt = txt.replaceAll("&ouml;", "�");
        txt = txt.replaceAll("&uuml;", "�");
        txt = txt.replaceAll("&Auml;", "�");
        txt = txt.replaceAll("&Ouml;", "�");
        txt = txt.replaceAll("&Uuml;", "�");
        txt = txt.replaceAll("&quot;", "\"");
        txt = txt.replaceAll("&lt;", "<");
        txt = txt.replaceAll("&gt;", ">");
        txt = txt.replaceAll("&ccedil;", "�");
        txt = txt.replaceAll("&eacute;", "�");
        txt = txt.replaceAll("&egrave;", "�");
        txt = txt.replaceAll("&aacute;", "�");
        txt = txt.replaceAll("&agrave;", "�");
        txt = txt.replaceAll("&ugrave;", "�");
        txt = txt.replaceAll("&Egrave;", "�");
        txt = txt.replaceAll("&Agrave;", "�");
        txt = txt.replaceAll("&acute;", "�");
        txt = txt.replaceAll("&szlig;", "�");
        txt = txt.replaceAll("&euml;", "�");
        txt = txt.replaceAll("&atilde;", "�" );
        txt = txt.replaceAll("&aring;", "�" );
                
      
        return txt;
    }        




    public boolean start_run_loop()
    {
        SwingWorker worker = new SwingWorker()
        {
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
        return true;    }

    public boolean check_requirements(StringBuffer sb)
    {
        return true;
    }

    private void flush_sql_error_buffer( File[] f, Statement sql_stm )
    {
        Main.info_msg("Flushing SQL statement buffer (" + f.length + " entries left)");
                        
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
                Main.err_log("Error occured while flushing file " + f[i].getName() + ": " + exc.getMessage());
                errs_in_a_row++;
            }
            if (errs_in_a_row > 3)
            {
                Main.err_log("Too many errors while flushing statement buffer, aborting");
                break;
            }
        }        
        if (i == f.length)
            Main.info_msg("SQL statement buffer was flushed completely");

    }

 
    
}


