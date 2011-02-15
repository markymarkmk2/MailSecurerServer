/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.Utilities.KeyToolHelper;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.CS_Constants;
import home.shared.Utilities.DefaultSSLServerSocketFactory;
import home.shared.Utilities.DefaultSSLSocketFactory;
import home.shared.hibernate.SmtpServer;
import home.shared.mail.RFCFileMail;
import java.io.*;
import java.util.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.PlainAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.UsernamePasswordValidator;
import org.subethamail.smtp.command.StartTLSCommand;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListenerAdapter;
import org.subethamail.smtp.server.SMTPServer;

class SMTP_Import_Uservalidator implements UsernamePasswordValidator
{

    SMTPImporter importer;

    public SMTP_Import_Uservalidator( SMTPImporter importer )
    {
        this.importer = importer;
    }

    @Override
    public void login( String username, String password ) throws LoginFailedException
    {
        if (username.compareTo(importer.get_SMTP_username()) != 0)
        {
            throw new LoginFailedException(Main.Txt("Wrong_username"));
        }

        if (password.compareTo(importer.get_SMTP_password()) != 0)
        {
            throw new LoginFailedException(Main.Txt("Wrong_password"));
        }

        return;  // OKAY!
    }
}

class SmtpImporterServer extends SMTPServer
{

    boolean ssl;
    boolean tls;
    

    public SmtpImporterServer( SimpleMessageListenerAdapter sml_adapter, PlainAuthenticationHandlerFactory pah_factory, boolean _ssl, boolean _tls)
    {
        super(sml_adapter, pah_factory);
        ssl = _ssl;
        tls = _tls;
    }

    /** */
    @Override
    public String getName()
    {
        return "SMTPServer for " + Main.APPNAME + " " + Main.get_version_str();
    }

    @Override
    protected ServerSocket createServerSocket() throws IOException
    {
        if (!tls)
        {
            // NO TLS
            setHideTLS(true);
        }
        
        if (!ssl)
        {
            return super.createServerSocket();
        }
        else
        {
            try
            {
                return getServerSocket(this.getPort(), this.getBindAddress());
            }
            catch (Exception exception)
            {
                throw new IOException(exception.getLocalizedMessage());
            }
        }
    }

    ServerSocket getServerSocket( int serverPort, InetAddress adress ) throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableKeyException, KeyManagementException, URISyntaxException
    {
        SSLContext sslContext;

        // DOES THIS DIFFER?
      /*  if (tls)
            sslContext = SSLContext.getInstance("TLS");
        else*/
            sslContext = SSLContext.getInstance("SSL");
        
        char[] password = "mailsecurer".toCharArray();

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

        SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) sslContext.getServerSocketFactory();


        SSLServerSocket ssl_server_socket;
        if (adress != null)
        {
            ssl_server_socket = (SSLServerSocket) sslserversocketfactory.createServerSocket(serverPort, 5, adress);
        }
        else
        {
            ssl_server_socket = (SSLServerSocket) sslserversocketfactory.createServerSocket(serverPort, 5);
        }
        ssl_server_socket.setEnabledCipherSuites(ssl_server_socket.getSupportedCipherSuites());

        return ssl_server_socket;
    }
}

public class SMTPImporter extends WorkerParentChild implements SimpleMessageListener
{

    private SmtpImporterServer server;
    String status_txt;
    public static final int MAX_SMTP_BACKLOG = 3000;
    public static final int MAX_SMTP_CONNECTIONS = 1024 * 1024;
    SmtpServer smtp_db_entry;

    public SMTPImporter( SmtpServer smtp_server )
    {
        this.smtp_db_entry = smtp_server;
    }

    boolean has_ssl()
    {
        try
        {
            return test_flag(CS_Constants.SL_SSL);
        }
        catch (Exception e)
        {
        }
        return false;
    }
    boolean has_tls()
    {
        try
        {
            return test_flag(CS_Constants.SL_USE_TLS_FORCE) | test_flag(CS_Constants.SL_USE_TLS_IF_AVAIL);
        }
        catch (Exception e)
        {
        }
        return false;
    }

    boolean is_disabled()
    {
        return test_flag(CS_Constants.SL_DISABLED);
    }
    boolean needs_auth()
    {
        return !test_flag(CS_Constants.SL_NO_SMTP_AUTH);
    }

    boolean test_flag( int f )
    {
        int fl = 0;
        if (smtp_db_entry.getFlags() != null && smtp_db_entry.getFlags().length() > 0)
            fl = Integer.parseInt(smtp_db_entry.getFlags());
        return (fl & f) == f;
    }

