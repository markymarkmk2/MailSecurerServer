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
import home.shared.SQL.OptCBEntry;
import home.shared.SQL.UserSSOEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

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
    UserSSOEntry sso_entry;
    MWImapServer is;

    public static final boolean qry_folder = true;
    public static final boolean browse_folder = true;
    public static final boolean day_folder = true;

    public static final boolean test_folder = false;

    boolean can_browse()
    {
        if (sso_entry == null)
            return true;
        
        if (sso_entry.is_admin())
            return true;

        // CHECK FOR ADMIN FLAG IN ROLE
        if (sso_entry.role_has_option(OptCBEntry.IMAP_BROWSE))
            return true;

        return false;
    }

    public MailKonto(MWImapServer is, String user, String pwd, MandantContext _mtx, ArrayList<String> mail_alias_list, UserSSOEntry sso_entry)
    {
        this.is = is;
        this.user = user;
        this.pwd = pwd;
        this.name = user;
        this.sso_entry = sso_entry;
        m_ctx = _mtx;
        this.mail_alias_list = mail_alias_list;

        mail_folders = new ArrayList<MailFolder>();

        mail_folders.add( new MailFolder(this, "INBOX"));
        if (qry_folder)
        {
            MailFolder new_folder = is.parent.get_cached_folder(user, MailFolder.QRYTOKEN);
            if (new_folder == null)
            {
                new_folder = new MailFolder(this,  MailFolder.QRYTOKEN);
                is.parent.add_to_folder_cache(new_folder, user);
            }

            mail_folders.add(new_folder );            
        }
        if (browse_folder)
        {
            mail_folders.add( new MailFolder(this,  MailFolder.BROWSETOKEN));
        }
        if (test_folder)
        {
            mail_folders.add( new MailFolder(this,  MailFolder.TESTTOKEN));
        }
        
        try
        {
            _init = true;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }     
    }

    public void close()
    {
        for (int i = 0; i < mail_folders.size(); i++)
        {
            MailFolder folder = mail_folders.get(i);
            folder.close();
        }
        mail_folders.clear();
        mail_alias_list.clear();
    }

    public boolean isInitialized()
    {
        return _init;
    }
    public String getName() //mop wird das benutzt
    {
        return name;
    }

    public String get_username()
    {
        return user;
    }
    public String get_pwd()
    {
        return pwd;
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
//                mf.update_uid_validity();
                return mf;
            }
        }
        if (key.startsWith(MailFolder.QRYTOKEN))
        {
            MailFolder cached_folder = is.get_parent().get_cached_folder(user, key);
            if (cached_folder != null)
            {
                mail_folders.add(cached_folder);
                return cached_folder;
            }
        }
        if (can_browse() && key.startsWith(MailFolder.BROWSETOKEN))
        {
            MailFolder cached_folder = is.get_parent().get_cached_folder(user, key);
            if (cached_folder != null)
            {
                mail_folders.add(cached_folder);
                return cached_folder;
            }

            String[] arr = key.split("/");
            if (arr.length == 3)
            {
                int year = Integer.parseInt(arr[1]);
                int month = Integer.parseInt(arr[2]);

                MailFolder folder = new MailFolder(this, year, month, key);
                folder.fill();
                is.get_parent().add_to_folder_cache(folder, user);
                mail_folders.add(folder);
                return folder;
            }
            if (arr.length == 4)
            {
                int year = Integer.parseInt(arr[1]);
                int month = Integer.parseInt(arr[2]);
                int day = Integer.parseInt(arr[3]);

                MailFolder folder = new MailFolder(this, year, month, day, key);
                folder.fill();
                is.get_parent().add_to_folder_cache(folder, user);
                mail_folders.add(folder);
                return folder;
            }
        }
        return null;
    }


    String[] getDirlist( String string )
    {
        int level = cnt_level( string );
        GregorianCalendar cal = new GregorianCalendar();
        Date now = new Date();
        cal.setTime(now);
        int month = cal.get(GregorianCalendar.MONTH) + 1;  // 1...12

       // String[] m_txt = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember" };
        
        switch( level )
        {
            case 0:
            {
                // LEVEL 1 IS "."
                ArrayList<String> fs = new ArrayList<String>();
                fs.add("INBOX");
                if (qry_folder)
                    fs.add(MailFolder.QRYTOKEN);

                if (test_folder)
                    fs.add(MailFolder.TESTTOKEN);

                if (browse_folder && can_browse())
                {
                    fs.add(MailFolder.BROWSETOKEN);
                    int last_year = -1;
                    for (int i = 1; i <= 12; i++)
                    {
                        int act_month = month -12 + i;
                        int act_year = cal.get(GregorianCalendar.YEAR);
                        if (act_month <= 0)
                        {
                            act_month += 12;
                            act_year--;
                        }
                        String year_str = MailFolder.BROWSETOKEN + "/" + Integer.toString(act_year);
                        if (act_year != last_year)
                        {
                            fs.add( year_str );
                            last_year = act_year;
                        }
                        fs.add( year_str + "/" + (act_month < 10 ? "0"+act_month : act_month));
                        if (day_folder)
                        {
                            GregorianCalendar dcal = new GregorianCalendar(act_year, month, 1);
                            int days = cal.getMaximum(GregorianCalendar.DAY_OF_MONTH);

                            for (int act_day = 1; act_day <= days; act_day++)
                            {
                                fs.add( year_str + "/" + (act_month < 10 ? "0"+act_month : act_month) + "/" + (act_day < 10 ? "0"+act_day : act_day));
                            }                            
                        }
                    }
                }

                String[] arr = fs.toArray(new String[0]);
                return arr;
            }
         }
        return new String[0];
    }
}
