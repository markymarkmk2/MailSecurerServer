
package dimm.home.index.IMAP;
import java.util.*;

public class MailFolder
{
    String file;
    MailKonto konto;
    String key = null;
    ArrayList<MWMailMessage> uid_map;
    ArrayList<MWMailMessage> last_uid_map;
    String uid_validity;

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

    public static int uid = 10;
    public MailFolder(MailKonto konto, String file,String key)
    {
        this.konto = konto;        
        this.file = file;
        this.key = key;
        uid_map = new ArrayList<MWMailMessage>();

        uid_validity = Long.toString(System.currentTimeMillis() / 1000);


        if (file.equals(QRYTOKEN))
        {
            //uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test.eml", uid++  ));
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test" + uid % 3  + ".eml", uid++ ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test" + uid % 3  + ".eml", uid ) );
        }
        int level = cnt_level(file);

        if (file.startsWith("INBOX") && level == 4)
        {
//            select_mail_per_date(
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test.eml", 1  ));
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test0.eml", 2 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test1.eml", 3 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test2.eml", 4 ) );
        }
    }
    public void create_new_mail()
    {
        last_uid_map = uid_map;

        uid_validity = Long.toString(System.currentTimeMillis() / 1000);
        uid_map = new ArrayList<MWMailMessage>();
        uid++;
        uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test" + uid % 3  + ".eml", uid++ ) );
        uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test" + uid % 3  + ".eml", uid ) );
    }
  /*  private MailFolder(String file) //temporraer, darf nichgt nach aussen gegeben werden
    {
        this.konto = null;
        this.file = file;
        this.key = null;
        uid_map = new ArrayList<MWMailMessage>();
        uid_validity = Long.toString(System.currentTimeMillis() / 1000);


        if (file.equals("INBOX"))
        {
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test.eml", 1  ));
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test0.eml", 2 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test1.eml", 3 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test2.eml", 4 ) );
        }
        if (file.startsWith("Query/200"))
        {
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test.eml", 1  ));
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test0.eml", 2 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test1.eml", 3 ) );
            uid_map.add( new  MWMailMessage( this, konto, "Z:\\Mailtest\\test2.eml", 4 ) );
        }
    }*/

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
        try
        {
           String uuid = uuid_from_imap_uindex( index );
          
           return getMessage(uuid);
        }
	catch(Exception e)
        {
            konto.log(e);
        }
        return null;
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
        return uid_map.size();
    }
    public int lastanzMessages()
    {
        if (last_uid_map == null)
            return 0;

        return last_uid_map.size();
    }

    MWMailMessage get_mail_message(  String uuid )
    {
        for (int i = 0; i < uid_map.size(); i++)
        {
            MWMailMessage mm = uid_map.get(i);
            if (mm.uuid.compareTo(uuid) == 0)
                return mm;

        }
        return null;

    }
    MWMailMessage get_mail_message( int idx )
    {
         return uid_map.get(idx);
    }
    MWMailMessage get_last_mail_message(  int idx  )
    {
         return last_uid_map.get(idx);
    }

   

    
    private String uuid_from_imap_uindex( int index )
    {
        return uid_map.get(index).uuid;
    }

    private String uuid_from_imap_uid( int uid )
    {
        for (int i = 0; i < uid_map.size(); i++)
        {
            MWMailMessage mm = uid_map.get(i);
            if (mm.uid == uid)
                return mm.uuid;

        }
        return null;
    }

    String get_uid_validity()
    {
        return uid_validity;
    }

    void search( int min, int max, int offset, String[] part )
    {

        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    
}
