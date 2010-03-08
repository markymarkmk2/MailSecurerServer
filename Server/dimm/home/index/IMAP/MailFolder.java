
package dimm.home.index.IMAP;
import dimm.home.index.SearchCall;
import dimm.home.index.SearchResult;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.vault.DiskSpaceHandler;
import home.shared.CS_Constants;
import home.shared.filter.ExprEntry;
import home.shared.filter.GroupEntry;
import home.shared.mail.RFCFileMail;
import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCMimeMail;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailFolder
{
    //String file;
    MailKonto konto;
    String key = null;
    ArrayList<MWMailMessage> uid_map_list;
    ArrayList<MWMailMessage> last_uid_map;
    String uid_validity;
    int year;
    int month;
    int day;

    int cnt_level( String path )
    {

        if (path.length() == 0 || path.compareTo(".") == 0)
            return 0;

        int lvl = 1;
        for (int i = 0; i < path.length(); i++)
        {
            char ch = path.charAt(i);
            if (ch == '/' || ch == '\\')
                lvl++;
        }
        return lvl;
    }

    public static final String QRYTOKEN = "Suchen";
    public static final String BROWSETOKEN = "Browsen";
    public static final String TESTTOKEN = "TEST";

    //public static int uid = 42;
    public MailFolder(MailKonto konto, /*String file,*/String key)
    {
        this.konto = konto;        
       // this.file = file;
        this.key = key;
        uid_map_list = new ArrayList<MWMailMessage>();

        uid_validity = Long.toString(System.currentTimeMillis() / 1000);


        if (key.startsWith(QRYTOKEN))
        {
  /*          RFCMimeMail mm = new RFCMimeMail();
            try
            {
                mm.create("status@MailSecurer.de", "status@MailSecurer.de", Main.Txt("Anzahl_Treffer:") + " " + 0, "", null);
            }
            catch (MessagingException ex)
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
            }

                uid_map_list.add( new MWMailMessage( this, konto, mm, 42, "0000" ) );*/
        }
      //  int level = cnt_level(file);

        if (key.startsWith("INBOX"))
        {
        }
        if (key.startsWith(BROWSETOKEN))
        {

        }
        if (key.startsWith(TESTTOKEN))
        {
            RFCFileMail testmessage = new RFCFileMail( new File("M:\\test.eml"), false);
            uid_map_list.add( new MWMailMessage( this, konto, testmessage, 42, null ) );
        }
    }



    // CREATE DATAFOLDER
    public MailFolder(MailKonto konto, int year, int month, String key)
    {
        this.konto = konto;
       // this.file = file;
        this.key = key;
        this.year = year;
        this.month = month;
        day = -1;

        uid_map_list = new ArrayList<MWMailMessage>();

        uid_validity = Integer.toString(year*365 + month*31 + day);
    }
    // CREATE DATAFOLDER
    public MailFolder(MailKonto konto, int year, int month, int day, String key)
    {
        this.konto = konto;
       // this.file = file;
        this.key = key;
        this.year = year;
        this.month = month;
        this.day = day;

        uid_map_list = new ArrayList<MWMailMessage>();

        uid_validity = Integer.toString(year*365 + month*31 + day);
    }
    synchronized void fill()
    {
        if (!konto.can_browse())
            return;
        
        // DO DATA IN MONTH FOLDER
        if (day == -1 && MailKonto.day_folder)
            return;

        GroupEntry ge = new GroupEntry();
        GregorianCalendar cal1;
        if (day == -1)
        {
            cal1 = new GregorianCalendar(year, month - 1, 1);
        }
        else
        {
            cal1 = new GregorianCalendar(year, month - 1, day);
        }
        Date d1 = cal1.getTime();
        if (day == -1)
            cal1.add(GregorianCalendar.MONTH, 1);
        else
            cal1.add(GregorianCalendar.DAY_OF_MONTH, 1);
        
        Date d2 = cal1.getTime();

       

        String t1 = Long.toString(d1.getTime(), 16);
        String t2 = Long.toString(d2.getTime(), 16);

        String qry_str = CS_Constants.FLD_DATE +":[" + t1 + " TO " + t2 + "]";
        
        SearchCall sc = new SearchCall(konto.m_ctx);
        try
        {
            MandantContext m_ctx = konto.m_ctx;
            sc.search_lucene_qry_str(konto.user, konto.pwd, qry_str, 100000, CS_Constants.USERMODE.UL_USER);

            add_new_mail_resultlist( m_ctx, sc );
        }
        catch (IOException ex)
        {
            Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IllegalArgumentException ex)
        {
            Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (org.apache.lucene.queryParser.ParseException ex)
        {
            Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    public String getKey()
    {
        return key;
    }
    
    public boolean isValid()
    {       
        return true;
    }
   

    public synchronized MWMailMessage getMessage( String uuid )
    {
        try
        {
            MWMailMessage mesg = get_mail_message( uuid);
            return mesg;
            
        }
        catch(Exception e)
        {
            konto.log(e);
        }
        return null;
    }
    public MWMailMessage getUidMesg(int uid)
    {
       
        try
        {
            String uuid = uuid_from_imap_uid( uid );
            return getMessage(uuid);
        }
	catch(Exception e)
        {
            konto.log(e);
        }
        return null;
    }
    public MWMailMessage getMesg(int index)
    {
        return uid_map_list.get(index);
    }
    public MailInfo getInfo(String uuid)
    {
        //Getting information without reading mf-file
        try
        {
            MWMailMessage mesg = get_mail_message(uuid);

            return (MailInfo)mesg;
        }
        catch(Exception e)
        {
            konto.log(e);
        }
        return null;
    }
    public MailInfo getUidInfo(int uid)
    {
        
        try
        {
            String uuid = uuid_from_imap_uid( uid );
            
            return getInfo(uuid);
        }
	catch(Exception e)
        {
            konto.log(e);
        }
        return null;
    }
    public MailInfo getMidInfo(String mid)
    {
        return getInfo( mid );
    }

    public MailInfo getInfo(int index)
    {
        String uuid = uuid_from_imap_uindex( index );

        return getInfo(uuid);
    }
  
   

    public int anzMessages()
    {
        return uid_map_list.size();
    }
    public int lastanzMessages()
    {
        if (last_uid_map == null)
            return 0;

        return last_uid_map.size();
    }

    MWMailMessage get_mail_message(  String uuid )
    {
        for (int i = 0; i < uid_map_list.size(); i++)
        {
            MWMailMessage mm = uid_map_list.get(i);
            if (mm.uuid.compareTo(uuid) == 0)
                return mm;

        }
        return null;

    }
    MWMailMessage get_mail_message( int idx )
    {
         return uid_map_list.get(idx);
    }
    MWMailMessage get_last_mail_message(  int idx  )
    {
         return last_uid_map.get(idx);
    }

   

    
    private String uuid_from_imap_uindex( int index )
    {
        return uid_map_list.get(index).uuid;
    }

    private String uuid_from_imap_uid( int uid )
    {
        for (int i = 0; i < uid_map_list.size(); i++)
        {
            MWMailMessage mm = uid_map_list.get(i);
            if (mm.getUID() == uid)
                return mm.uuid;

        }
        return null;
    }

    String get_uid_validity()
    {
        return uid_validity;
    }

    boolean is_ign_search_token( String string )
    {
        String[] ign_cmd = {"all","answered", "deleted", "undeleted","draft","draft","seen","flagged","new","recent",
        "unseen","old", "unaswered","undraft","unflagged"};
        boolean ret = false;
        for (int j = 0; j < ign_cmd.length; j++)
        {
            String string1 = ign_cmd[j];
            if (string1.compareToIgnoreCase(string) == 0)
            {
                ret = true;
                break;
            }
        }
        return ret;
    }
    boolean is_val_token( String string )
    {
        String[] ign_cmd = {"bcc","cc", "from", "header","keyword","unkeyword","larger","on","sentbefore","senton","sentsince",
        "since","smaller", "subject", "text", "body", "to", "uid"};
        boolean ret = false;
        for (int j = 0; j < ign_cmd.length; j++)
        {
            String string1 = ign_cmd[j];
            if (string1.compareToIgnoreCase(string) == 0)
            {
                ret = true;
                break;
            }
        }
        return ret;
    }

    boolean is_date_arg( String string )
    {
        String[] ign_cmd = {"on","sentbefore","senton","sentsince","since"};
        boolean ret = false;
        for (int j = 0; j < ign_cmd.length; j++)
        {
            String string1 = ign_cmd[j];
            if (string1.compareToIgnoreCase(string) == 0)
            {
                ret = true;
                break;
            }
        }
        return ret;
    }

    boolean is_2val_token( String string )
    {
        String[] ign_cmd = {"header"};
        boolean ret = false;
        for (int j = 0; j < ign_cmd.length; j++)
        {
            String string1 = ign_cmd[j];
            if (string1.compareToIgnoreCase(string) == 0)
            {
                ret = true;
                break;
            }
        }
        return ret;
    }
    boolean is_token( String s1, String s2 )
    {
        return s1.compareToIgnoreCase(s2) == 0;
    }


    boolean search( int min, int max, int offset, String[] part )
    {
        String arg1 = null;
        String arg2 = null;
        int skip = 1;
        GroupEntry ge = new GroupEntry();
        GroupEntry work_ge = ge;
        boolean next_is_or = false;
        
        boolean next_is_not = false;
        Date date = null;



        // WE DO NOT HAVE TO LOOK FOR UID IN INDEX, UID WAS GENERATORD BY FOLDER, JUST SORT OUT WHAT HE WAATS
        boolean search_uid = false;

        String pattern = "EEE, d MMM yyyy HH:mm:ss Z";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);

        // 0 IS THE COMMAND ITSELF
        for (int i = 1; i < part.length; i += skip)
        {
            skip = 1;
            String token = part[i].toLowerCase();

            if (is_token(token, "and"))
            {
             
                next_is_or = false;
                continue;
            }
            if (is_token(token, "or"))
            {
             
                next_is_or = true;
                continue;
            }
            if (is_token(token, "not"))
            {
                next_is_not = true;
                continue;
            }

            if (is_ign_search_token( token ))
                continue;
            
            if (is_val_token( token))
            {
                if (i > part.length - 1)
                    return false;
                skip = 2;
                arg1 = part[i+1];
            }
            if (is_2val_token( token))
            {
                if (i > part.length - 1)
                    return false;
                skip = 3;
                arg1 = part[i+1];
                arg2 = part[i+2];
            }
            if (is_date_arg( token ))
            {
                try
                {
                    date = sdf.parse(arg1);
                }
                catch (ParseException ex)
                {
                    Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, "Invalid date: " + arg1, ex);
                    continue;
                }
            }

            if (is_token(token, "bcc"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_BCC, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "cc"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_CC, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "from"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_FROM, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "to"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TO, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "subject"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_SUBJECT, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "text"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_BODY, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "body"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_BODY, arg1, ExprEntry.OPERATION.CONTAINS, ExprEntry.TYPE.STRING, next_is_not, next_is_or));
            else if (is_token(token, "before"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "< " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else if (is_token(token, "sentbefore"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "< " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else if (is_token(token, "since"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "> " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else if (is_token(token, "sentsince"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "> " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else if (is_token(token, "on"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "= " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else if (is_token(token, "senton"))
                ge.getChildren().add(new ExprEntry(ge.getChildren(), CS_Constants.FLD_TM, "= " + date.getTime(), ExprEntry.OPERATION.REGEXP, ExprEntry.TYPE.TIMESTAMP, next_is_not, next_is_or));
            else
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, "Invalid search token: " + token );
            }
           
            // RESET FLAGS
        /*    next_is_or = false;
   
            next_is_not = false;*/
        }
        if (!search_uid)
        {

            SearchCall sc = new SearchCall(konto.m_ctx);
            try
            {
                MandantContext m_ctx = konto.m_ctx;
                sc.search_lucene(konto.user, konto.pwd, ge.getChildren(), 100, CS_Constants.USERMODE.UL_USER);

                add_new_mail_resultlist( m_ctx, sc );
            }
            catch (IOException ex)
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (IllegalArgumentException ex)
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
            }
            catch (org.apache.lucene.queryParser.ParseException ex)
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else
        {
        }


        return true;                
    }

    public void add_new_mail_resultlist( MandantContext m_ctx, SearchCall sc ) throws IOException
    {        
        last_uid_map = uid_map_list;

        uid_validity = Long.toString(System.currentTimeMillis() / 1000);
        uid_map_list = new ArrayList<MWMailMessage>();
        

        int results = sc.get_result_cnt();

        RFCMimeMail mm = null;
        /*try
        {*/
           /* StringBuffer sb = new StringBuffer();
            SearchCall.gather_lucene_qry_text(sb, ge.getChildren(), 0);

            mm = new RFCMimeMail();
            mm.create("status@MailSecurer.de", "status@MailSecurer.de",
                    Main.Txt("Anzahl_Treffer:") + " " + results,
                    Main.Txt("Die_Abfrage_lautete:") + " " + sb.toString(), null);
            */
            //uid_map_list.add( new MWMailMessage( this, konto, mm, 42/*uid++*/, "0000" ) );
       /* }
        catch (MessagingException messagingException)
        {
        }*/




        Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, "Results found: " + results );
        for (int i = 0; i < results; i++)
        {
            SearchResult result = sc.get_res(i);
            
            DiskSpaceHandler dsh = m_ctx.get_dsh(result.getDs_id());
            if (dsh == null)
            {
                LogManager.err_log_fatal("Found ds " +result.getDs_id() + " in index, but index is gone" );
                continue;
            }
            try
            {
                long time = DiskSpaceHandler.get_time_from_uuid(result.getUuid());
                
                RFCGenericMail rfc = dsh.get_mail_from_time(time, dsh.get_enc_mode(), dsh.get_fmode());

                MWMailMessage mail = new MWMailMessage( this, konto, rfc, 42 + i, result );

                uid_map_list.add( mail );
            }
            catch (VaultException ex)
            {
                Logger.getLogger(MailFolder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        update_uid_validity();
    }

    void update_uid_validity()
    {
        //uid_validity = Long.toString(System.currentTimeMillis() / 1000);
    }
    
    
}
