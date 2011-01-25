/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.Utilities.LogManager;
import home.shared.CS_Constants;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 *
 * @author mw
 */
public class TempFileHandler
{
    public static final String IMPORT_PREFIX = "import";
    public static final String IMPMAIL_PREFIX = "mailimp";
    public static final String QUARANTINE_PREFIX = "quarantine";
    public static final String HOLD_PREFIX = "hold";
    public static final String WORK_PREFIX = "work";
    public static final String INDEX_BUFFER_PREFIX = "indexbuffer";

    MandantContext ctx;

    ArrayList<File> delete_list;

    public TempFileHandler( MandantContext ctx )
    {
        this.ctx = ctx;
        delete_list = new ArrayList<File>();

        delete_work_files();


        Thread thr = new Thread()
        {

            @Override
            public void run()
            {
                clean_up();
            }

        };
        Runtime r = Runtime.getRuntime();
        r.addShutdownHook(thr);

    }

    public void check_space( long size ) throws VaultException
    {
        File tmp_dir =  ctx.get_tmp_path();
        if (!tmp_dir.exists())
        {
            throw new VaultException( Main.Txt("temp_filesystem_does_not_exist:") + " " + tmp_dir.getAbsolutePath() );
        }

        long free_space = tmp_dir.getFreeSpace();
        if ( free_space - size < Main.MIN_FREE_SPACE)
        {
            throw new VaultException( Main.Txt("not_enough_space_left_on_temp_filesystem:") + " " + tmp_dir.getAbsolutePath() );
        }
    }
    

    public String create_imp_mail_path( String ip, String suffix )
    {
        String name = ctx.get_tmp_path() + "/" + IMPMAIL_PREFIX + "." + ip + "." + System.currentTimeMillis() + "." + suffix;
        return name;
    }

    private String get_ip_mail_path( File f, int n ) throws VaultException
    {
        try
        {
            StringTokenizer sto = new StringTokenizer(f.getName(), ".");

            while (n > 0)
            {
                sto.nextToken();
                n--;
            }
            return sto.nextToken();
        }
        catch (Exception e)
        {
            throw new VaultException( "Invalid ip_mail_path: " + f.getName() );
        }        
    }
    public String get_ip_from_mail_path( File f ) throws VaultException
    {
        return get_ip_mail_path(f, 1);
    }
    public long get_time_from_mail_path( File f ) throws VaultException
    {
        String tm = get_ip_mail_path(f, 2);
        try
        {
            return Long.parseLong(tm);
        }
        catch (NumberFormatException numberFormatException)
        {
            throw new VaultException( "Invalid time format in ip_mail_path: " + f.getName() );
        }
    }

