/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index.IMAP;

import dimm.home.index.SearchCall;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Notification.Notification;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;

import java.net.Socket;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

/*****
 * SSl
 * C:\Programme\Java\jre6\bin>rm c:\ssl\mskeystore.str

C:\Programme\Java\jre6\bin>keytool -genkey -keyalg "RSA" -keysize 1024 -dname "c
n=MailSecurer" -alias mailsecurer -keypass 123456 -keystore c:\ssl\mskeystore.st
r -storepass 123456 -validity 3650

C:\Programme\Java\jre6\bin>keytool -selfcert -alias mailsecurer -dname "cn=MailS
ecurer" -validity 3650 -keystore c:\ssl\mskeystore.str
Geben Sie das Keystore-Passwort ein:

C:\Programme\Java\jre6\bin>keytool -list -export -file c:\ssl\exp.cert -alias ma
ilsecurer -rfc -keystore c:\ssl\mskeystore.str
Geben Sie das Keystore-Passwort ein:
Zertifikat in Datei <c:\ssl\exp.cert> gespeichert.
 *
 *
 * C:\Programme\Java\jre6\bin>keytool -genkey -keyalg "RSA" -keysize 1024 -dname "c
n=MailSecurer" -alias mailsecurer -keypass 123456 -keystore c:\ssl\mskeystore.st
r -storepass 123456 -validity 3650

C:\Programme\Java\jre6\bin>keytool -selfcert -alias mailsecurer -dname "cn=MailS
ecurer" -validity 3650 -keystore c:\ssl\mskeystore.str
Geben Sie das Keystore-Passwort ein:

C:\Programme\Java\jre6\bin>keytool -list -export -file c:\ssl\exp.cert -alias ma
ilsecurer -rfc -keystore c:\ssl\mskeystore.str
Geben Sie das Keystore-Passwort ein:
Zertifikat in Datei <c:\ssl\exp.cert> gespeichert.

C:\Programme\Java\jre6\bin>keytool -importkeystore -srckeystore c:\ssl\mskeystor
e.str -srcalias mailsecurer -srcstorepass 123456 -srcstoretype jks -destkeystore
c:\ssl\mskey.p12 -deststoretype pkcs12 -deststorepass mailsecurer
Unzulõssige Option:  -destkeystorec:\ssl\mskey.p12
Verwenden Sie den Befehl keytool -help

C:\Programme\Java\jre6\bin>keytool -importkeystore -srckeystore c:\ssl\mskeystor
e.str -srcalias mailsecurer -srcstorepass 123456 -srcstoretype jks -destkeystore
 c:\ssl\mskey.p12 -deststoretype pkcs12 -deststorepass mailsecurer

C:\Programme\Java\jre6\bin>keytool -importkeystore -srckeystore c:\ssl\mskeystor
e.str -srcalias mailsecurer -srcstorepass 123456 -srcstoretype jks -destkeystore
 c:\ssl\mskey.p12 -deststoretype pkcs12 -deststorepass mailsecurer

 * @author mw
 */

class MyKeyManager extends X509ExtendedKeyManager
{

    KeyStore ks;
    public MyKeyManager( KeyStore _ks)
    {
        ks = _ks;
    }

    @Override
    public String[] getClientAliases( String keyType, Principal[] issuers )
    {
        String[] ret = new String[] {"mykey"};
        return ret;
    }

    @Override
    public String chooseClientAlias( String[] keyType, Principal[] issuers, Socket socket )
    {
        String ret = "mailsecurer";
        return ret;
    }

    @Override
    public String[] getServerAliases( String keyType, Principal[] issuers )
    {
        String[] ret = new String[] {"mailsecurer"};
        return ret;
    }

    @Override
    public String chooseServerAlias( String keyType, Principal[] issuers, Socket socket )
    {
        String ret = "mailsecurer";
        return ret;
    }

