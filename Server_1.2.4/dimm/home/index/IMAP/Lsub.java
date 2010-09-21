/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index.IMAP;

/**
 *
 * @author mw
 */
public class Lsub extends ImapCmd
{
    public Lsub()
    {
        super( "lsub");
    }

    @Override
    public int action( ImapsInstance is, String sid, String parameter )
    {
        return lsub( is, sid, parameter );
    }

    static int lsub( ImapsInstance is, String sid, String par )
    {
        if (is.get_konto() != null)
        {
            String part[] = imapsplit(par);
            if (part != null && part.length >= 2)
            {
                String dirlist[] = is.get_konto().getDirlist(part[0]);
                for (int i = 0; i < dirlist.length; i++)
                {
                    boolean filter = true;
                    //filtern der directories
                    int l = part[1].length();
                    if (l > 1)
                    {
                        if (part[1].charAt(l-1) == '*')
                            part[1] = part[1].substring(0, l-2);
                    }

                    if (dirlist[i].startsWith(part[1]))
                    {
                        filter = false;
                    }
                    if (part[1].equals("*"))
                    {
                        filter = false;
                    }

                    if (filter)
                    {
                        continue;
                    }
                    is.response("LSUB () \"/\" " + dirlist[i]);
                }
                is.response(sid, true, "LSUB completed");
                return 0;
            }
        }
        is.response(sid, false, "LSUB failed");
        return 1;
    }

}
