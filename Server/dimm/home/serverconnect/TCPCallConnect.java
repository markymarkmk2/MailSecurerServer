
package dimm.home.serverconnect;


import com.thoughtworks.xstream.XStream;
import dimm.home.hibernate.HibernateUtil;
import dimm.home.mailarchiv.Commands.AbstractCommand;
import dimm.home.mailarchiv.Commands.AuthUser;
import dimm.home.mailarchiv.Commands.GetLog;
import dimm.home.mailarchiv.Commands.GetSetOption;
import dimm.home.mailarchiv.Commands.GetStatus;
import dimm.home.mailarchiv.Commands.HelloCommand;
import dimm.home.mailarchiv.Commands.IPConfig;
import dimm.home.mailarchiv.Commands.ImportMailFile;
import dimm.home.mailarchiv.Commands.ListOptions;
import dimm.home.mailarchiv.Commands.ListUsers;
import dimm.home.mailarchiv.Commands.Ping;
import dimm.home.mailarchiv.Commands.ReIndex;
import dimm.home.mailarchiv.Commands.ReadLog;
import dimm.home.mailarchiv.Commands.Reboot;
import dimm.home.mailarchiv.Commands.Restart;
import dimm.home.mailarchiv.Commands.RestartMandant;
import dimm.home.mailarchiv.Commands.SearchCommand;
import dimm.home.mailarchiv.Commands.SetStation;
import dimm.home.mailarchiv.Commands.ShellCmd;
import dimm.home.mailarchiv.Commands.StartVPN;
import dimm.home.mailarchiv.Commands.TestLogin;
import dimm.home.mailarchiv.Commands.UploadCertificate;
import dimm.home.mailarchiv.Commands.UploadMailFile;
import dimm.home.mailarchiv.Commands.WriteFile;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.LogicControl;
import home.shared.SQL.SQLArrayResult;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.MandantPreferences;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import dimm.home.mailarchiv.Utilities.SwingWorker;
import dimm.home.mailarchiv.WorkerParent;
import dimm.home.workers.SQLWorker;
import home.shared.CS_Constants;
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
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.StringTokenizer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import org.apache.commons.codec.binary.Base64;
import org.hibernate.SessionFactory;



public class TCPCallConnect extends WorkerParent
{
    ArrayList<AbstractCommand> cmd_list;

    final ArrayList<ConnEntry> conn_list = new ArrayList<ConnEntry>();
    final ArrayList<StatementEntry> sta_list = new ArrayList<StatementEntry>();
    final ArrayList<ResultEntry> rs_list = new ArrayList<ResultEntry>();
    final ArrayList<InputStreamEntry> istream_list = new ArrayList<InputStreamEntry>();
    final ArrayList<OutputStreamEntry> ostream_list = new ArrayList<OutputStreamEntry>();

    // 10 Agents parallel
    private static final int backlog = 10;
    private static final int TCPCMDBUFF_LEN = 80;
    byte[] tcp_cmd_buff;
    private static final int TCP_LEN = 64;
    public static final String HELLO_CMD = "HELLO";
    ServerSocket tcp_s;
    String server_ip;
    int server_port;
    MandantContext m_ctx;
    boolean use_ssl;
    private boolean is_started;
    private static final int DBG_LVL_VERB = 7;

