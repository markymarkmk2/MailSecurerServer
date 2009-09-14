/*  SimpleFilter implementation
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
package dimm.home.index.IMAP.mailfilter;
import dimm.home.index.IMAP.jimap.MailKonto;
import dimm.home.index.IMAP.jimap.MailMessage;
import dimm.home.index.IMAP.jimap.MailQueue;
import dimm.home.index.IMAP.util.ObjectCollector;


public class SimpleFilter implements MailQueue
{
    MailKonto konto;
    public void set(MailKonto konto)
    {
        this.konto = konto;
    }
    ObjectCollector list;
    String hitoutbox = "INBOX";
    String outbox    = "INBOX";
    String mark      = null;
    public void init(ObjectCollector data)
    {
        if(data != null)
        {
            ObjectCollector d[] = data.resetandgetArray();
            for(int i = 0;i < d.length;i++)
            {
                String n = d[i].getName();
                if(n.equals("list")) list = konto.getObjectCollector(d[i].getValue());
                if(n.equals("hitout")) hitoutbox  = d[i].getValue();
                if(n.equals("mark"))   mark  = d[i].getValue();
                if(n.equals("out"))    outbox  = d[i].getValue();
            }
        }
    }
    public void add(MailMessage m) {
        
        if(list != null)
        {
            for(int i = 0;i < MailMessage.Keys.length;i++)
            {
                ObjectCollector tags[] = list.resetandgetArray(MailMessage.Keys[i]);
                String value = m.get(MailMessage.Keys[i]);
                if(value != null)
                    for(int x = 0;x < tags.length;x++)
                    {
                        if(0 <= value.indexOf(tags[x].getValue()))
                        {
                            m.SetMark(mark);
                            konto.add(hitoutbox,m);
                            return;
                        }
                    }
            }
        }
        konto.add( outbox,m);
        return;
    }
    public void raise(String messageid,String tag,String mfid)
    {
        if(tag.equals(MailKonto.FLAGS))
        {
            String flags = konto.get(tag,mfid,messageid).toLowerCase();
            if(flags.indexOf("nonjunk") >= 0)
            {
                //konto.log(mfid+" :is no junk "+messageid);
            }
            else if(flags.indexOf("junk") >= 0)
            {  
                //konto.log(mfid+" :is junk "+messageid);
            }
        }
    }
}
