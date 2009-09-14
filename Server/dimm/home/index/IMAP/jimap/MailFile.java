/*  MailFile implementation
 *  Copyright (C) 2006 Dirk friedenberger <projekte@frittenburger.de>
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package dimm.home.index.IMAP.jimap;
import java.util.*;

public class MailFile
{
    String file;
    MailKonto konto;
    String key = null;
    

    public MailFile(MailKonto konto, String file,String key)
    {
        this.konto = konto;        
        this.file = file;
        this.key = key;
    }
    private MailFile(String file) //temporraer, darf nichgt nach aussen gegeben werden 
    {
        this.konto = null;
        this.file = file;
        this.key = null;
    }

    public String getKey()
    {
        return key;
    }
    
    public boolean isValid()
    {       
        return true;
    }
   

    public synchronized MailMessage getMessage( String uuid )
    {
        try
        {
            MailMessage mesg = get_mail_message( uuid);
            return mesg;
            
        }
        catch(Exception e)
        {
            konto.log(e);
        }
        return null;
    }
    public MailMessage getUidMesg(int uid)
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
    public MailMessage getMesg(int index)
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
            MailMessage mesg = get_mail_message(uuid);

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
        return 5;
    }

    private MailMessage get_mail_message(  String uuid )
    {
        for (int i = 0; i < uid_map.size(); i++)
        {
            MailMessage mm = uid_map.get(i);
            if (mm.uuid.compareTo(uuid) == 0)
                return mm;

        }
        return null;

    }

   

    ArrayList<MailMessage> uid_map;
    private String uuid_from_imap_uindex( int index )
    {
        return uid_map.get(index).uuid;
    }

    private String uuid_from_imap_uid( int uid )
    {
        for (int i = 0; i < uid_map.size(); i++)
        {
            MailMessage mm = uid_map.get(i);
            if (mm.uid == uid)
                return mm.uuid;

        }
        return null;
    }
    
    
}
