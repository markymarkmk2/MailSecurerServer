/*  MailMessage implementation
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
package dimm.home.index.IMAP;

import java.util.*;

public class MailMessage implements MailInfo
{
    String uuid;
    String header = null;
    Vector mesg = null;
    int bodysize = 0;
    int mesgsize = 0;
    boolean ishead = true;
    int uid = 0;
    String messageid = null;
    MailKonto parent = null;
    MailFile mailfile = null;
    boolean read = false;

    public MailMessage()
    {
        throw new NullPointerException("Constructor not supported");
    }
/*
    public MailMessage( MailFile mailfile, MailKonto parent )
    {
        if (parent == null)
        {
            throw new NullPointerException("parent == null");
        }
        if (mailfile == null)
        {
            throw new NullPointerException("mailfile == null");
        }
        this.parent = parent;
        this.mailfile = mailfile;
    }
*/
    public MailMessage( MailFile mailfile, MailKonto parent, String messageid, int uid )
    {
        this.parent = parent;
        this.mailfile = mailfile;
        this.messageid = messageid;
        this.uuid = messageid;
        this.uid = uid;
    }

   

    @Override
    public int getUID()
    {
        return uid;
    }

    @Override
    public String getMID()
    {
        if (messageid == null)
        {
            throw new NullPointerException("messageid == null");
        }
        return messageid;
    }

    @Override
    public String getFlags()
    {
        return "";
        //return parent.get(MailKonto.FLAGS, mailfile.getKey(), messageid) + " " + parent.get(MailKonto.FLAGS, MailKonto.GLOBAL, messageid);
    }
    //nur fuers abspeichern 
    //public void add(char [] buffer,int size)
    //{
    // header += new String(buffer,0,size);
    //ist uninterresant das alles in den header gespeichert wird, weill eh erst abgespeichert wird;
    //}
    Hashtable h = new Hashtable();


    public final static String FROM = "from";
    public final static String TO = "to";
    public final static String SUBJECT = "subject";
    public final static String Keys[] = new String[]
    {
        FROM, TO, SUBJECT
    };

    public String get( String key )
    {
        return (String) h.get(key);
    }

     @Override
    public int getRFC822size()
    {
        if (!read)
        {
            return mesgsize;
        }
        return header.length() + bodysize;
    }

 
    @Override
    public String getRFC822header()
    {
        return header;
    }

  
    public Vector getRFC822body()
    {
        return mesg;
    }

    public static String getMessageId( String line )
    {
        String mid = null;
        if (line.trim().toLowerCase().startsWith("message-id"))
        {
            mid = line;
            int s = mid.indexOf("<");
            if (-1 < s)
            {
                mid = mid.substring(s + 1);
            }
            s = mid.lastIndexOf(">");
            if (-1 < s)
            {
                mid = mid.substring(0, s);
            }
        }
        return mid;
    }
}
