/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Utilities;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Administrator
 */
public class ZipUtilities
{
    private static final int BUFFSIZE = 2048;
    int file_count;
    long total_size;
    boolean abort;

    public void do_abort()
    {
        abort = true;        
    }
    public boolean is_aborted()
    {
        return abort;
    }
    
    private void notify_listeners_state(int state, String status)
    {
        for (int j = 0; j < listeners.size(); j++)
        {
            ZipListener zipListener = listeners.get(j);
            zipListener.new_status(state, status);
        }       
    }
    
    private void notify_listeners_file(String source_file)
    {
        for (int j = 0; j < listeners.size(); j++)
        {
            ZipListener zipListener = listeners.get(j);
            zipListener.act_file_name(source_file);
        }
    }    

    private void notify_listeners_total_percent(int pc)
    {
        for (int j = 0; j < listeners.size(); j++)
        {
            ZipListener zipListener = listeners.get(j);
            zipListener.total_percent(pc);
        }
    }    
    private void notify_listeners_file_percent(int pc)
    {
        for (int j = 0; j < listeners.size(); j++)
        {
            ZipListener zipListener = listeners.get(j);
            zipListener.file_percent(pc);
        }
    }    
    
    ArrayList<ZipListener> listeners;
    

    public ZipUtilities()
    {
        listeners = new ArrayList<ZipListener>();
        file_count = 0;
        total_size = 0;        
    }
    public void addListener( ZipListener l )
    {
        listeners.add(l);
    }
    public void removeListener( ZipListener l )
    {
        listeners.remove(l);
    }
    
    public boolean zip(String path, String target_file)
    {
        return zip( path, target_file, null );
    }
    
    public boolean zip(String path, String target_file, String[] exclude_list)
    {
        try
        {
            //create a ZipOutputStream to zip the data to 
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target_file));

            File f = new File(path);
            if (f.isDirectory())
            {
                zipDir(f.getAbsolutePath(), f.getAbsolutePath(), zos, exclude_list);
            }
            else
            {
                zipFile(f.getParentFile().getAbsolutePath(), f.getPath(), zos);
            }