    @Override
    public int get_mandant_id()
    {
        return smtp_db_entry.getMandant().getId();
    }

    boolean startup()
    {
        // WE ARE LISTENER
        try
        {
            ArrayList<SimpleMessageListener> listeners = new ArrayList<SimpleMessageListener>();
            listeners.add(this);

            SimpleMessageListenerAdapter sml_adapter = new SimpleMessageListenerAdapter(listeners);

            

            PlainAuthenticationHandlerFactory pah_factory = null;
            
            if (needs_auth())
            {
                // WE VALIDATE THROUGH OUR OWN USERNAME / PWD
                SMTP_Import_Uservalidator up_validator = new SMTP_Import_Uservalidator(this);
                pah_factory = new PlainAuthenticationHandlerFactory(up_validator);
            }

            server = new SmtpImporterServer(sml_adapter, pah_factory, has_ssl(), has_tls());

            if (smtp_db_entry.getServer() != null && smtp_db_entry.getServer().length() > 0)
            {
                InetAddress bindAddress;
                try
                {
                    bindAddress = InetAddress.getByName(smtp_db_entry.getServer());
                    server.setBindAddress(bindAddress);
                }
                catch (Exception uhe)
                {
                    LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, Main.Txt("Cannot_bind_smtp_server_to_address") + " " + smtp_db_entry.getServer(), uhe);
                }
            }
            server.setPort(smtp_db_entry.getPort());
            server.setBacklog(MAX_SMTP_BACKLOG);
            server.setMaxConnections(MAX_SMTP_CONNECTIONS);


            LogManager.msg(LogManager.LVL_INFO, LogManager.TYP_IMPORT, "SMTP-Listener is running on 'smtp://" + smtp_db_entry.getServer()
                    + ":" + smtp_db_entry.getPort() + "'");

            server.start();        
        }
        catch (Exception e)
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, Main.Txt("Cannot_start_smtp_server_on_port") + " " + smtp_db_entry.getPort(), e);
            return false;
        }

        return true;
    }

    public String get_SMTP_username()
    {
        return smtp_db_entry.getUsername();
    }

    public String get_SMTP_password()
    {
        return smtp_db_entry.getPassword();
    }

    @Override
    public boolean accept( String from, String recipient )
    {
        // TODO: FILTER MESSAGES
        return true;
    }

    @Override
    public void deliver( String from, String recipient, InputStream data ) throws TooMuchDataException, IOException
    {
        archive_message(data);
    }

    protected void archive_message( InputStream data )
    {
        RFCFileMail mail = null;
        try
        {
            status.set_status(StatusEntry.BUSY, "Archiving message from Mail server <" + smtp_db_entry.getServer() + ">");

            mail = Main.get_control().create_import_filemail_from_eml_stream(smtp_db_entry.getMandant(), data, "smtpimp", smtp_db_entry.getDiskArchive());

            Main.get_control().add_rfc_file_mail(mail, smtp_db_entry.getMandant(), smtp_db_entry.getDiskArchive(), /*bg*/ true, /*del_after*/ true);

            return; /// OK

        }
        catch (VaultException ex)
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Vault Exception in archive_message", ex);
        }
        catch (IndexException ex)
        {
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Index Exception in archive_message", ex);
        }
        catch (ArchiveMsgException ex)
        {
            status.set_status(StatusEntry.ERROR, "Cannot archive message from <" + smtp_db_entry.getServer() + ">");
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, status.get_status_txt(), ex);
        }

        // ONLY ON ERROR
        if (mail != null)
        {
            try
            {
                Main.get_control().move_to_quarantine(mail, smtp_db_entry.getMandant());
            }
            catch (IOException iOException)
            {
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT, "Cannot move mail to quarantine", iOException);
            }
        }
    }

    @Override
    public void idle_check()
    {
    }

    @Override
    public void finish()
    {
        server.stop();
        do_finish = true;
    }

    @Override
    public void run_loop()
    {
        startup();
        started = true;

        while (!do_finish)
        {
            // YAWN....
            LogicControl.sleep(1000);
        }
        finished = true;
    }

    @Override
    public boolean is_started()
    {
        return started;
    }

    @Override
    public Object get_db_object()
    {
        return smtp_db_entry;
    }

    @Override
    public String get_task_status_txt()
    {
        return "";
    }

    @Override
    public String get_name()
    {
        return "SMTPImporter";
    }
}