    public TCPCallConnect( MandantContext m_ctx)
    {
        super("TCPCallConnect");
        this.m_ctx = m_ctx;
        
        use_ssl = Main.get_bool_prop(GeneralPreferences.SERVER_SSL, false);


        if (m_ctx != null)
        {
            server_ip = m_ctx.getPrefs().get_prop(MandantPreferences.SERVER_IP, Main.ws_ip );
            server_port = 0;

            // THIS IS NOT VISIBLE FOR CLIENT, WE HAVE TO PUT PORT INTO MANADANT TO MAKE THIS WORK
         /*   String port_text = m_ctx.getPrefs().get_prop(MandantPreferences.SERVER_PORT);
            try
            {
                server_port = Integer.parseInt(port_text);
                LogManager.info_msg("Setting TCP-Port for mandant " + m_ctx.getMandant().getName() + " to " + server_port);
            }
            catch (NumberFormatException numberFormatException)
            {
                LogManager.err_log_fatal("Invalid Port for TCP-Server");
            }*/
            if (server_port == 0)
            {
                server_port = Main.ws_port + 1 + m_ctx.getMandant().getId();
                LogManager.err_log_warn("Setting TCP-Port for mandant " + m_ctx.getMandant().getName() + " to " + server_port);
            }
            
        }
        else
        {
            server_port = Main.ws_port;
        }
        if (!use_ssl)
        {
            LogManager.err_log_warn("Starting TCPListener w/o SSL on  port " + server_port);
        }


        tcp_cmd_buff = new byte[TCPCMDBUFF_LEN];

        cmd_list = new ArrayList<AbstractCommand>();

        cmd_list.add( new HelloCommand() );
        cmd_list.add( new GetSetOption() );
        cmd_list.add( new ListOptions() );
        cmd_list.add( new IPConfig() );
        cmd_list.add( new Ping() );
        cmd_list.add( new ReadLog() );
        cmd_list.add( new Reboot() );
        cmd_list.add( new Restart() );
        cmd_list.add( new GetStatus() );
        cmd_list.add( new ShellCmd() );
        cmd_list.add( new GetLog() );
        cmd_list.add( new SetStation() );
        cmd_list.add( new WriteFile() );
        cmd_list.add( new StartVPN() );
        cmd_list.add( new ImportMailFile() );
        cmd_list.add( new UploadMailFile() );
        cmd_list.add( new TestLogin() );
        cmd_list.add( new UploadCertificate() );
        cmd_list.add( new SearchCommand() );
        cmd_list.add( new RestartMandant() );
        cmd_list.add( new AuthUser() );
        cmd_list.add( new ListUsers() );
        cmd_list.add( new ReIndex() );
    }
    public void add_command_list( ArrayList<AbstractCommand> list )
    {
        for (int i = 0; i < list.size(); i++)
        {
            AbstractCommand abstractCommand = list.get(i);
            if (!cmd_list.contains(abstractCommand))
                cmd_list.add(abstractCommand);
        }
    }

    public ArrayList<AbstractCommand> get_cmd_array()
    {
        return cmd_list;
    }

    ConnEntry get_conn( int id )
    {
        synchronized( conn_list )
        {
        for (int i = 0; i < conn_list.size(); i++)
        {
            ConnEntry connEntry = conn_list.get(i);
            if (connEntry.id == id)
            {
                return connEntry;
            }
        }
        return null;
        }    }

    StatementEntry get_sta( int id )
    {
        synchronized( sta_list )
        {
        for (int i = 0; i < sta_list.size(); i++)
        {
            StatementEntry staEntry = sta_list.get(i);
            if (staEntry.id == id)
            {
                return staEntry;
            }
        }
        return null;
    }}

    ResultEntry get_rs( int id )
    {
        synchronized( rs_list )
        {
        for (int i = 0; i < rs_list.size(); i++)
        {
            ResultEntry rsEntry = rs_list.get(i);
            if (rsEntry.id == id)
            {
                return rsEntry;
            }
        }
        return null;
    }}

    public InputStreamEntry get_istream( int id )
    {
        synchronized( istream_list )
        {
        for (int i = 0; i < istream_list.size(); i++)
        {
            InputStreamEntry rsEntry = istream_list.get(i);
            if (rsEntry.id == id)
            {
                return rsEntry;
            }
        }
        }
        return null;
    }

    public OutputStreamEntry get_ostream( int id )
    {
        synchronized( ostream_list )
        {
        for (int i = 0; i < ostream_list.size(); i++)
        {
            OutputStreamEntry rsEntry = ostream_list.get(i);
            if (rsEntry.id == id)
            {
                return rsEntry;
            }
        }
        }
        return null;
    }
    String new_conn_entry( Connection c )
    {
        synchronized( conn_list )
        {
            int id = conn_list.size();
            conn_list.add(new ConnEntry( c, id));
            return "c" + id;
        }
    }

