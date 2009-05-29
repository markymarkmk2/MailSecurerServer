/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hibernate.DiskArchive;
import dimm.home.hibernate.DiskSpace;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import java.util.Iterator;
import java.util.Set;
import javax.mail.Message;

/**
 *
 * @author mw
 */
public class DiskVault
{

    public static final int DS_FULL = 0x0001;
    public static final int DS_ERROR = 0x0002;
    public static final int DS_OFFLINE = 0x0004;

    DiskArchive disk_archive;


    DiskVault( DiskArchive da )
    {
        disk_archive = da;
    }

    private boolean test_flag( DiskSpace ds, int flag )
    {
        return ((ds.getFlags() & flag) == flag);
    }

    DiskSpace get_next_active_diskspace(int index )
    {
        Set<DiskSpace> dss = disk_archive.getDiskSpaces();

        Iterator<DiskSpace> it = dss.iterator();

        while (it.hasNext())
        {
            DiskSpace ds = it.next();
            if (test_flag( ds, DS_FULL) || test_flag(ds, DS_ERROR) || test_flag(ds, DS_OFFLINE))
                continue;

            if (index > 0)
            {
                index--;
                continue;
            }

            // GOT IT
            return ds;
        }

        // NADA
        return null;
    }

    void archive_mail( Message msg ) throws ArchiveMsgException
    {
        int ds_idx = 0;

        DiskSpace ds = get_next_active_diskspace( ds_idx );

        if (ds == null)
        {
            throw new ArchiveMsgException("No Diskspace found" );
        }

    }
}
