/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.importmail;

import home.shared.mail.RFCFileMail;
import dimm.home.mailarchiv.Exceptions.IndexException;
import home.shared.hibernate.Hotfolder;
import home.shared.hibernate.HotfolderExclude;
import home.shared.mail.RFCMimeMail;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.ImportException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.StatusEntry;
import dimm.home.mailarchiv.StatusHandler;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ZipUtilities;
import dimm.home.mailarchiv.WorkerParentChild;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import javax.mail.Message;
import javax.mail.MessagingException;

class HFFilenameFilter implements FilenameFilter
{

    Hotfolder hfolder;

    HFFilenameFilter( Hotfolder _hfolder )
    {
        hfolder = _hfolder;
    }

    @Override
    public boolean accept( File dir, String name )
    {
        boolean ret = true;

        Set<HotfolderExclude> excludes = hfolder.getHotfolderExcludes();
        Iterator<HotfolderExclude> it = excludes.iterator();

        while (it.hasNext())
        {
            HotfolderExclude ex = it.next();
            ret = handle_check_accept(dir, name, ex);

            // THE FIRST CONDITION THAT GIVES NOK STOPS
            if (ret == false)
            {
                break;
            }
        }

        return ret;
    }

    private boolean handle_check_accept( File dir, String name, HotfolderExclude ex )
    {
        boolean ret = false;
        int name_mode = ex.getFlags() & HotFolderImport.HFE_NAME_MASK;
        int flags = ex.getFlags() & HotFolderImport.HFE_FLAG_MASK;
        boolean ign_case = ((flags & HotFolderImport.HFE_IGN_CASE) == HotFolderImport.HFE_IGN_CASE);
        boolean is_dir = ((flags & HotFolderImport.HFE_IS_DIR) == HotFolderImport.HFE_IS_DIR);
        boolean negate = ((flags & HotFolderImport.HFE_NEGATE) == HotFolderImport.HFE_NEGATE);

        switch (name_mode)
        {
            case HotFolderImport.HFE_BEGINS_WITH:
            {
                ret = name.regionMatches(ign_case, 0, ex.getName(), 0, ex.getName().length());
                break;
            }
            case HotFolderImport.HFE_ENDS_WITH:
            {
                if (ex.getName().length() <= name.length())
                {
                    ret = name.regionMatches(ign_case, name.length() - ex.getName().length(), ex.getName(), 0, ex.getName().length());
                }
                break;
            }
            case HotFolderImport.HFE_CONTAINS:
            {
                if (!ign_case)
                {
                    if (name.indexOf(ex.getName()) >= 0)
                    {
                        ret = true;
                    }
                }
                else
                {
                    if (name.toLowerCase().indexOf(ex.getName().toLowerCase()) >= 0)
                    {
                        ret = true;
                    }
                }
            }
            case HotFolderImport.HFE_EQUALS:
            {
                if (!ign_case)
                {
                    ret = name.equals(ex.getName());
                }
                else
                {
                    ret = name.equalsIgnoreCase(ex.getName());
                }
            }
        }

        // CHECK FILE MODE, THIS OVERRIDES ANY FOUND NAME MATCHES
        File f = new File(dir, name);
        if (!is_dir && f.isDirectory())
        {
            ret = false;
        }

        // NEGATE OVERRIDES EVERYTHING
        if (negate)
        {
            ret = !ret;
        }

        return ret;
    }
}





class DirectoryEntry
{
    private File file;
    private ArrayList<DirectoryEntry> children;

    DirectoryEntry( File f )
    {
        file = f;
        if (file.isDirectory())
        {
            File[] list = file.listFiles();

            for (int i = 0; i < list.length; i++)
            {
                children.add( new DirectoryEntry( list[i] ) );
            }
        }
    }

