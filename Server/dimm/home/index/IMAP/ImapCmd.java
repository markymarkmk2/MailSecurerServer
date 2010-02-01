/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import java.util.Vector;

/**
 *
 * @author mw
 */
public abstract class ImapCmd
{
    String cmd;
    protected final static String RESTAG = "* ";
    protected int counter;

    public int getNextCounter()
    {
        return ++counter;
    }
    public void resetCounter()
    {
        counter = 0;
    }



    public String getCmd()
    {
        return cmd;
    }

    public ImapCmd(  String cmd )
    {
        this.cmd = cmd;
    }
    static String[] imapsplit( String line )
    {

        Vector<String> v = new Vector<String>();
        while (true)
        {
            line = line.trim();
            char tr = ' ';
            if (line.startsWith("\""))
            {
                line = line.substring(1);
                tr = '\"';
            }
            if (line.startsWith("("))
            {
                line = line.substring(1);
                tr = ')';
            }
            if (line.startsWith("{"))
            {
                line = line.substring(1);
                tr = '}';
            }
            if (line.trim().equals(""))
            {
                break;
            }
            int i = -1;
            if (tr == ' ')
                i = line.indexOf(tr);
            else if (tr == ')')
                i = line.lastIndexOf(tr);
            else
                i = line.indexOf(tr);


            if (line.startsWith("BODY"))
            {
                int klauf = line.indexOf('[');
                if (klauf > 0 && i > 0 && klauf < i)
                {
                    int klzu = line.indexOf(']');
                    if (klzu > 0)
                    {
                        i = klzu + 1;
                        if (i >= line.length())
                            i = -1;
                    }

                }
            }
            if (i < 0)
            {
                v.add(line);
                break;
            }

            v.add(line.substring(0, i));
            if (tr != ' ' && i < line.length() - 1)
                i++;
            line = line.substring(i + 1);
        }
        String part[] = new String[v.size()];
        for (int x = 0; x < part.length; x++)
        {
            part[x] =  v.elementAt(x);
        }
        return part;
    }

    static String[] pathsplit( String line )
    {
        Vector<String> v = new Vector<String>();
        while (true)
        {
            line = line.trim();
            String tr = "/";
            int i = line.indexOf(tr);
            if (i < 0)
            {
                v.add(line);
                break;
            }
            v.add(line.substring(0, i));
            line = line.substring(i + 1);
        }
        String part[] = new String[v.size()];
        for (int x = 0; x < part.length; x++)
        {
            part[x] = v.elementAt(x);
        }
        return part;
    }


    public abstract int action(  MWImapServer is, String sid, String parameter);
}
