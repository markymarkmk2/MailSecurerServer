/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.serverconnect;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 *
 * @author mw
 */
public class DimmCommand
{
    String server;
    int port;
    Socket socket;
    private static final int SHORT_TIMEOUT = 10000;

    public static final int JOB_INVALID = -1;
    public static final int JOB_SLEEPING = 0;
    public static final int JOB_BUSY = 1;
    public static final int JOB_READY = 2;
    public static final int JOB_USERQRY = 3;
    public static final int JOB_ERROR = 4;
    public static final int JOB_DELAYED = 5;
    public static final int JOB_WAITING = 6;
    public static final int JOB_USER_READY = 7;
    public static final int JOB_WARNING = 8;


    public DimmCommand( String server, int port )
    {
        this.server = server;
        this.port = port;
    }

    public DimmCommand( int port )
    {
        this("127.0.0.1", port );
    }

    public boolean check_exists()
    {
        if (!connect())
            return false;

        String ret = send_cmd( "?" );

        disconnect();
        
        if (ret != null && ret.length() > 0 && ret.charAt(0) == '0')
            return true;

        return false;
    }

    public boolean connect()
    {
        disconnect();
        try
        {
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setSoTimeout(SHORT_TIMEOUT);

            socket.connect(new InetSocketAddress(server, port), SHORT_TIMEOUT );
            return true;
        }
        catch (Exception iOException)
        {
            LogManager.msg_comm( LogManager.LVL_ERR, "Cannot connect to " + server + ":" + port, iOException);
        }
        return false;
    }
    public void disconnect()
    {
        if (socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException iOException)
            {
            }
        }
    }

    public String send_cmd( String string )
    {
        return send_cmd(string, 0);
    }
    public String send_cmd( String string, int timeout_s )
    {
        try
        {
            if (timeout_s == 0)
            {
                socket.setSoTimeout(SHORT_TIMEOUT);
            }
            else
            {
                socket.setSoTimeout(timeout_s * 1000);
            }

            StringBuffer sb = new StringBuffer();
            String tcmd = string + "\n";
            LogManager.msg_system(LogManager.LVL_VERBOSE, "DCS: " + string);
            socket.getOutputStream().write(tcmd.getBytes("UTF-8"));

            while (true)
            {
                byte[] retval = new byte[10240];
                int rlen = socket.getInputStream().read(retval);

                if (rlen > 0)
                {
                    sb.append(new String(retval, 0, rlen));
                }
                if (is_finished( sb ))
                {
                    LogManager.msg_system(LogManager.LVL_VERBOSE, "DCR: " + sb.toString());
                    return sb.toString();
                }
                if (rlen <= 0)
                {
                    return null;
                }
            }
        }
        catch (Exception iOException)
        {
            LogManager.msg_comm( LogManager.LVL_ERR, "Comm with ApplServer " + server + ":" + port + " failed", iOException);
        }
        return null;
    }

    private boolean is_finished( StringBuffer sb )
    {
        int len = sb.length();
        if (len >= 4)
        {
            String tail = sb.substring(len -4);
            if (tail.equals("|OK|"))
                return true;
        }
        // ALLOW TRAILING CRLF
        if (len >= 6)
        {
            String tail = sb.substring(len -6);
            if (tail.contains("|OK|"))
                return true;
        }
        return false;
    }
}
