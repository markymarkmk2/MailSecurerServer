/*
 * StatusDisplay.java
 *
 * Created on 9. Oktober 2007, 21:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxy;

import dimm.home.mailproxy.Utilities.CmdExecutor;
import dimm.home.mailproxy.Utilities.SwingWorker;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;




/**
 *
 * @author Administrator
 */
public class MailArchiver extends WorkerParent
{
    public static final String NAME = "MailArchiver";
    public static final String MA_SUFFIX = ".eml";
    public static final String ERR_SUFFIX = ".err";
    
    ArrayList<File> file_list;
    ArrayList<File> eml_file_list;
    
        
    
    /** Creates a new instance of StatusDisplay */
    public MailArchiver()
    {        
        super(NAME);
        
    }
    
    public boolean initialize()
    {
        file_list = new ArrayList<File>();
        eml_file_list = new ArrayList<File>();
        
        try
        {
            
            File[] dir = new File(Main.RFC_PATH).listFiles();
            if (dir != null && dir.length > 0)
            {
                for (int f = 0; f < dir.length; f++)
                {
                    file_list.add(dir[f]);
                }
            }            
        }
        
        catch (Exception exc)
        {
            Main.err_log_fatal("Cannot list existing rfc dumps: " + exc.getMessage());
            this.setGoodState(false);
            
            return false;
        }
        
        return true;
    }

    private boolean handle_archive_ex2mailarchiver()
    {
        String java_agent_opts = Main.get_prop(Preferences.MAIL_ARCHIVA_AGENT_OPTS, "");
        
        try
        {
            String cmd[] = {"ex2mailarchiva.sh", java_agent_opts, "-s", Main.RFC_PATH};
          
            CmdExecutor exe = new CmdExecutor(cmd);
            int ret = exe.exec();

            if (ret != 0)
            {
                this.setGoodState(false);
                this.setStatusTxt(exe.get_err_text());
                Main.err_log_fatal(" Archiving failed: " + exe.get_out_text() + " / " + exe.get_err_text() );
            }
            else
            {
                String result = exe.get_out_text();
                Main.debug_msg(1, result);
                
                for (int i = 0; i < this.eml_file_list.size(); i++)
                {
                    String search_txt = "sent " + eml_file_list.get(i).getName() + " OK";
                    if (result.indexOf(search_txt) < 0)
                    {
                        Main.err_log("Archival of message <" + eml_file_list.get(i).getName() + "> was not detected, setting to error" );
                        String name = eml_file_list.get(i).getName();
                        int suffix_idx = name.lastIndexOf(MA_SUFFIX);
                        String new_name = name.substring(0, suffix_idx) + ERR_SUFFIX;
                        eml_file_list.get(i).renameTo(new File( new_name ));                        
                        ret = 2;
                    }                                            
                }
                
            }
            return (ret == 0);
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            Main.err_log_fatal("Cannot archive rfc dump files: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");
        }
        
        return false;
    }

