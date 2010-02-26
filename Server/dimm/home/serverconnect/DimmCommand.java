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
        try
        {
            socket = new Socket(server, port);
            socket.setReuseAddress(true);
            socket.setSoTimeout(SHORT_TIMEOUT);
            socket.connect(new InetSocketAddress(server, port), SHORT_TIMEOUT );
            return true;
        }
        catch (Exception iOException)
        {
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
                    return sb.toString();
                }
            }
        }
        catch (Exception iOException)
        {
            LogManager.err_log("Comm with ApplServer " + server + ":" + port + " failed", iOException);
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
