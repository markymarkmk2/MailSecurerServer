/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Commands;

import com.thoughtworks.xstream.XStream;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.File;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.SQL.RMXFile;
import java.io.IOException;
import javax.swing.filechooser.FileSystemView;

/**
 *
 * @author Administrator
 */
public class FileSystemViewCommand extends AbstractCommand
{
    static File toFile( String s )
    {
        XStream xs = new XStream();
        RMXFile f = (RMXFile)xs.fromXML( s );
        return new File( f.getAbsolutePath() );
    }

    static String fromFile( File f )
    {
        RMXFile real_f = new RMXFile(f);
        XStream xs = new XStream();
        return xs.toXML(real_f);
    }
    static String fromFileArray( File[] f )
    {
        RMXFile[] real_f = new RMXFile[f.length];
        for (int i = 0; i < f.length; i++)
        {
            File file = f[i];
            real_f[i] = new RMXFile(file);
        }
        XStream xs = new XStream();
        return xs.toXML(real_f);
    }


    
    /** Creates a new instance of HelloCommand */
    public FileSystemViewCommand()
    {
        super("FSV");
    }
    @Override
    public boolean do_command(String data)
    {
        FileSystemView fsv = FileSystemView.getFileSystemView();

        answer = "";
        ParseToken pt = new ParseToken(data);

        String file_str = pt.GetString("FL:");        
        String path_arg = pt.GetString("PA:");        
        String cmd = pt.GetString("CMD:");
        LogManager.debug_msg(8, "FSV In : " + data);

        
        try
        {
            File file_arg = null;
            if (file_str != null && file_str.length() > 0)
                file_arg = toFile(file_str);


            if (cmd.compareTo("createNewFolder") == 0)
            {
                boolean ret = file_arg.mkdir();
                if (!ret)
                    answer = "1: failed";
                else
                    answer = "0: " + fromFile(file_arg);
            }
            else if (cmd.compareTo("createFileObject") == 0)
            {
                File ret = fsv.createFileObject(path_arg);
                answer = "0: " + fromFile(ret);
            }
            else if (cmd.compareTo("getDefaultDirectory") == 0)
            {
                File ret = fsv.getDefaultDirectory();
                answer = "0: " + fromFile(ret);
            }
            else if (cmd.compareTo("getFiles") == 0)
            {
                boolean ufh = pt.GetBoolean("UF:");
                File[] ret = fsv.getFiles( file_arg, ufh);

                answer = "0: " + fromFileArray(ret);
            }
            else if (cmd.compareTo("getRoot") == 0)
            {
                File[] arr = File.listRoots();
                String fpath = file_arg.getAbsolutePath();
                File ret = null;
                for (int i = 0; i < arr.length; i++)
                {
                    File file = arr[i];
                    if (fpath.startsWith(file.getAbsolutePath()) )
                    {
                            ret = file;
                            break;
                    }
                }
                answer = "0: " + fromFile(ret);
            }
            else if (cmd.compareTo("getRoots") == 0)
            {
                File[] ret = File.listRoots(); //fsv.getRoots();
                answer = "0: " + fromFileArray(ret);
            }
            else if (cmd.compareTo("getSystemDisplayName") == 0)
            {
                String ret = fsv.getSystemDisplayName(file_arg);
                answer = "0: " + ret;
            }
            else if (cmd.compareTo("getParentDirectory") == 0)
            {
                File ret = file_arg.getParentFile();
                if (ret == null)
                    ret = file_arg;
                //File ret = fsv.getParentDirectory(file_arg);
                answer = "0: " + fromFile(ret);
            }
            else if (cmd.compareTo("isFileSystem") == 0)
            {
                boolean ret = fsv.isFileSystem(file_arg);
                answer = "0: " + (ret ? "1" : "0");
            }
            else if (cmd.compareTo("isFileSystemRoot") == 0)
            {
                boolean ret = fsv.isFileSystemRoot(file_arg);
                answer = "0: " + (ret ? "1" : "0");
            }
            else if (cmd.compareTo("isParent") == 0)
            {
                File folder_arg = toFile(pt.GetString("FO:"));
                boolean ret = fsv.isParent(folder_arg, file_arg);
                answer = "0: " + (ret ? "1" : "0");
            }
            else if (cmd.compareTo("isRoot") == 0)
            {
                boolean ret = fsv.isRoot(file_arg);
                answer = "0: " + (ret ? "1" : "0");
            }
            else if (cmd.compareTo("isTraversable") == 0)
            {
                boolean ret = fsv.isTraversable(file_arg);
                answer = "0: " + (ret ? "1" : "0");
            }
            else
                throw new IOException("Invalid command " + cmd );
        }
        catch (Exception iOException)
        {
            answer = "1: " + iOException.getMessage();
            return true;
        }
        LogManager.debug_msg(8, "FSV Out: " + answer);

        return true;
    }
    
  
}
