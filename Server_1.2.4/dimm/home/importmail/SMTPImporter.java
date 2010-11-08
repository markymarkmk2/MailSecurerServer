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
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.WorkerParentChild;
import home.shared.hibernate.SmtpServer;
import home.shared.mail.RFCFileMail;
import java.io.*;
import java.util.*;
import java.net.*;
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
            return "SMTPServer for " + Main.APPNAME + " " + Main.get_version_str();
    }
}


public class SMTPImporter  extends WorkerParentChild implements SimpleMessageListener
{	
	private SmtpImporterServer server;
        String status_txt;
  
        public static final int MAX_SMTP_BACKLOG = 30000;
	public static final int MAX_SMTP_CONNECTIONS = 1024*1024;

        SmtpServer smtp_db_entry;
  	public SMTPImporter(SmtpServer smtp_server)
        {
            this.smtp_db_entry = smtp_server;
	}

    @Override
    public int get_mandant_id()
    {
        return smtp_db_entry.getMandant().getId();
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
                        LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,  Main.Txt("Cannot_bind_smtp_server_to_address") + " " + smtp_db_entry.getServer(), uhe);
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
            LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,status.get_status_txt(), ex);
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
                LogManager.msg(LogManager.LVL_ERR, LogManager.TYP_IMPORT,"Cannot move mail to quarantine", iOException);
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
        do_finish = true;
        server.stop();
    }

    @Override
    public void run_loop()
    {
        started = true;
        // WAIT A MINUTE BEFORE STARTING
        sleep_seconds(60);

        while(!do_finish)
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