    String new_statement_entry( ConnEntry conn, Statement s )
    {
        synchronized( ostream_list )
        {
            int id = sta_list.size();
            sta_list.add(new StatementEntry(conn, s, id));
            return "s" + id;
        }
    }
    String new_result_entry( ConnEntry conn,  ResultSet rs )
    {
        synchronized( rs_list )
        {
            int id = rs_list.size();
            rs_list.add(new ResultEntry(conn, rs, id));
            return "r" + id;
        }
    }

    String new_outstream_entry( OutputStream is, File f )
    {
        synchronized( ostream_list )
        {
            int id = ostream_list.size();
            ostream_list.add(new OutputStreamEntry(is, f, id));
            return "o" + id;
        }
    }
    String new_instream_entry( InputStream is, File f )
    {
        synchronized( istream_list )
        {
            int id = istream_list.size();
            istream_list.add(new InputStreamEntry(is, f, id));
            return "i" + id;
        }
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
        synchronized( istream_list )
        {
            InputStreamEntry c = get_istream(id);
            if (c != null)
                istream_list.remove(c);
        }
    }

    void drop_ostream( int id )
    {
        synchronized( ostream_list )
        {
            OutputStreamEntry c = get_ostream(id);
            ostream_list.remove(c);
        }
    }

    /* WE HAVE THE FOLLOWING RESTRIUCTIONS FOR REMOTE CALLABLE FUNCTIONS:
     *
     *  - NAME STARTS WITH RMX_
     *  - TWO TYPES OF PARAMETERS:
     *      1. 0 - 5 STRING PARAMETERS
     *      2. FIRST PARAM IS SOCKET, SECOND IS LONG, REST 0 - 3 STRING PARAMETERS
     *
     *  RETURN SHOULD BE "0: [optional text] OR <ERROR>: [optional text]
     *
     * */
    Object call_method( Socket s, long stream_len, String cmf, ArrayList<String> params ) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        Method[] list = this.getClass().getDeclaredMethods();
        String str = "";
        ArrayList<Object> args = new ArrayList<Object>();
        Long s_len = new Long(stream_len);