    private boolean handle_archive_push_agent(File rfc_dump)
    {        
        String java_agent_opts = Main.get_prop(Preferences.MAIL_ARCHIVA_AGENT_OPTS, "-Xmx256m");
        
        try
        {
            String cmd[] = {"java", java_agent_opts, "-jar", "./PushAgent.jar", Main.get_prop(Preferences.MAIL_ARCHIVA_URL, "http://localhost/mailarchiva"), rfc_dump.getAbsolutePath()};
          
            CmdExecutor exe = new CmdExecutor(cmd);
            int ret = exe.exec();

            return (ret == 0);
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            Main.err_log_fatal("Cannot archive rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");
        }
        
        return false;
    }
 /*   private boolean handle_archive_smtp(File rfc_dump)
    {        
    
  DataOutputStream os  = null;
  BufferedReader   is  = null;
  String           sRt = "";


  public synchronized final String sendEmail( String sSmtpServer,
                                              String sFromAdr, String sFromRealName,
                                              String sToAdr,   String sToRealName,
                                              String sSubject, String sText )
  throws IOException, Exception
  {
    Socket so = null;
    try {
      sRt = "";
      if( null == sSmtpServer  || 0 >= sSmtpServer.length() ||
          null == sFromAdr     || 0 >= sFromAdr.length()    ||
          null == sToAdr       || 0 >= sToAdr.length()      ||
          (  (null == sSubject || 0 >= sSubject.length())
          && (null == sText    || 0 >= sText.length())  )   )
        throw new Exception( "Invalid Parameters for SmtpSimple.sendEmail()." );
      if( null == sFromRealName || 0 >= sFromRealName.length() )  sFromRealName = sFromAdr;
      if( null == sToRealName   || 0 >= sToRealName.length() )    sToRealName   = sToAdr;
      so = new Socket( sSmtpServer, 25 );
      os = new DataOutputStream( so.getOutputStream() );
      is = new BufferedReader(
           new InputStreamReader( so.getInputStream() ) );
      so.setSoTimeout( 10000 );
      writeRead( true, "220", null );
      writeRead( true, "250", "HELO " + sSmtpServer + "\n" );
      writeRead( true, "250", "RSET\n" );
      writeRead( true, "250", "MAIL FROM:<" + sFromAdr + ">\n" );
      writeRead( true, "250", "RCPT TO:<" + sToAdr + ">\n" );
      writeRead( true, "354", "DATA\n" );
      writeRead( false, null, "To: " + sToRealName + " <" + sToAdr + ">\n" );
      writeRead( false, null, "From: " + sFromRealName + " <" + sFromAdr + ">\n" );
      writeRead( false, null, "Subject: " + sSubject + "\n" );
      writeRead( false, null, "Mime-Version: 1.0\n" );
      writeRead( false, null, "Content-Type: text/plain; charset=\"iso-8859-1\"\n" );
      writeRead( false, null, "Content-Transfer-Encoding: quoted-printable\n\n" );
      writeRead( false, null, sText + "\n" );
      writeRead( true, "250", ".\n" );
      writeRead( true, "221", "QUIT\n" );
      return sRt;
    } finally {
      if( is != null ) try { is.close(); } catch (Exception ex) {}
      if( os != null ) try { os.close(); } catch (Exception ex) {}
      if( so != null ) try { so.close(); } catch (Exception ex) {}
      is = null;
      os = null;
    }
  }

  private final void writeRead( boolean bReadAnswer,
                                String  sAnswerMustStartWith,
                                String  sWrite )
  throws IOException, Exception
  {
    if( null != sWrite && 0 < sWrite.length() )
    {
      sRt += sWrite;
      os.writeBytes( sWrite );
    }
    if( bReadAnswer )
    {
      String sRd = is.readLine() + "\n";
      sRt += sRd;
      if( null != sAnswerMustStartWith
          && 0 < sAnswerMustStartWith.length()
          && !sRd.startsWith( sAnswerMustStartWith ) )
        throw new Exception( sRt );
    }
  }
}
    */
    
    
    
