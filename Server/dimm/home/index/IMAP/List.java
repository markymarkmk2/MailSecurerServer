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


        if (is.get_konto() != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                if (part[1].length() == 0)
                {
                    is.response("LIST (\\Marked) \"/\" \"\"");  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }
                /*
                if (part[1].compareTo("*") == 0)
                {
                    if (MailKonto.qry_folder)
                        is.response("LIST (\\Marked) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    if (MailKonto.browse_folder)
                        is.response("LIST (\\Marked) \"/\" " + MailFolder.BROWSETOKEN);  // \\NoInferiors \\HasNoChildren
                    is.response("LIST (\\Marked) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    
                    is.response(sid, true, "LIST completed");
                    return 0;
                }*/
                if (part[1].startsWith("INBOX"))
                {
                    is.response("LIST (\\Marked) \"/\" INBOX");  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }
                if (part[1].startsWith( MailFolder.QRYTOKEN ))
                {
                    is.response("LIST (\\Marked) \"/\" " + MailFolder.QRYTOKEN);  // \\NoInferiors \\HasNoChildren
                    is.response(sid, true, "LIST completed");
                    return 0;
                }

                String dirlist[] = is.get_konto().getDirlist(".");
                String req[] = pathsplit(part[1]);
                for (int i = 0; i < dirlist.length; i++)
                {
                    String qreq[] = pathsplit(dirlist[i]);
                    if (req.length != qreq.length && req.length > 0 && !req[0].equals("*"))
                    {
                        continue;
                    }
                    for (h = 0; h < req.length; h++)
                    {
                        if (req[h].equals("%"))
                        {
                            continue;
                        }
                        if (req[h].equals(qreq[h]) || req[h].equals("*"))
                        {
                            continue;
                        }
                        break;
                    }
                    if (h < req.length)
                    {
                        continue;
                    }

                    is.response("LIST (\\Marked) \"/\" " + dirlist[i]);  // \\NoInferiors \\HasNoChildren
                }
                is.response(sid, true, "LIST completed");
                return 0;
            }
        }
        is.response(sid, false, "LIST failed");
        return 1;
    }
}