        for (int i = 0; i < list.length; i++)
        {
            Method method = list[i];
            String name = method.getName();
            if (!name.startsWith("RMX_"))
            {
                continue;
            }

            // RICHTIGE FUNKTION ?
            if (name.compareTo(cmf) == 0)
            {
                // SIND NICHT ZU VIELE PARAMETER DA?
                if (method.getParameterTypes().length >= params.size())
                {
                    Class[] paramc = method.getParameterTypes();

                    // WE HAVE SOCKET AS FIRST PARAM ?, THEN WE HAVE LEN AS SECOND (WLEN OR RLEN)
                    boolean with_socket = false;
                    if (paramc.length >= 2 && paramc[0] == s.getClass() && paramc[1] == s_len.getClass())
                    {
                        with_socket = true;
                    }

                    int j = 0;
                    int k = 0;

                    // CHECK CORRECT ARGS AND STORE ARS IN LIST
                    for (j = 0; j < paramc.length; j++)
                    {
                        // SKIP SOCK PARAMS (2)
                        if (with_socket && j < 2)
                        {
                            continue;
                        }

                        Object arg = null;

                        // ADD ARGUMENT TO ARGLIST IF AVAILABLE
                        if (k < params.size())
                        {
                            Class class1 = paramc[j];

                            // WE ONLY ACCEPT STRINGS RIGHT NOW
                            if ((class1 == str.getClass()))
                                arg = params.get(k);
                            else
                            {

                                try
                                {
                                    String cn = class1.getName();
                                    if (cn.compareTo("int") == 0 || cn.endsWith(".Integer"))
                                    {
                                        arg = new Integer(params.get(k));
                                    }
                                    else if (cn.compareTo("long") == 0 || cn.endsWith(".Long"))
                                    {
                                        arg = new Long(params.get(k));
                                    }
                                    else if (cn.compareTo("boolean") == 0 || cn.endsWith(".Boolean"))
                                    {
                                        arg = new Boolean(params.get(k));
                                    }
                                    else                                    
                                    {
                                        throw new Exception("Invalid class type");                                        
                                    }
                                }
                                catch (Exception exception)
                                {
                                    return "8: Error in parameters for comand " + name + ": " + class1.getName() + ": " + exception.getMessage() ;
                                }
                            }                            
                        }

                        // ARGS CONTAINS EXACTLY paramc.length ENTRIES
                        args.add(arg);
                        k++;
                    }

                    Object ret = null;
                    if (with_socket)
                    {

                        switch (paramc.length)
                        {
                            case 2:
                                ret = method.invoke(this, s, s_len);
                                break;
                            case 3:
                                ret = method.invoke(this, s, s_len, args.get(0));
                                break;
                            case 4:
                                ret = method.invoke(this, s, s_len, args.get(0), args.get(1));
                                break;
                            case 5:
                                ret = method.invoke(this, s, s_len, args.get(0), args.get(1), args.get(2));
                                break;
                            case 6:
                                ret = method.invoke(this, s, s_len, args.get(0), args.get(1), args.get(2), args.get(3));
                                break;
                            case 7:
                                ret = method.invoke(this, s, s_len, args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
                                break;
                            default:
                                ret = null;
                        }
                    }
                    else
                    {
                        switch (paramc.length)
                        {
                            case 0:
                                ret = method.invoke(this);
                                break;
                            case 1:
                                ret = method.invoke(this, args.get(0));
                                break;
                            case 2:
                                ret = method.invoke(this, args.get(0), args.get(1));
                                break;
                            case 3:
                                ret = method.invoke(this, args.get(0), args.get(1), args.get(2));
                                break;
                            case 4:
                                ret = method.invoke(this, args.get(0), args.get(1), args.get(2), args.get(3));
                                break;
                            case 5:
                                ret = method.invoke(this, args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
                                break;
                            default:
                                ret = null;
                        }
                    }

                    return ret;
                }
            }
        }
        return "9: cmd not found";
    }

    private boolean handle_ip_command( Socket s ) throws IOException
    {
        InputStream in = s.getInputStream();

        OutputStream out = s.getOutputStream();

        // TCP-PACKET HAS AT LEAST TCP_LEN BYTE
        byte[] buff = new byte[TCP_LEN];

        try
        {
            in.read(buff);
            if (buff[0] == 0)
            {
                return false;
            }
        }
        catch (IOException iOException)
        {
            // CLIENT CLOSED CONN
            LogManager.info_msg("Client " + s.getRemoteSocketAddress().toString() + " closed connection");
            return false;
        }
        
        String data = new String(buff, "UTF-8");
        ParseToken pt = new ParseToken(data);
        String cmd = pt.GetString("CMD:");
        long slen = pt.GetLongValue("SLEN:");
        int len = pt.GetLong("PLEN:").intValue();

        if (cmd == null || cmd.length() == 0)
        {
            return false;
        }


        byte[] add_data = null;
        if (len > 0)
        {
            add_data = new byte[len];
            int rlen = 0;
            while (rlen < len)
            {
                int llen = in.read(add_data, rlen, len - rlen);
                rlen += llen;
            }
        }

        dispatch_tcp_command(s, cmd, slen, add_data, out);

        return true;
    }

    void write_tcp_answer( boolean ok, String ret, OutputStream out ) throws IOException
    {
        Main.debug_msg( DBG_LVL_VERB, "Answer is <" + ret + ">");
        StringBuffer answer = new StringBuffer();

        if (ok)
        {
            answer.append("OK:LEN:");
        }
        else
        {
            answer.append("NOK:LEN:");
        }

        int alen = 0;

        if (ret != null)
        {
            alen = ret.getBytes("UTF-8").length;
        }

        answer.append(Integer.toString(alen));
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes());

        if (alen > 0)
        {
            out.write(ret.getBytes("UTF-8"));
        }

        out.flush();
    }

