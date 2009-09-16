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

public class MailKonto
{
    String of;
    private boolean _init = false;
    String user;
    String pwd;
    String name;

    public MailKonto(String user, String pwd)
    {
        this.user = user;
        this.pwd = pwd;
        this.name = user;
        
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
    
    public MailFile select(String folder)
    {
        System.out.println("select " + folder);

        MailFile f = new MailFile(this, folder, ImapServer.cleanup(folder));
        if(f.isValid())
            return f;
        return null;

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

    String[] getDirlist( String string )
    {
        // LEVEL 1 IS "."
        return new String[]{"INBOX"};
    }
}
