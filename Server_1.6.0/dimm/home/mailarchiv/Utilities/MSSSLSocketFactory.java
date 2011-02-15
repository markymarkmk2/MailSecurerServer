/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import home.shared.Utilities.DefaultSSLSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Administrator
 */
public class MSSSLSocketFactory extends DefaultSSLSocketFactory
{

    @Override
    public Socket createSocket( Socket socket, String host, int port, boolean auto_close ) throws IOException
    {
        Socket s = null;
        char[] password = "mailsecurer".toCharArray();
        SSLContext sslContext;

        try
        {
            sslContext = SSLContext.getInstance("SSL");


            /*
             * Allocate and initialize a KeyStore object.
             */
            KeyStore ks = KeyToolHelper.load_keystore(/*syskeystore*/false);

            /*
             * Allocate and initialize a KeyManagerFactory.
             */
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);
            /*
             * Allocate and initialize a TrustManagerFactory.
             */
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);


            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            SSLSocketFactory sslserversocketfactory = (SSLSocketFactory) sslContext.getSocketFactory();

            s = sslserversocketfactory.createSocket(socket, host, port, auto_close);

            if (s instanceof SSLSocket)
            {
                SSLSocket ssl_server_socket = (SSLSocket) s;

                ssl_server_socket.setEnabledCipherSuites(ssl_server_socket.getSupportedCipherSuites());
            }
        }
        catch (NoSuchAlgorithmException noSuchAlgorithmException)
        {
            throw new IOException(noSuchAlgorithmException);
        }
        catch (KeyStoreException keyStoreException)
        {
            throw new IOException(keyStoreException);
        }
        catch (UnrecoverableKeyException unrecoverableKeyException)
        {
            throw new IOException(unrecoverableKeyException);
        }
        catch (KeyManagementException keyManagementException)
        {
            throw new IOException(keyManagementException);
        }



        return s;
    }


}