    void write_tcp_answer( boolean ok, long alen, InputStream in, OutputStream out ) throws IOException
    {
        Main.debug_msg(DBG_LVL_VERB, "Answer is stream with len " + alen + ">");

        StringBuffer answer = new StringBuffer();

        if (ok)
        {
            answer.append("OK:LEN:");
        }
        else
        {
            answer.append("NOK:LEN:");
        }


        answer.append(Long.toString(alen));
        while (answer.length() < TCP_LEN)
        {
            answer.append(" ");
        }


        out.write(answer.toString().getBytes("UTF-8"), 0, TCP_LEN);

        // PUSH DATA OVER BUFFER
        int buff_len = CS_Constants.STREAM_BUFFER_LEN;
        byte[] buff = new byte[buff_len];

        long start_len = alen;
        while (alen > 0)
        {
            long blen = alen;
            if (blen > buff_len)
            {
                blen = buff_len;
            }

            int rlen = in.read(buff, 0, (int) blen);
            if (rlen < 0)
            {
                System.out.println("Short read wants: " + start_len + " left over: " + alen);
                
                while (alen-- > 0)
                    out.write(' ');

                break;
                //throw new IOException("Short read wants: " + start_len + " left over: " + alen);
            }
            out.write(buff, 0, rlen);
            alen -= rlen;
        }
        in.close();
        out.flush();
    }

    void write_tcp_answer( boolean ok, InputStream is, long len, OutputStream out ) throws IOException
    {
        write_tcp_answer(ok, len, is, out);
    }

    void write_tcp_answer( boolean ok, String result, OutputStream out, long len ) throws IOException
    {
        write_tcp_answer(ok, result, out);
    }
    void write_tcp_answer( boolean ok, AbstractCommand cmd, OutputStream out ) throws IOException
    {
        if (cmd.has_stream())
        {
            write_tcp_answer( ok, cmd.get_data_len(), cmd.get_stream(), out );
        }
        else
        {
            write_tcp_answer( ok, cmd.get_answer(), out );
        }
    }


    void dispatch_tcp_command( Socket s, String cmd, long stream_len, byte[] add_data, OutputStream out ) throws IOException
    {

        if (cmd.equals("?") || cmd.equals("help"))
        {
            String answer = "Help!";

            write_tcp_answer(true, answer, out);
            return;
        }
        else
        {
            // BUILD ARGLIST (...)
            ArrayList<String> params = new ArrayList<String>();
            if (add_data != null)
            {
                String add_str = new String(add_data, "UTF-8");
                StringTokenizer str = new StringTokenizer(add_str, "|");
                while (str.hasMoreTokens())
                {
                    String arg = str.nextToken();
                    arg = decode_pipe(arg);
                    params.add(arg);
                }
            }
            StringBuffer args = new StringBuffer();
            for (int i = 0; i < params.size(); i++)
            {
                if (i > 0)
                    args.append(" ");

                args.append( params.get(i) );
            }
            Main.debug_msg(DBG_LVL_VERB - 1, "Received ip command <" + cmd + " " + args + "> ");

            // call_ ARE THE OLDSTYLE FUNCS
            if (cmd.substring(0,5).compareTo("call_") == 0 )
            {
                try
                {
                    String cmd_name = cmd.substring(5);
                    for (int i = 0; i < cmd_list.size(); i++)
                    {
                        AbstractCommand cmd_func = cmd_list.get(i);
                        if (cmd_func.is_cmd(cmd_name))
                        {
                            cmd_func.set_socket(s);
                            boolean ok = cmd_func.do_command(add_data);

                            write_tcp_answer(ok, cmd_func, out);
                            return;
                        }
                    }
                    Main.err_log("Unknown funtion call " + cmd_name);
                    throw new Exception( "Unknown function call " + cmd_name);
                }
                catch (Exception iOException)
                {
                    iOException.printStackTrace();
                    write_tcp_answer(false, iOException.getMessage(), out);
                    return;
                }
            }
            else  // LOOK FOR NEW RMX_ INTERFACE
            {
                try
                {
                    Object ret = call_method(s, stream_len, cmd, params);
                    
                    if (ret != null)
                    {
                        write_tcp_answer(true, ret.toString(), out);
                    }
                }
                catch (Exception exc)
                {
                    exc.printStackTrace();
                    write_tcp_answer(false, exc.getMessage(), out);
                }
            }
        }
    }

    @Override
    public boolean start_run_loop()
    {
        Main.debug_msg(1, "Starting communicator tasks");
        if (is_started)
            return true;

        start_tcpip_task();

        SwingWorker worker = new SwingWorker()
        {

            @Override
            public Object construct()
            {
                run_loop();

                return null;
            }
        };

        is_started = true;
        worker.start();
        return true;

    }