    void delete_recursive()
    {
        for (int i = 0; i < children.size(); i++)
        {
            DirectoryEntry directoryEntry = children.get(i);
            if (directoryEntry.getFile().isDirectory())
            {
                directoryEntry.delete_recursive();

            }
            else
            {
                directoryEntry.getFile().delete();
            }
        }
        file.delete();
    }
    boolean is_unchanged(DirectoryEntry de)
    {
        // COMPARE NAMES
        if (file.getAbsolutePath().compareTo( de.getFile().getAbsolutePath()) != 0)
            return false;
        
        // COMPARE STAMP
        if (file.lastModified() != de.getFile().lastModified())
            return false;
        
        // COMPARE SIZE
        if (file.length() != de.getFile().length())
            return false;
        
        // TRY TO OPEN REG FILES
        if (file.isFile())
        {
            RandomAccessFile raf = null;
            try
            {
                raf = new RandomAccessFile(file, "r");
                if (file.length() > 0)
                {
                    raf.seek( file.length() - 1);
                    raf.read();
                }
                raf.close();
            }
            catch (IOException ex)
            {
                // NOT COMPLETE, WE FAIL
                return false;
            }            
            finally
            {
                if (raf != null)
                {
                    try
                    {
                        raf.close();
                    }
                    catch (IOException ex)
                    {                        
                    }
                }
            }
        }

        if (children != null && de.getChildren() == null)
            return false;
        if (children == null && de.getChildren() != null)
            return false;
        
        // COMPARE CHILDREN COUNT
        if (children != null)
        {
            if ( children.size() != de.getChildren().size())
            return false;
        
            // RECURSIVELY CHECK CHILDREN
            for (int i = 0; i < children.size(); i++)
            {
                DirectoryEntry directoryEntry = children.get(i);
                if (!directoryEntry.is_unchanged( de.getChildren().get(i) ))
                    return false;
            }
        }
        return true;
    }

    /**
     * @return the file
     */
    public File getFile()
    {
        return file;
    }

    /**
     * @return the children
     */
    public ArrayList<DirectoryEntry> getChildren()
    {
        return children;
    }
}
/**
 *
 * @author mw
 */
public class HotFolderImport implements StatusHandler, WorkerParentChild
{

    final Hotfolder hfolder;
    // TODO: PUT TO DB
    public static final int HF_SLEEP_SECS = 10;

    public static final int HFE_NAME_MASK = 0x000F;
    public static final int HFE_BEGINS_WITH = 0x0001;
    public static final int HFE_ENDS_WITH = 0x0002;
    public static final int HFE_EQUALS = 0x0003;
    public static final int HFE_CONTAINS = 0x0004;
    public static final int HFE_FLAG_MASK = 0xFFF0;
    public static final int HFE_IS_DIR = 0x0010;
    public static final int HFE_NEGATE = 0x0020;
    public static final int HFE_IGN_CASE = 0x0040;

    public static final int HF_FLAG_ZIP_FILES = 0x0001;


    public HotFolderImport( Hotfolder _hf )
    {
        hfolder = _hf;
    }
    boolean do_finish;

    @Override
    public void finish()
    {
        do_finish = true;
    }