    private void run_loop_single_file()
    {
        while (!isShutdown())
        {
            Main.sleep(1000);
            File rfc_dump = null;
            
            synchronized (file_list)            
            {
                if (file_list.isEmpty())
                    continue;
            
            
                rfc_dump = file_list.get(0);
                if (!rfc_dump.exists())
                {
                    Main.err_log_fatal("rfc dump <" + rfc_dump.getName() + " disappeared!");
                    this.setGoodState(false);
                    file_list.remove(0);
                    continue;
                }
            }
            
            this.setStatusTxt("Archiving <" + rfc_dump.getName()  + ">...");
            Main.debug_msg(0, "Archiving rfc dump " + rfc_dump.getName());
            
            if ( handle_archive_push_agent( rfc_dump ))
            {
                synchronized (file_list)
                {
                    file_list.remove(rfc_dump);
                }
                
                rfc_dump.delete();
                
                this.setGoodState(true);
                this.setStatusTxt("");
                
            }
            else
            {
                Main.err_log("Archiving of <" +  rfc_dump.getName() + "> failed" );
                
                // ON ERROR GET SLOWER
                Main.sleep(60*1000);
                // ON ERROR REQUE MAIL, MAYBE THIS ONE IS THE ONLY ONE THAT SUCKS
                synchronized (file_list)
                {
                    file_list.remove(rfc_dump);
                    file_list.add(rfc_dump);
                }                    
            }                       
        }                    
    }
    private void run_loop_bulk()
    {
        while (!isShutdown())
        {
            Main.sleep(10000);
            File rfc_dump = null;
            eml_file_list.clear();
            
            synchronized (file_list)            
            {
                if (file_list.isEmpty())
                    continue;

                
                try
                {
                    
                    for (int i = 0; i < file_list.size(); i++)
                    {            
                        rfc_dump = file_list.get(i);
                        if (!rfc_dump.exists())
                        {
                            Main.err_log_fatal("rfc dump <" + rfc_dump.getName() + " disappeared!");
                            this.setGoodState(false);
                            file_list.remove(i);
                            i--;
                            continue;
                        }
                        String name = rfc_dump.getAbsolutePath();
                        int suffix_idx = name.lastIndexOf(".txt");
                        int converted_suffix_idx = name.lastIndexOf(MA_SUFFIX);
                        int err_suffix_idx = name.lastIndexOf(ERR_SUFFIX);
                        if (suffix_idx <= 0 && converted_suffix_idx <= 0 && err_suffix_idx <= 0)
                        {
                            Main.err_log_warn("rfc dump <" + rfc_dump.getName() + " has wrong suffix, ignoring!");
                            continue;
                        }
                        if (err_suffix_idx > 0)
                        {
                            // SKIP ERRORS
                            continue;
                        }
                        if (converted_suffix_idx > 0)
                        {
                            // ALREADY CONVERTED, SKIP
                            eml_file_list.add(rfc_dump);
                            file_list.remove(rfc_dump);
                            i--;
                            continue;
                        }
                        String new_name = name.substring(0, suffix_idx) + MA_SUFFIX;
                        rfc_dump.renameTo(new File( new_name ));
                        eml_file_list.add( new File( new_name ));
                        file_list.remove(rfc_dump);
                        i--;                        
                    }
                }
                
                catch (Exception exc)
                {
                    Main.err_log_fatal("Failed to convert files in rfc_directory: " + exc.getMessage());
                    Main.sleep(60);
                    continue;
                }                                    
            }
            
            
            // OK, ALL FILES ARE NOW .eml

            this.setStatusTxt("Archiving " + file_list.size() + " file(s) from rfc directory...");
            this.setGoodState(true);
            Main.debug_msg(0, this.getStatusTxt());
            
            
            if ( handle_archive_ex2mailarchiver( ))
            {
                synchronized (file_list)
                {
                    for (int i = 0; i < eml_file_list.size(); i++)
                    {                                
                        eml_file_list.get(i).delete();
                    }
                    
                    eml_file_list.clear();
                }
                this.setGoodState(true);
                this.setStatusTxt("");
                
            }
            else
            {
                this.setGoodState(false);
                this.setStatusTxt("Archiving failed");
                Main.err_log( this.getStatusTxt() );

                // REJOIN LIST ON ERROR
                synchronized (file_list)
                {
                    file_list.addAll(eml_file_list);
                    //file_list.clear();
                    eml_file_list.clear();
                }
                
                
                // ON ERROR GET SLOWER
                Main.sleep(60*1000);
                
             }                       
        }                    
    }

    
    public boolean start_run_loop()
    {       
        Main.debug_msg(1, "Starting mail archiver task" );
        
         
        SwingWorker worker = new SwingWorker()
        {
            public Object construct()
            {
                run_loop_bulk();

                return null;
            }
        };
        
        worker.start();
        return true;
         
      
    }
        
    
 
    public boolean check_requirements(StringBuffer sb)
    {
        long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
        if (space_left_mb < 100)
        {
            Main.err_log_fatal("Not enough space left, free space is " + space_left_mb + "MB");
            return false;
        }
        
        return true;
    }

    void add_rfc_file(File rfc_dump)
    {
        synchronized (file_list)
        {
            file_list.add(rfc_dump);
        }
    }
    
    public BufferedOutputStream get_rfc_stream( File rfc_dump)
    {
    
        BufferedOutputStream bos = null;
        try
        {
            if (rfc_dump.exists())
            {
                Main.err_log_warn("Removing existing rfc_dump file " + rfc_dump.getName());
                rfc_dump.delete();
            }
            
            FileOutputStream fos = new FileOutputStream(rfc_dump);
            bos = new BufferedOutputStream(fos);

            rfc_dump.getFreeSpace();
        }
        catch (Exception exc)
        {
            long space_left_mb = (long) (new File(Main.RFC_PATH).getFreeSpace() / (1024.0 * 1024.0));
            Main.err_log_fatal("Cannot open rfc dump file: " + exc.getMessage() + ", free space is " + space_left_mb + "MB");

            try
            {
                if (bos != null)
                {
                    bos.close();
                }
                if (rfc_dump != null && rfc_dump.exists())
                {
                    rfc_dump.delete();
                }
            }
            catch (Exception exce)
            {
            }
            bos = null;

        }    
        return bos;
    }
  

}