    int get_da_id_from_import_file( String filename )
    {
        try
        {
            String[] s = filename.split("\\.");
            return Integer.parseInt(s[0]);
        }
        catch (Exception numberFormatException)
        {
            LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_IMPORT, "Invalid import filename " + filename, numberFormatException);
            return -1;
        }
    }
    public File create_new_import_file( String name,  int da_id )
    {
        // MOVE TO IMPORT PATH
        String new_path = get_clientimport_path() + "/" +  da_id + "." + name;

        File nf = new File(new_path);

        if (!nf.getParentFile().exists())
            nf.getParentFile().mkdirs();

        // VERY UNLIKELY THERE IS ALREADY A FILE WITH SAME NAME...
        int i = 100;
        while (nf.exists() && i > 0)
        {
            LogicControl.sleep(4);
            String rand = Long.toString(System.currentTimeMillis() % 10000);
            new_path = get_clientimport_path() + "/" +  da_id + "." + rand + "_" + name;
                        
            i--;
        }
        return nf;
    }
    public File create_new_mailimp_file( String name,  int da_id )
    {
        // MOVE TO IMPORT PATH
        String new_path = get_import_mail_path() + "/" +  da_id + "." + name;

        File nf = new File(new_path);

        if (!nf.getParentFile().exists())
            nf.getParentFile().mkdirs();

        // VERY UNLIKELY THERE IS ALREADY A FILE WITH SAME NAME...
        int i = 100;
        while (nf.exists() && i > 0)
        {
            LogicControl.sleep(4);
            String rand = Long.toString(System.currentTimeMillis() % 10000);
            new_path = get_import_mail_path() + "/" +  da_id + "." + rand + "_" + name;

            i--;
        }
        return nf;
    }

    
    public void clean_up()
    {
        for (int i = 0; i < delete_list.size(); i++)
        {
            File file = delete_list.get(i);
            try
            {
                if (file.exists())
                    file.delete();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        delete_work_files();
    }
    public File create_temp_file(String subdir, String prefix, String suffix) throws IOException
    {
        return create_temp_file(subdir, prefix, suffix, true);
    }
    public File create_file(String subdir, String prefix, String suffix) throws IOException
    {
        return create_temp_file(subdir, prefix, suffix, false);
    }

    public File create_temp_file(String subdir, String prefix, String suffix, boolean del_on_exit) throws IOException
    {
        File tmp_file = null;
        File directory = null;

        directory = ctx.get_tmp_path();

        // WE NEED 3 CHAR PREFIX
        if (prefix.length() < 3)
            prefix += "___".substring(prefix.length());

        // WE NEED VALID DOT-LEADING SUFFIX
        if (suffix == null || suffix.length() == 0)
            suffix = ".tmp";

        if (suffix.charAt(0) != '.')
            suffix = "." + suffix;

        try
        {
            if (subdir != null && subdir.length() > 0)
            {
                directory = new File( directory.getAbsolutePath() + "/" + subdir );
                if (!directory.exists())
                    directory.mkdir();
            }
            tmp_file = File.createTempFile(prefix, suffix, directory);
        }
        catch (IOException ex)
        {
            LogManager.msg_system(LogManager.LVL_DEBUG,  "create_temp_file failed", ex);
        }

        // IF THIS FAILS WE TRY TO USE DEFAULT TEMP DIR, THIS IS ACCEPTABLE FOR TMP FILES
        if (tmp_file == null)
        {
                tmp_file = File.createTempFile(prefix, suffix, null);
        }

        if (tmp_file.getParentFile().getFreeSpace() < Main.MIN_FREE_SPACE)
        {
            throw new IOException(Main.Txt( "No_disk_space_left_in_temp_dir") + ": " + tmp_file.getParent());
        }

        // GET RID OF FILE ON EXIT OF JVM
        if (del_on_exit)
        {
            //tmp_file.deleteOnExit();
            delete_list.add(tmp_file);
        }

        return tmp_file;
    }

    public File writeTemp( String subdir, String prefix, String suffix, InputStream is, boolean del_on_exit ) throws IOException, ArchiveMsgException
    {
        File file = create_temp_file(subdir, prefix, suffix, del_on_exit);

        OutputStream os = null;
        try
        {
            os = new BufferedOutputStream(new FileOutputStream(file));
            //InputStream bis = new BufferedInputStream(is);
            InputStream bis = is;
            byte[] buff = new byte[ CS_Constants.STREAM_BUFFER_LEN ];
            int rlen;
            while ((rlen = bis.read(buff)) != -1)
            {
                os.write(buff, 0, rlen);
            }
        }
        catch (IOException ex)
        {            
            throw new ArchiveMsgException("Cannot write to temp file: " + ex.getMessage());
        }
        finally
        {
            if (os != null)
                os.close();
        }

        
        
        return file;
    }

    public File get_quarantine_mail_path( )
    {
        File d = new File( ctx.get_tmp_path() , QUARANTINE_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }
    
    public File get_hold_mail_path( )
    {
        File d = new File( ctx.get_tmp_path() , HOLD_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }
    
    public File get_index_buffer_mail_path( )
    {
        File d = new File( ctx.get_tmp_path() , INDEX_BUFFER_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }

    // FOR NON-ENCODED FILE DATA (LIKE MBOXES, IMPORTED EML ETC.)
    public File get_import_mail_path( )
    {
        File d = new File( ctx.get_tmp_path() , IMPMAIL_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }

    // FOR ENCODED FILE DATA
    public File get_clientimport_path( )
    {
        File d = new File( ctx.get_tmp_path() , IMPORT_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }

    public void delete( File tmp_file )
    {
        for (int i = 0; i < delete_list.size(); i++)
        {
            File file = delete_list.get(i);
            if (file == tmp_file)
            {
                tmp_file.delete();
                delete_list.remove(tmp_file);
                return;
            }
        }
        tmp_file.delete();
    }

    public File get_work_path()
    {
        File d = new File( ctx.get_tmp_path() , WORK_PREFIX );
        if (!d.exists())
            d.mkdirs();

        return d;
    }

    private void delete_work_files()
    {
        try
        {
            File wrk_dir = get_work_path();
            File[] wrf_files = wrk_dir.listFiles();
            for (int i = 0; i < wrf_files.length; i++)
            {
                File file = wrf_files[i];
                file.delete();
            }
        }
        catch (Exception e)
        {
        }
    }


}