    @Override
    public X509Certificate[] getCertificateChain( String alias )
    {
        if (ks == null)
            return new X509Certificate[0];

        try
        {
            Certificate[] list = ks.getCertificateChain(alias);
            X509Certificate[] xlist =  new X509Certificate[list.length];
            for (int i = 0; i < list.length; i++)
            {
                xlist[i] = (X509Certificate) list[i];
            }
            return xlist;
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }
        return new X509Certificate[0];


    }

    @Override
    public PrivateKey getPrivateKey( String alias )
    {
        KeyStore.PasswordProtection nullPasswordProt = new KeyStore.PasswordProtection("mailsecurer".toCharArray());
        try
        {
            KeyStore.PrivateKeyEntry thePrivKeyEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(alias, nullPasswordProt);
            return thePrivKeyEntry.getPrivateKey();
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }
        return null;

    }
}

class MailFolderCache
{
    public static final int MAX_CACHED_FOLDERS = 10;

    final ArrayList<MailFolder> browse_mail_folders;
    String user;

    public MailFolderCache(String user )
    {
        browse_mail_folders = new ArrayList<MailFolder>();
        this.user = user;
    }

}
/**
 *
 * @author mw
 */
public class IMAPServer extends WorkerParentChild
{

    ServerSocket sock;
    MandantContext m_ctx;
    String host;
    int port;
    final ArrayList<ImapsInstance> imaps_instance_list;
    ArrayList<MailFolderCache> folder_cache;


    public String getHost()
    {
        return host;
    }

    public MandantContext get_ctx()
    {
        return m_ctx;
    }

