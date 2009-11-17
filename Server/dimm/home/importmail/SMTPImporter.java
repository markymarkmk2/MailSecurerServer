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
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.hibernate.SmtpServer;
import home.shared.mail.RFCFileMail;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.PlainAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.UsernamePasswordValidator;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListenerAdapter;
import org.subethamail.smtp.server.SMTPServer;


class SMTP_Import_Uservalidator  implements UsernamePasswordValidator
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
            throw new LoginFailedException( Main.Txt("Wrong_username"));

        if (password.compareTo(importer.get_SMTP_password()) != 0)
            throw new LoginFailedException( Main.Txt("Wrong_password"));

        return;  // OKAY!
    }
}



class SmtpImporterServer extends SMTPServer
{

    public SmtpImporterServer( SimpleMessageListenerAdapter sml_adapter, PlainAuthenticationHandlerFactory pah_factory)
    {
        super(sml_adapter, pah_factory);
    }
    /** */
    @Override
    public String getName()
    {
            return "SMTPServer for " + Main.APPNAME + " " + Main.VERSION;
    }
}


public class SMTPImporter implements StatusHandler, WorkerParentChild, SimpleMessageListener
{	
	private SmtpImporterServer server;
        String status_txt;
        boolean do_shutdown;

        public static final int MAX_SMTP_BACKLOG = 30000;
	public static final int MAX_SMTP_CONNECTIONS = 1024*1024;

        SmtpServer smtp_db_entry;
        private boolean started;
        private boolean finished;

 	public SMTPImporter(SmtpServer smtp_server)
        {
            this.smtp_db_entry = smtp_server;
	}

	

	 boolean  startup()
         {
             // WE ARE LISTENER
             ArrayList<SimpleMessageListener> listeners = new ArrayList<SimpleMessageListener>();
             listeners.add(this);

             SimpleMessageListenerAdapter sml_adapter = new SimpleMessageListenerAdapter( listeners );

             // WE VALIDATE THROUGH OUR OWN USERNAME / PWD
             SMTP_Import_Uservalidator up_validator = new SMTP_Import_Uservalidator(this);

             PlainAuthenticationHandlerFactory pah_factory = new PlainAuthenticationHandlerFactory( up_validator );

             server = new SmtpImporterServer(sml_adapter, pah_factory);

             if (smtp_db_entry.getServer() != null && smtp_db_entry.getServer().length() > 0)
             {
		InetAddress bindAddress;
                try
                {
                        bindAddress = InetAddress.getByName(smtp_db_entry.getServer());
                        server.setBindAddress(bindAddress);
                } catch (Exception uhe)
                {
                        LogManager.log(Level.SEVERE, Main.Txt("Cannot_bind_smtp_server_to_address") + " " + smtp_db_entry.getServer(), uhe);
                }
            }
            server.setPort(smtp_db_entry.getPort());
            server.setBacklog(MAX_SMTP_BACKLOG);
            server.setMaxConnections(MAX_SMTP_CONNECTIONS);


             try
             {
                 server.start();
             }
             catch (Exception e)
             {
                  LogManager.log(Level.SEVERE, Main.Txt("Cannot_start_smtp_server_on_port") + " " + smtp_db_entry.getPort(), e);
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
    public boolean accept(String from, String recipient)
    {
        // TODO: FILTER MESSAGES
        return true;
    }

    @Override
    public void deliver(String from, String recipient, InputStream data) throws TooMuchDataException, IOException
    {
        archive_message( data );
    }
	


    protected void archive_message( InputStream data )
    {
        RFCFileMail mail = null;
        try
        {
            status.set_status(StatusEntry.BUSY, "Archiving message from Mail server <" + smtp_db_entry.getServer() + ">");

            mail = Main.get_control().create_import_filemail_from_eml_stream(smtp_db_entry.getMandant(), data, "smtpimp", smtp_db_entry.getDiskArchive());

            Main.get_control().add_rfc_file_mail(mail, smtp_db_entry.getMandant(), smtp_db_entry.getDiskArchive(), /*bg*/ true, /*del_after*/ true);

        }
        catch (VaultException ex)
        {
            Logger.getLogger(SMTPImporter.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IndexException ex)
        {
            Logger.getLogger(SMTPImporter.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (ArchiveMsgException ex)
        {
            status.set_status(StatusEntry.ERROR, "Cannot archive message from <" + smtp_db_entry.getServer() + ">");
            LogManager.err_log(status.get_status_txt(), ex);
            if (mail != null)
            {
                try
                {
                    Main.get_control().move_to_quarantine(mail, smtp_db_entry.getMandant());
                }
                catch (IOException iOException)
                {
                    LogManager.err_log("Cannot move mail to quarantine", iOException);
                }
            }
        }
    }

    @Override
    public String get_status_txt()
    {
        return status.get_status_txt();
    }

    @Override
    public int get_status_code()
    {
        return status.get_status_code();
    }


    @Override
    public void idle_check()
    {
    }


    @Override
    public void finish()
    {
        do_shutdown = true;
        server.stop();
    }

    @Override
    public void run_loop()
    {
        started = true;
        while (!startup())
        {
            LogicControl.sleep(60*1000);
        }

        while(!do_shutdown)
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
    public boolean is_finished()
    {
        return finished;
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

}
