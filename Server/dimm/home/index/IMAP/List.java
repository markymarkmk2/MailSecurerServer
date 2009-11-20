/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class List extends ImapCmd
{
    public List()
    {
        super( "list");
    }

    @Override
    public int action( MWImapServer is, String sid, String parameter )
    {
        return list( is, sid, parameter );
    }

    static  int list( MWImapServer is, String sid, String par )
    {
        int h;
        int anz;


        if (is.konto != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                if (part[1].length() == 0)
                {
                    is.response("LIST  \"/\" ");  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].compareTo("*") == 0)
                {
                    is.response("LIST (\\HasNoChildren) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    is.response("LIST (\\HasNoChildren) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith("INBOX"))
                {
                    is.response("LIST (\\HasNoChildren) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith( MailFolder.QRYTOKEN ))
                {
                    is.response("LIST (\\HasNoChildren) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }

                String dirlist[] = is.konto.getDirlist(".");
                String req[] = pathsplit(part[1]);
                for (int i = 0; i < dirlist.length; i++)
                {
                    String qreq[] = pathsplit(dirlist[i]);
                    if (req.length != qreq.length)
                    {
                        continue;
                    }
                    for (h = 0; h < req.length; h++)
                    {
                        if (req[h].equals("%"))
                        {
                            continue;
                        }
                        if (req[h].equals(qreq[h]))
                        {
                            continue;
                        }
                        break;
                    }
                    if (h < req.length)
                    {
                        continue;
                    }

                    is.response("LIST (" + "" + ") \"/\" " + dirlist[i]);  // \\NoInferiors \\HasNoChildren
                }
                is.response(sid, true, "LIST completed");
                return 0;
            }
        }
        is.response(sid, false, "LIST failed");
        return 1;
    }
}