    public int getPort()
    {
        return port;
    }
 
/*
 *


 * */
    public void set_search_results( SearchCall sc, String user, String pwd )
    {
        ArrayList<MailFolder> update_list = new ArrayList<MailFolder>();
        synchronized(imaps_instance_list)
        {
            log_debug("Adding " + sc.get_result_cnt() + " results to IMAP account ");
            for (int i = 0; i < imaps_instance_list.size(); i++)
            {
                ImapsInstance mWImapServer = imaps_instance_list.get(i);
                if (mWImapServer.get_konto() == null)
                    continue;

                if (user.compareTo( mWImapServer.get_konto().get_username()) == 0 &&
                    pwd.compareTo( mWImapServer.get_konto().get_pwd()) == 0)
                {
                    try
                    {
                        // WE SET EACH DIFFERENT FOLDER WITH NEW CONTENTS AND SET FLAG FOR EACH CONNECTION
                        // FOLDERS ARE SHRED BETWEEN CONNECTIONS
                        MailFolder folder = mWImapServer.get_selected_folder();
                        if (folder != null && folder.key.equals( MailFolder.QRYTOKEN))
                        {
                            if (!update_list.contains(folder))
                            {
                                folder.add_new_mail_resultlist(m_ctx, sc);
                                update_list.add(folder);
                            }
                            mWImapServer.set_has_searched( true );
                        }
                        // ADD ONLKY ONE RESULT TO EACH FOLDER
                        MailFolder qry_folder = get_cached_folder(user, MailFolder.QRYTOKEN);
                        if (qry_folder != null)
                        {
                            if (!update_list.contains(qry_folder))
                            {
                                qry_folder.add_new_mail_resultlist(m_ctx, sc);
                                update_list.add(qry_folder);
                            }
                            mWImapServer.set_has_searched( true );                            
                        }
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
        update_list.clear();
    }
    
    ServerSocket getServerSocket(int serverPort, String server_ip) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException, URISyntaxException
    {
	SSLContext      sslContext = SSLContext.getInstance("TLS");
        char[] password = "mailsecurer".toCharArray();

        /*
         * Allocate and initialize a KeyStore object.
         */
        KeyStore ks = KeyToolHelper.load_keystore(/*syskeystore*/ false);

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


        sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(), null);

        SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) sslContext.getServerSocketFactory();
        
        SSLServerSocket ssl_sock = (SSLServerSocket) sslserversocketfactory.createServerSocket(serverPort, 5, InetAddress.getByName(server_ip) );
        return ssl_sock;
    }


    public IMAPServer( MandantContext m_ctx, String host, int port, boolean is_ssl ) throws IOException
    {
        this.m_ctx = m_ctx;
        this.host = host;
        this.port = port;
        log_debug(Main.Txt("Opening_socket"));

        if (is_ssl)
        {
            try
            {
                sock = getServerSocket(port, host);
            }
            catch (Exception iOException)
            {
                LogManager.msg_imaps(LogManager.LVL_ERR, "Cannot create SSL IMAP Server", iOException);
            }
        }
        else
        {
            sock = new ServerSocket(port, 0, InetAddress.getByName(host));
        }

        imaps_instance_list = new ArrayList<ImapsInstance>();
        folder_cache = new ArrayList<MailFolderCache>();
    }

    void log_debug( String s )
    {
        LogManager.msg_imaps(LogManager.LVL_DEBUG, s);
    }

    void log_debug( String s, Exception e )
    {
        LogManager.msg_imaps(LogManager.LVL_DEBUG, s, e);
    }
    
    @Override
    public void finish()
    {
        do_finish = true;
        try
        {
            if (sock != null)
                sock.close();
            sock = null;
            
            synchronized (imaps_instance_list)
            {
                for (int i = 0; i < imaps_instance_list.size(); i++)
                {
                    ImapsInstance imapServer = imaps_instance_list.get(i);
                    imapServer.close();
                }
            }
            imaps_instance_list.clear();
            for (int i = 0; i < folder_cache.size(); i++)
            {
                MailFolderCache c = folder_cache.get(i);
                synchronized (c.browse_mail_folders)
                {
                    for (int j = 0; j < c.browse_mail_folders.size(); j++)
                    {
                        MailFolder f = c.browse_mail_folders.get(j);
                        f.close();
                    }
                    c.browse_mail_folders.clear();
                }
            }
            folder_cache.clear();
        }
        catch (IOException ex)
        {
        }
    }

    @Override
    public void run_loop()
    {
        started = true;

        while (!do_finish)
        {
            try
            {
                log_debug(Main.Txt("Accepting_new_connection"));
                Socket cl = sock.accept();
                if (cl instanceof SSLSocket)
                    ((SSLSocket)cl).startHandshake();

                log_debug("IMAP Server connected to <" + cl.getRemoteSocketAddress() + ">");
                boolean trace = false;
                if (LogManager.has_lvl(LogManager.TYP_IMAPS, LogManager.LVL_VERBOSE ))
                    trace = true;

                ImapsInstance mwimap = new ImapsInstance(this, m_ctx, cl, trace);
                synchronized(imaps_instance_list)
                {
                    imaps_instance_list.add(mwimap);
                }
                mwimap.start();

                // WAIT FOR NEXT ACCEPT TO PREVENT DENIAL OF SERVICE
                sleep_seconds(1);

            }
            catch (Exception iOException)
            {
                if (!do_finish)
                {
                    
                    // WAIT FOR NEXT ACCEPT TO PREVENT DENIAL OF SERVICE

                    if (iOException instanceof KeyStoreException ||
                        iOException instanceof CertificateException ||
                        iOException instanceof UnrecoverableKeyException ||
                        iOException instanceof KeyManagementException)
                    {
                        LogManager.msg_imaps(LogManager.LVL_ERR, Main.Txt("SSL_certificate_is_missing_or_invalid") + ": " + iOException.getMessage());
                        Notification.throw_notification_one_shot(m_ctx.getMandant(), Notification.NF_ERROR, Main.Txt("SSL_certificate_is_missing_or_invalid"));
                        sleep_seconds( 30 );
                    }
                    else
                    {
                        LogManager.msg_imaps(LogManager.LVL_ERR, Main.Txt("IMAP_connection_broken") + ": " + iOException.getMessage());
                        sleep_seconds(1);
                    }
                }
            }
        }

        finished = true;
    }

    @Override
    public void idle_check()
    {
        synchronized (imaps_instance_list)
        {
            for (int i = 0; i < imaps_instance_list.size(); i++)
            {
                ImapsInstance sr = imaps_instance_list.get(i);
                if (!sr.isAlive())
                {
                    imaps_instance_list.remove(i);
                    sr.close();
                    i = -1;
                    continue;
                }
            }
        }
    }

    public int getInstanceCnt()
    {
        int r = 0;
        synchronized (imaps_instance_list)
        {
            r = imaps_instance_list.size();
        }
        return r;
    }
    public int getUserInstanceCnt(String user)
    {
        int r = 0;
        synchronized (imaps_instance_list)
        {
            for (int i = 0; i < imaps_instance_list.size(); i++)
            {
                ImapsInstance sr = imaps_instance_list.get(i);
                if (sr.get_konto().get_username().compareTo(user) == 0)
                    r++;
            }
        }
        return r;
    }

   
    @Override
    public Object get_db_object()
    {
        return m_ctx.getMandant();
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

   

    @Override
    public String get_name()
    {
        return "IMAPBrowser";
    }
    void clear_cache( String user)
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            synchronized (entry.browse_mail_folders)
            {
                for (int j = 0; j < entry.browse_mail_folders.size(); j++)
                {
                    MailFolder folder = entry.browse_mail_folders.get(j);
                    folder.close();
                }
                entry.browse_mail_folders.clear();
            }
        }
    }

    MailFolder get_cached_folder( String user, String key)
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            synchronized (entry.browse_mail_folders)
            {
                for (int j = 0; j < entry.browse_mail_folders.size(); j++)
                {
                    MailFolder folder = entry.browse_mail_folders.get(j);
                    if (folder.key.equals(key))
                    {
                        folder.set_last_time_used(System.currentTimeMillis());
                        return folder;
                    }
                }
            }
        }
        return null;
    }

    void add_to_folder_cache( MailFolder folder, String user )
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            synchronized (entry.browse_mail_folders)
            {
                if (entry.browse_mail_folders.size() > MailFolderCache.MAX_CACHED_FOLDERS)
                {
                    remove_oldest_folder( entry.browse_mail_folders );
                }
                
                entry.browse_mail_folders.add( folder);
            }
            return;
        }
        MailFolderCache entry = new MailFolderCache( user);
        entry.browse_mail_folders.add(folder);
        folder_cache.add(entry);
    }

    void update_to_folder_cache( MailFolder new_folder, String user )
    {
        for (int i = 0; i < folder_cache.size(); i++)
        {
            MailFolderCache entry = folder_cache.get(i);
            if (!entry.user.equals(user))
                continue;

            synchronized (entry.browse_mail_folders)
            {
                // REPLACE
                for (int j = 0; j < entry.browse_mail_folders.size(); j++)
                {
                    MailFolder folder = entry.browse_mail_folders.get(j);
                    if (folder.key.equals(new_folder.key))
                    {
                        if (folder != new_folder)
                        {
                            entry.browse_mail_folders.remove(folder);
                            entry.browse_mail_folders.add(new_folder);
                            new_folder.set_last_time_used(System.currentTimeMillis());
                            folder.close();
                        }
                        return;
                    }
                }
                if (entry.browse_mail_folders.size() > MailFolderCache.MAX_CACHED_FOLDERS)
                {
                    remove_oldest_folder( entry.browse_mail_folders );
                }
                entry.browse_mail_folders.add( new_folder);
            }
            return;
        }
        MailFolderCache entry = new MailFolderCache( user);
        entry.browse_mail_folders.add(new_folder);
        folder_cache.add(entry);
    }

    private void remove_oldest_folder( ArrayList<MailFolder> browse_mail_folders )
    {
        MailFolder oldest = null;

        synchronized (browse_mail_folders)
        {
            for (int j = 0; j < browse_mail_folders.size(); j++)
            {
                MailFolder folder = browse_mail_folders.get(j);
                if (oldest == null)
                    oldest = folder;
                else
                {
                    if (folder.get_last_time_used() < oldest.get_last_time_used())
                    {
                        oldest = folder;
                    }
                }
            }
            if (oldest != null)
            {
                browse_mail_folders.remove(oldest);
                oldest.close();
            }
        }
    }

    

}