    @Override
    public void run_loop()
    {

        ArrayList<DirectoryEntry> last_entry_list = new ArrayList<DirectoryEntry>();

        while (!do_finish)
        {
            try
            {
                status.set_status(StatusEntry.SLEEPING, Main.Txt("sleeping") );

                Thread.sleep(HF_SLEEP_SECS * 1000);
            }
            catch (InterruptedException ex)
            {
            }

            try
            {
                status.set_status(StatusEntry.BUSY, Main.Txt("searching") );

                // LIST FILES IN DIR
                FilenameFilter filter = new HFFilenameFilter(hfolder);

                File path = new File(hfolder.getPath());

                if (!path.exists())
                {
                    throw new ImportException( Main.Txt("Hotfolder_path_does_not_exist")  + ": " + path.getAbsolutePath() );
                }
                
                File[] flist = path.listFiles(filter);
                
                ArrayList<DirectoryEntry> entry_list = new ArrayList<DirectoryEntry>();

                // FOR EACH ENTRY CREATE RECURSIVE DIRECTRORYENTRY AND COMPARE WITH EARLIER RESULT
                if (flist.length > 0)
                {
                    for (int i = 0; i < flist.length; i++)
                    {
                        // CREAT ERECURSIVE ENTRY
                        DirectoryEntry entry = new  DirectoryEntry(  flist[i] );
                        entry_list.add( entry );

                        // LOOK FOR ENTRY IN PREVIOUS RESULT
                        for (int j = 0; j < last_entry_list.size(); j++)
                        {
                            DirectoryEntry last_entry = entry_list.get(j);

                            // SAME PATH?
                            if (last_entry.getFile().getAbsolutePath().equals(flist[i].getAbsolutePath()))
                            {
                                // CHECK IF ENTRIES DIFFER (LIKE COPY IS IN PROGRESS, FILECOUNT CHANGES ETC.)
                                if (entry.is_unchanged(last_entry))
                                {
                                    // FOUND AN ENTRY WHICH HASNT CHANGED BETWEEN LOOP CALLS
                                    status.set_status(StatusEntry.BUSY, Main.Txt("processing") + " " + entry.getFile().getName() );

                                    handle_hotfolder_entry( entry );
                                }
                            }
                        }
                    }
                }

                // LATCH RESULTS FOR NEXT COMPARISON
                last_entry_list = entry_list;
            }
            catch (Exception e)
            {
                status.set_status(StatusEntry.ERROR, Main.Txt("error") + " " + e.getMessage() );
            }
        }
    }

    @Override
    public void idle_check()
    {
        synchronized (hfolder)
        {
        }
    }

    private void handle_hotfolder_entry( DirectoryEntry entry ) throws ArchiveMsgException, VaultException, IndexException
    {
        File arch_file = entry.getFile();
        File zipfile = null;

        int flags = Integer.parseInt(hfolder.getFlags());

        if (entry.getFile().isDirectory() || (flags & HF_FLAG_ZIP_FILES) == HF_FLAG_ZIP_FILES)
        {
            String name = entry.getFile().getAbsolutePath();
            // FIND UNIQUE ZIPNAME BASED ON NAME
            while (true)
            {
                zipfile = new File( name + ".zip" );
                if (!zipfile.exists())
                    break;
                name += "_";

                ZipUtilities zu = new ZipUtilities();
                zu.zip(entry.getFile().getAbsolutePath(), zipfile.getAbsolutePath());
                arch_file = zipfile;
            }
        }
        
        if (handle_hotfolder_file( arch_file ))
        {
            arch_file.delete();

            // NON-ZIP FILE EXISTS
            if (entry.getFile().isFile() && entry.getFile().exists())
                entry.getFile().delete();

            if (entry.getFile().isDirectory())
                entry.delete_recursive();

        }
    }

    private boolean handle_hotfolder_file( File arch_file ) throws ArchiveMsgException, VaultException, IndexException
    {
        try
        {
            // Create a mail session
            RFCMimeMail mm = new RFCMimeMail();
            mm.create(hfolder.getUsermailadress(), hfolder.getUsermailadress(), Main.Txt("Hotfolder_") + arch_file.getName(),
                        Main.Txt("This_mail_was_created_by_an_archive_hotfolder"), arch_file);
        
            Message m = mm.getMsg();

            RFCFileMail mail = Main.get_control().create_import_filemail_from_eml(hfolder.getMandant(), m, "hf_imp");

            Main.get_control().add_mail_file(mail, hfolder.getMandant(), hfolder.getDiskArchive(), /*bg*/true, /*delete_afterwards*/true);
           
            return true;
        }
        catch (MessagingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }

        // TODO: QUARANTINE FOR FAILED OBJECTS
        return false;
        
    }

    @Override
    public String get_status_txt()
    {
        return status.get_status_txt();
    }

    @Override
    public int get_status_code()
    {
        return status.get_status_code();
    }


}
