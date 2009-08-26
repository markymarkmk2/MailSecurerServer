/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mw
 */
public class TempFileHandler
{
    public static final String IMPMAIL_PREFIX = "mailimp";

    MandantContext ctx;

    ArrayList<File> delete_list;

    public TempFileHandler( MandantContext ctx )
    {
        this.ctx = ctx;
        delete_list = new ArrayList<File>();
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
        String name = ctx.get_tmp_path() + "/" + IMPMAIL_PREFIX + "_" + ip + "_" + System.currentTimeMillis() + "." + suffix;
        return name;
    }

    public String get_ip_mail_path( File f, int n )
    {
        try
        {
            StringTokenizer sto = new StringTokenizer(f.getName(), "_");

            while (n > 0)
            {
                sto.nextToken();
                n--;
            }
            return sto.nextToken();
        }
        catch (Exception e)
        {
        }
        return null;
    }
    public String get_ip_from_mail_path( File f )
    {
        return get_ip_mail_path(f, 1);
    }
    public long get_time_from_mail_path( File f )
    {
        try
        {
            return Long.parseLong(get_ip_mail_path(f, 2));
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return 0;
    }

    public File create_new_import_file( String name )
    {
        // MOVE TO IMPORT PATH
        String new_path = ctx.get_tmp_path() + Main.IMPORTRELPATH + name;
        File nf = new File(new_path);

        // VERY UNLIKELY THERE IS ALREADY A FILE WITH SAME NAME...
        int i = 10;
        while (nf.exists() && i > 0)
        {
            new_path += Long.toString(System.currentTimeMillis() % 10000);
            nf = new File(new_path);
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
                file.delete();
            }
            catch (Exception e)
            {
            }
        }
    }

    public File create_temp_file(String subdir, String prefix, String suffix) throws ArchiveMsgException
    {
        File tmp_file = null;
        File directory = null;
        directory = ctx.get_tmp_path();

        try
        {
            if (suffix != null && suffix.length() > 0)
            {
                directory = new File( directory.getAbsolutePath() + suffix + "/");
                directory.mkdir();
            }
            tmp_file = File.createTempFile(prefix, suffix, directory);
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (tmp_file == null)
        {
            try
            {
                tmp_file = File.createTempFile(prefix, suffix, null);
            }
            catch (IOException ex)
            {
                Logger.getLogger(LogicControl.class.getName()).log(Level.SEVERE, null, ex);
                throw new ArchiveMsgException("Cannot create temp file: " + ex.getMessage());
            }
        }

        // GET RID OF FILE ON EXIT OF JVM
        tmp_file.deleteOnExit();
        delete_list.add(tmp_file);

        return tmp_file;
    }

    public File writeTemp( String subdir, String prefix, String suffix, InputStream is ) throws IOException, ArchiveMsgException
    {
        File file = ctx.getTempFileHandler().create_temp_file("Extract", "ex", "tmp");

        OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        BufferedInputStream bis = new BufferedInputStream(is);
        int c;
        while ((c = bis.read()) != -1)
        {
            os.write(c);
        }
        os.close();
        return file;
    }


}
