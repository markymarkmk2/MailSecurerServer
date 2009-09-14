/*  ObjectCollector implementation
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
package dimm.home.index.IMAP.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.Vector;

public class ObjectCollector implements Serializable
{

    public String value = "";
    public String name = "";
    private ObjectCollector ob[] = null;
    private int anz_ob = 0;

    public ObjectCollector()
    {
    }

    public ObjectCollector( String name )
    {
        this.name = name;
    }

    public ObjectCollector( String name, String value )
    {
        this.name = name;
        this.value = value;
    }

    public void add( String val, String opt )
    {
        add(new ObjectCollector(val, opt));
    }

    public void add( ObjectCollector obj )
    {
        debug("obj addiert " + obj.getName());
        ObjectCollector tempob[] = new ObjectCollector[anz_ob + 1];
        for (int x = 0; x < anz_ob; x++)
        {
            tempob[x] = ob[x];
        }
        tempob[anz_ob] = obj;
        ob = tempob;
        anz_ob++;
    }

    public void set( ObjectCollector obj )
    {
        boolean found = false;
        debug("obj ausgetauscht " + obj.getName());
        for (int x = 0; x < anz_ob; x++)
        {
            if (ob[x].getName().equals(obj.getName()))
            {
                ob[x] = obj;
                found = true;
            }
        }
        if (!found)
        {
            add(obj);
        }
    }

    public String getValue()
    {
        return value;
    }

    public String getName()
    {
        return name;
    }

    public void reset()
    {
        pos = 0;
    }
    private int pos = 0;

    public ObjectCollector[] resetandgetArray( String name )
    {
        pos = 0;
        return getArray(name);
    }

    public ObjectCollector[] resetandgetArray()
    {
        pos = 0;
        return getArray(null);
    }

    public ObjectCollector resetandget( String name )
    {
        pos = 0;
        return get(name);
    }

    public ObjectCollector get()
    {
        ObjectCollector obj = null;
        if (pos < anz_ob)
        {
            obj = ob[pos];
            pos++;
        }
        return obj;
    }

    public ObjectCollector[] getArray( String name )
    {
        Vector v = new Vector();
        for (int x = pos; x < anz_ob; x++)
        {
            pos++;
            if (name == null || name.equals(ob[x].getName()))
            {
                v.add(ob[x]);
            }
        }
        ObjectCollector a[] = new ObjectCollector[v.size()];
        for (int i = 0; i < v.size(); i++)
        {
            a[i] = (ObjectCollector) v.get(i);
        }
        return a;
    }

    public ObjectCollector get( String name )
    {
        for (int x = pos; x < anz_ob; x++)
        {
            pos++;
            if (name.equals(ob[x].getName()))
            {
                return ob[x];
            }
        }
        return null;
    }
    //Parsing

    public String parse( String text )
    {
        text = cutToken(text);
        name = Token;
        int act_pos = text.indexOf("<");
        if (act_pos < 0)
        {
            return "no token";
        }
        value = text.substring(0, act_pos).trim();
        text = text.substring(act_pos);
        while (true)
        {
            text = cutToken(text);
            if (start == 0 && Token.equals(name))
            {
                break;
            }
            else if (start == 1)
            {
                ObjectCollector obj = new ObjectCollector();
                text = obj.parse("<" + Token + ">" + text);
                if (text.startsWith("error"))
                {
                    return text;
                }
                add(obj);
            }
            else
            {
                return "error Ende parsing";
            }
        }

        return text;
    }
    private String Token = "";
    private int start = -1;

    private String cutToken( String text )
    {
        start = -1;
        int s = text.indexOf("<");
        int e = text.indexOf(">");
        int ende = text.indexOf("/");
        if (s < e && s > -1 && e > -1)
        {
            start = 1;
            if (ende == s + 1)
            {
                start = 0;
                s = s + 1;
            }
            Token = text.substring(s + 1, e);
            return text.substring(e + 1);
        }
        return text;
    }

    public String objtoString()
    {
        String ret = "";
        for (int x = 0; x < anz_ob; x++)
        {
            ret += ob[x].toString();
        }
        return ret;
    }

    @Override
    public String toString()
    {
        String ret = "<" + name + ">";
        if (value.equals(""))
        {
            ret += "\n" + objtoString();
        }
        else
        {
            ret += value;
        }
        ret += "</" + name + ">\n";
        return ret;
    }

    static void debug( String ausgabe )
    {
        System.out.println(ausgabe);
    }
    //hilfsroutinen 

    public static ObjectCollector Config( String f )
    {
        String file = readFile(f);
        if (file == null)
        {
            return null;
        }
        ObjectCollector o = new ObjectCollector();
        o.parse(file);
        return o;
    }

    public static String readFile( String file )
    {
        String data = "";
        try
        {
            String line = "";
            BufferedReader in = new BufferedReader(new FileReader(file));
            while ((line = in.readLine()) != null)
            {
                data += line;
            }
        }
        catch (Exception e)
        {
            return null;
        }
        return data;
    }

    public static void saveFile( String file, String data )
    {
        try
        {
            BufferedWriter fout = new BufferedWriter(new FileWriter(file));
            fout.write(data);
            fout.close();
        }
        catch (Exception e)
        {
        }
        
    }
}