    private void run_loop()
    {
        int fallback_cnt = 0;
        while (!isShutdown())
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
        finished = true;

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
                this.setGoodState(true);
                if (use_ssl)
                {
                 //   System.setProperty("javax.net.debug","ssl");

                    final String[] enabledCipherSuites = { "SSL_DH_anon_WITH_RC4_128_MD5" };

                    SSLContext ctx;
                    KeyManagerFactory kmf;
                    KeyStore ks;
                    char[] passphrase = "123456".toCharArray();

                    ctx = SSLContext.getInstance("TLS");
                    kmf = KeyManagerFactory.getInstance("SunX509");
                    ks = KeyStore.getInstance("JKS");

                    ks.load(CS_Constants.class.getResourceAsStream("/Utilities/ms.keystore"), passphrase);
                    kmf.init(ks, passphrase);
                    ctx.init(kmf.getKeyManagers(), null, null);

                    SSLServerSocketFactory factory = ctx.getServerSocketFactory();


                    tcp_s = factory.createServerSocket(server_port, backlog);

                    if (tcp_s instanceof SSLServerSocket)
                    {
                        SSLServerSocket ssl = (SSLServerSocket)tcp_s;
                        ssl.setEnabledCipherSuites(enabledCipherSuites);                        
                    }
                }
                else
                {
                    tcp_s = new ServerSocket(server_port, backlog);
                }
                tcp_s.setReuseAddress(true);
                tcp_s.setReceiveBufferSize(CS_Constants.STREAM_BUFFER_LEN * 2);

                final Socket s = tcp_s.accept();
                s.setTcpNoDelay(true);

                SwingWorker work = new SwingWorker()
                {

                    @Override
                    public Object construct()
                    {
                        try
                        {

                            while (!isShutdown())
                            {
                                // HELLO CLIENT...
                                if (!handle_ip_command(s))
                                {
                                    // ON ERROR CLOSE
                                    s.close();
                                    break;
                                }
                            }
                        }
                        catch (Exception exc)
                        {

                            if (!isShutdown())
                            {
                                exc.printStackTrace();
                                setStatusTxt("Communication aborted: " + exc.getMessage());
                                setGoodState(false);
                                Main.err_log(getStatusTxt());
                            }
                        }
                        finally
                        {
                            try
                            {
                                if (!s.isClosed())
                                {
                                    s.close();
                                }
                            }
                            catch (Exception lexc)
                            {
                            }
                        }
                        return null;
                    }
                };

                work.start();

            }
            catch (Exception exc)
            {
                if (!isShutdown())
                {
                    exc.printStackTrace();
                    Main.err_log("Kommunikationsport geschlossen: " + exc.getMessage());
                    this.setStatusTxt("Communication is closed (2 processes?): " + exc.getMessage());
                    this.setGoodState(false);
                    LogicControl.sleep(5000);
                }
            }
            if (tcp_s != null)
            {
                try
                {
                    tcp_s.close();
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void setShutdown( boolean shutdown )
    {

        if (shutdown)
        {
            try
            {
                tcp_s.close();
            }
            catch (Exception exc)
            {
            }
        }
        super.setShutdown(shutdown);
    }

    public String RMX_open( String db_name )
    {
        try
        {
            SQLWorker sql = Main.get_control().get_sql_worker();
            Connection con = sql.open_db_connect();

            String id = new_conn_entry(con);

            return "0: " + id;
        }
        catch (Exception exception)
        {
            return "1: " + exception.getMessage();
        }
    }

    public int get_id( String s )
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
            ConnEntry conn = get_conn(get_id(conn_id));
            Statement sta = conn.conn.createStatement();

            String id = new_statement_entry(conn, sta);

            return "0: " + id;
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
            int id = get_id(conn_txt);
            int type = get_type(conn_txt);

            if (type == 'c')
            {
                ConnEntry conn = get_conn(id);
                if (conn != null)
                {
                    conn.conn.close();
                }

                drop_conn(id);

                // DROP ALL CONNECTED CONTENTS
                for (int i = 0; i < sta_list.size(); i++)
                {
                    StatementEntry ste = sta_list.get(i);
                    if (ste.ce == conn)
                    {
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
                        rse.rs.close();
                        
                        drop_rs(rse.id);
                        i--;
                    }
                }
            }

            if (type == 's')
            {
                StatementEntry sta = get_sta(id);
                if (sta != null)
                {
                    sta.sta.close();
                }

                drop_sta(id);
            }
            if (type == 'r')
            {
                ResultEntry rs = get_rs(id);
                if (rs != null)
                {
                    rs.rs.close();
                }

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
            Statement sta = get_sta(get_id(stmt_txt)).sta;

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
            Statement sta = get_sta(get_id(stmt_txt)).sta;

            int ret = sta.executeUpdate(cmd);


            return "0: " + ret;
        }
        catch (Exception exc)
        {
            Main.err_log("Call of execute <" + cmd + "> gave :" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }
    public String RMX_DeleteObject(  String cmd )
    {
        try
        {
            XStream xstream = new XStream();
            Object o = xstream.fromXML(cmd);

            SessionFactory s = HibernateUtil.getSessionFactory();
            org.hibernate.Transaction tx = s.getCurrentSession().beginTransaction();
            s.getCurrentSession().refresh(o);
            s.getCurrentSession().delete(o);
            tx.commit();
            
            return "0: ";
        }
        catch (Exception exc)
        {
            Main.err_log("Call of delete object failed:" + exc.getMessage());
            return "1: " + exc.getMessage();
        }
    }


    public String RMX_executeQuery( String stmt_txt, String cmd )
    {
        try
        {
            StatementEntry ste = get_sta(get_id(stmt_txt));

            ResultSet rs = ste.sta.executeQuery(cmd);

            String id = new_result_entry(ste.ce, rs);

            return "0: " + id;
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
            ResultSet rs = get_rs(get_id(resultset)).rs;

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
            ResultSet rs = get_rs(get_id(resultset)).rs;

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

    public String RMX_getSQLFirstRowField( String conn_text, String qry, int field )
    {
        Statement sta = null;
        ResultSet rs = null;
        try
        {
            ConnEntry conn = get_conn(get_id(conn_text));
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
                {
                    rs.close();
                }
                if (sta != null)
                {
                    sta.close();
                }
            }
            catch (SQLException sQLException)
            {
            }

        }
        return "2: no data";
    }
/*
    public String RMX_TXTFunctionCall( String func_name, String args )
    {
        Communicator comm = Main.get_control().get_communicator();
        ArrayList<AbstractCommand> txt_cmd_list = comm.get_cmd_array();

        // STRIP OFF STATION ID, ONLY REST TO FUNCS
        for (int i = 0; i < txt_cmd_list.size(); i++)
        {
            AbstractCommand cmd = txt_cmd_list.get(i);
            if (cmd.is_cmd(func_name))
            {
                boolean ok = cmd.do_command(args);

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
*/


    public String RMX_OpenOutStream( String stream_name, String args )
    {
        try
        {
            File f = null;
            String filename = null;
            if (args == null || args.length() == 0)
            {
                filename = stream_name;
            }
            if (filename.compareTo("TEMP") == 0)
            {
                f = m_ctx.getTempFileHandler().create_temp_file("", "os_", "dat");
            }
            else
            {
                f = new File(filename);
            }
            FileOutputStream fos = new FileOutputStream(f);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 1024 * 1024);

            String id = new_outstream_entry(bos, f);

            return "0: " + id;
        }
        catch (IOException iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        catch (Exception exception)
        {
            return "2: Exception: " + exception.getMessage();
        }
    }

    public String RMX_CloseOutStream( String stream_id )
    {
        try
        {
            OutputStream os = get_ostream(get_id(stream_id)).os;

            os.close();

            return "0: ";
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        finally
        {
            drop_ostream(get_id(stream_id));
        }
    }
    public String RMX_CloseDeleteOutStream( String stream_id )
    {
        try
        {
            OutputStream os = get_ostream(get_id(stream_id)).os;

            os.close();
            
            get_ostream(get_id(stream_id)).file.delete();

            return "0: ";
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        finally
        {
            drop_ostream(get_id(stream_id));
        }
    }

    public String RMX_WriteOutStream( Socket s, Long slen, String stream_id )
    {
        int buff_len = CS_Constants.STREAM_BUFFER_LEN;
        try
        {
            long len = slen.longValue();

            OutputStreamEntry ose = get_ostream(get_id(stream_id));
            if (ose == null)
            {
                return "2: Stream id " + stream_id + " not found";
            }
            OutputStream os = ose.os;
            BufferedOutputStream bos = new BufferedOutputStream(os,buff_len*2);
            BufferedInputStream bis = new BufferedInputStream(s.getInputStream(),buff_len*2);

            byte[] buff = new byte[buff_len];

            while (len > 0)
            {
                int rlen = buff.length;
                if (len < rlen)
                {
                    rlen = (int) len;
                }

                int rrlen = bis.read(buff, 0, rlen);
                bos.write(buff, 0, rrlen);

                len -= rrlen;
            }
            bos.flush();
            return "0: ";
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }

    public String RMX_WriteOut( Socket s, Long slen, String stream_id, String sdata )
    {
        try
        {
            sdata = decode_pipe(sdata);
            byte[] data = Base64.decodeBase64(sdata.getBytes());

            OutputStream os = get_ostream(get_id(stream_id)).os;

            os.write(data);

            return "0: ";
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }
    public String RMX_OpenInStream( String stream_name, String args )
    {
        try
        {
            File f = null;
            String filename = null;
            if (args == null || args.length() == 0)
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
            BufferedInputStream bis = new BufferedInputStream(fis, CS_Constants.STREAM_BUFFER_LEN);

            String id = new_instream_entry( bis, f );

            return "0: " + id + " LEN:" + len;
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
    }
    
    public String RMX_OpenInStream( InputStream is, long len )
    {
        String id = new_instream_entry( is, null );
        return "0: " + id + " LEN:" + len;
    }

    public String RMX_CloseInStream( String stream_id )
    {
        try
        {

            InputStreamEntry ise = get_istream(get_id(stream_id));
            if (ise != null)
            {
                ise.is.close();

                return "0: ";
            }
            return "1: not found: " + stream_id;
        }
        catch (Exception iOException)
        {
            return "1: Exception: " + iOException.getMessage();
        }
        finally
        {
            drop_istream(get_id(stream_id));
        }
    }

    public String RMX_ReadInStream( Socket s, Long slen, String stream_id )
    {
        try
        {
            InputStream is = get_istream(get_id(stream_id)).is;

            long len = slen.longValue();            

            write_tcp_answer( true, len, is, s.getOutputStream());
            return null; // NO ANSWER NEEDE
        }
        catch (Exception iOException)
        {
            return new String("2: Exception: " + iOException.getMessage());
        }
    }

    public String RMX_ReadIn( String stream_id, String slen )
    {
        try
        {
            int len = Integer.parseInt(slen);

            byte[] data = new byte[len];

            InputStream is = get_istream(get_id(stream_id)).is;

            is.read(data);
            String ret = "0: " + new String(Base64.encodeBase64(data));

            return ret;
        }
        catch (Exception iOException)
        {
            return new String("2: Exception: " + iOException.getMessage());
        }
    }

    @Override
    public boolean initialize()
    {
        // throw new UnsupportedOperationException("Not supported yet.");
        return true;
    }

    @Override
    public boolean check_requirements( StringBuffer sb )
    {
//        throw new UnsupportedOperationException("Not supported yet.");
        return true;
    }

    public static String decode_pipe( String s )
    {
        int idx = s.indexOf("^");
        if (idx == -1)
        {
            return s;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (ch != '^')
            {
                sb.append(ch);
                continue;
            }
            if (i + 2 < s.length())
            {
                if (s.charAt(i + 1) == '7' && s.charAt(i + 2) == 'C')
                {
                    sb.append('|');
                    i += 2;
                }
                if (s.charAt(i + 1) == '5' && s.charAt(i + 2) == 'E')
                {
                    sb.append('^');
                    i += 2;
                }
            }
        }
        return sb.toString();
    }

    public static String encode_pipe( String s )
    {
        int idx = s.indexOf("|");
        if (idx == -1)
        {
            return s;
        }
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

    @Override
    public String get_task_status()
    {
        return "";
    }
}