            //close the stream 
            zos.close();
        }
        catch (Exception e)
        {
            notify_listeners_state( ZipListener.ST_ERROR, e.getMessage());
            return false;
        }
        return true;
    }

    void zipDir(String start_path, String dir2zip, ZipOutputStream zos, String[] exclude_list) throws FileNotFoundException, IOException, Exception
    {
        //create a new File object based on the directory we have to zip File    
        File zipDir = new File(dir2zip);
        //get a listing of the directory content 
        String[] dirList = zipDir.list();

        //loop through dirList, and zip the files 
        for (int i = 0; i < dirList.length; i++)
        {
            File f = new File(zipDir, dirList[i]);
                    
            // HANDLE EXCLUDES
            if (exclude_list != null)
            {
                String rel_path = f.getAbsolutePath().substring(start_path.length() + 1);
                
                if (File.separatorChar != '/')
                    rel_path.replace(File.separatorChar, '/');
                
                int j = 0;            
                for (j = 0; j < exclude_list.length; j++)
                {
                    String string = exclude_list[j];
                    if (string.equals( rel_path))
                        break;
                }
                
                if (j != exclude_list.length)
                    continue;
            }
            
            if (f.isDirectory())
            {
                String filePath = f.getPath();
                zipDir(start_path, filePath, zos, exclude_list);
                //loop again 
                continue;
            }            
            if (abort)
            {
                throw new Exception("Aborted");
            }
            
            zipFile(start_path, f.getPath(), zos);
        }
    }

    void zipFile(String start_path, String file, ZipOutputStream zos) throws FileNotFoundException, IOException, Exception
    {
        byte[] readBuffer = new byte[BUFFSIZE];
        int bytesIn = 0;
        File f = new File(file);
        long file_size = f.length();
        
        // NOTIFY
        notify_listeners_file( f.getName());

        //create a FileInputStream on top of f 
        FileInputStream fis = new FileInputStream(f);
        //create a new zip entry 
        String e_name = f.getAbsolutePath().substring(start_path.length() + 1);
        
        // REPLACE BS WITH SLASH
        if (System.getProperty("os.name").startsWith("Win"))
        {
            e_name = e_name.replace('\\', '/');
        }
        
        ZipEntry anEntry = new ZipEntry(e_name);
        //place the zip entry in the ZipOutputStream object 
        anEntry.setTime( f.lastModified() );
        zos.putNextEntry(anEntry);
        
        
        int act_size = 0;
        while ((bytesIn = fis.read(readBuffer)) != -1)
        {
            if (abort)
            {
                throw new Exception("Aborted");
            }
            
            zos.write(readBuffer, 0, bytesIn);
            
            if (file_size > 0)
            {
                act_size += bytesIn;
                int percent = (int)(( act_size * 100) / file_size);
                notify_listeners_file_percent(percent);
            }
        }
        
        fis.close();
    }

    public final void copyInputStream(InputStream in, OutputStream out, long file_size)
            throws IOException, Exception
    {
        byte[] buffer = new byte[BUFFSIZE];
        int len;
        long act_file_size = 0;

        while ((len = in.read(buffer)) >= 0)
        {
            if (abort)
            {
                throw new Exception("Aborted");
            }
            out.write(buffer, 0, len);
            
            if (file_size > 0)
            {
                act_file_size += len;
                int percent = (int)((act_file_size * 100) / file_size);
                notify_listeners_file_percent(percent);
            }            
        }

        in.close();
        out.close();
    }

    public boolean unzip(String path, String source_file)
    {
        return unzip( path, source_file, null );
    }
    public boolean unzip(String path, String source_file, String select_file)
    {
        Enumeration entries;
        ZipFile zipFile;
        long act_total_size = 0;

        notify_listeners_state( ZipListener.ST_STARTED, "Unzipping " + source_file + " to " + path );

        try
        {
            zipFile = new ZipFile(source_file);

            entries = zipFile.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                
                // DE WE WANT TO EXTRACT A SINGLE FILE ?
                if (select_file != null)
                {
                    if (select_file.compareTo(entry.getName()) != 0)
                        continue;
                }
                
                File f = new File (entry.getName());
                

                // CREATE DIRS ON THE FLY
                File targ_path = null;
                if (path.equals("."))
                {
                    targ_path = new File( entry.getName() );
                }
                else
                {
                    targ_path = new File( path + File.separator + entry.getName() );
                }
                
                if (entry.isDirectory())
                {                                        
                    targ_path.mkdirs();
                    continue;
                }
                                
                // NOTIFY
                notify_listeners_file(entry.getName());
                

                // RELA PATH ?
                
                File parent_path = targ_path.getParentFile();
                if (!parent_path.exists())
                {
                    parent_path.mkdirs();
                }

                // COPY OUT DATA
                copyInputStream(zipFile.getInputStream(entry),
                        new BufferedOutputStream(new FileOutputStream(targ_path)),
                        entry.getSize());
                
                // NOTIFY
                if (total_size > 0)
                {
                    act_total_size += entry.getSize();
                    int percent = (int)((act_total_size * 100) / total_size);
                    notify_listeners_total_percent(percent);
                }
                
                // SET FILESTAT
                targ_path.setLastModified( entry.getTime() );
            }

            zipFile.close();
        }
        catch (Exception ioe)
        {            
            notify_listeners_state( ZipListener.ST_ERROR, ioe.getMessage());
            return false;
        }
        return true;
    }
    public boolean list( String source_file)
    {
        Enumeration entries;
        ZipFile zipFile;


        try
        {
            zipFile = new ZipFile(source_file);

            entries = zipFile.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                File f = new File (entry.getName());

                if (entry.isDirectory())
                {
                    
                    // This is not robust, just for demonstration purposes.
                    System.out.println("Dir:  " + f.getPath() );
                    continue;
                }
                System.out.println("File: " + f.getPath() );
            }

            zipFile.close();
        }
        catch (Exception ioe)
        {            
            notify_listeners_state( ZipListener.ST_ERROR, ioe.getMessage());            
            return false;
        }
        return true;
    }
    public boolean read_stat( String source_file)
    {
        Enumeration entries;
        ZipFile zipFile;

        file_count = 0;
        total_size = 0;

        try
        {
            zipFile = new ZipFile(source_file);

            entries = zipFile.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                File f = new File (entry.getName());

                if (entry.isDirectory())
                {
                    continue;
                }
                file_count++;
                total_size += entry.getSize();
            }

            zipFile.close();
        }
        catch (Exception ioe)
        {            
            file_count = 0;
            total_size = 0;
            notify_listeners_state( ZipListener.ST_ERROR, ioe.getMessage());
            return false;
        }
        return true;
    }
    
    public static void main(String[] args)
    {
        boolean  ret = false;

        ZipUtilities zu = new ZipUtilities();
        
        if (args[0].equals("-d"))
        {
            ret = zu.unzip( args[1], args[2] );
        }
        else if (args[0].equals("-l"))
        {
            ret = zu.list( args[1] );
        }
        else
        {
            ret = zu.zip( args[0], args[1] );
        }

        System.exit( ret == false ? 1 : 0 );

    }

}
