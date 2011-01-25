/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

import java.io.IOException;
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
    
    /*

     * 8.8 UID FETCH 3 (INTERNALDATE UID RFC822.SIZE FLAGS BODY.PEEK[HEADER.FIELDS (date subject from to cc message-id in-reply-to references x-priority x-mail-rss-source-name x-uniform-type-identifier x-universally-unique-identifier x-apple-mail-todo-message-id x-apple-mail-todo-id received-spf x-spam-status x-spam-flag content-type)])
     * 9.8 UID FETCH 3 (BODYSTRUCTURE BODY.PEEK[HEADER])
     * 10.8 UID FETCH 3 (BODY.PEEK[2] BODY.PEEK[3] BODY.PEEK[5] BODY.PEEK[1.1] BODY.PEEK[1.2])
     * 7.8 FETCH 1:3 (FLAGS UID)
     * 12.8 UID FETCH 3 BODY.PEEK[4]<65536.7048>
     * 15.8 UID STORE 3 +FLAGS.SILENT (\Seen)
     *
     * */


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


            // IF SPACE DELIMITED BODY, THEN WE SEARCH FOR CLOSING BRACKET
            if (tr != ')' && line.startsWith("BODY"))
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


    public abstract int action(  ImapsInstance is, String sid, String parameter) throws IOException;
}
