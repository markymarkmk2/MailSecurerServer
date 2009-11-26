/*  MailKonto implementation
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

import dimm.home.mailarchiv.MandantContext;
import java.util.ArrayList;

public class MailKonto
{
    String of;
    private boolean _init = false;
    String user;
    String pwd;
    String name;
    ArrayList<String> mail_alias_list;
    ArrayList<MailFolder> mail_folders;
    MandantContext m_ctx;
    public static final boolean qry_folder = false;


    public MailKonto(String user, String pwd, MandantContext _mtx, ArrayList<String> mail_alias_list)
    {
        this.user = user;
        this.pwd = pwd;
        this.name = user;
        m_ctx = _mtx;
        this.mail_alias_list = mail_alias_list;

        mail_folders = new ArrayList<MailFolder>();

        mail_folders.add( new MailFolder(this, "INBOX", "INBOX"));
        if (qry_folder)
            mail_folders.add( new MailFolder(this, "Query", MailFolder.QRYTOKEN));
        
        try
        {
            _init = true;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }     
    }
    public boolean isInitialized()
    {
        return _init;
    }
    public String getName() //mop wird das benutzt
    {
        return name;
    }
    
    public boolean authenticate(String user,String passwd)
    {
        try
        {
            
            if(!this.user.equals(user)) 
                throw new Exception("wrong user "+user+" != "+this.user);
            
            if(!this.pwd.equals(passwd))
                throw new Exception("wrong password");
            
            return true;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }
    
    
  
   
    public String getParameter(String folder)
    {
        System.out.println("getParameter " + folder);


            //Eigenschaften ermiiteln 
            return "";
        
    }
    
    
   
    //verwaltung
    public final static String LOADDATE = "loaddate";
    public final static String UID      = "uid";
    public final static String NEXTUID  = "nextuid";
    public final static String FLAGS    = "flags";

    public final static String GLOBAL   = "global";

    public boolean exists(String id) //and create
    {
            return true;
    }

    
    boolean isGlobal(String tag,String value)
    {
        if(tag.equals(FLAGS))
        {
            if(value.toLowerCase().equals("junk")) return true;
            if(value.toLowerCase().equals("nonjunk")) return true;
            if(value.toLowerCase().equals("\\seen")) return true;
            return false;
        }
        return true;
    }



    public void log(Object o)
    {
        if(o instanceof Exception)
            ((Exception)o).printStackTrace();
        else
            System.out.println(o);                    
    }


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


    public MailFolder select(String key)
    {
        System.out.println("select " + key);

        for (int i = 0; i < mail_folders.size(); i++)
        {
            MailFolder mf = mail_folders.get(i);
            if (mf.key.compareTo(key) == 0)
            {
                mf.update_uid_validity();
                return mf;
            }
        }
        return null;
    }


    String[] getDirlist( String string )
    {
        int level = cnt_level( string );

        String[] m_txt = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember" };
        
        switch( level )
        {
            case 0:
            {
                // LEVEL 1 IS "."
                ArrayList<String> fs = new ArrayList<String>();
                fs.add("INBOX");
                if (qry_folder)
                    fs.add(MailFolder.QRYTOKEN);

/*                for (int i = 0; i < 1; i++)
                {
                    String year = "INBOX" + "/" + Integer.toString(2008 + i );
                    fs.add( year );
                    for ( int m = 0; m < m_txt.length; m++)
                    {
                        String month = Integer.toString( m + 1 );
                        if (m < 9)
                            month = "0" + month;

                     //   month += " " + m_txt[m] + "";

                        fs.add( year + "/" + month);
//                        for ( int d = 1; d< 31; d++)
                        for ( int d = 1; d< 2; d++)
                        {
                            String day = Integer.toString( d );
                            if (d < 10)
                                day = "0" + day;

                            fs.add( year + "/" + month + "/" + day);
                        }
                    }
                }
 * */
                String[] arr = fs.toArray(new String[0]);
                return arr;
            }
         }
        return new String[0];
    }
}
