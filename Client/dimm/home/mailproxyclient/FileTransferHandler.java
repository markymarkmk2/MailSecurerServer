package dimm.home.mailproxyclient;

/*
 * FileWriter.java
 *
 * Created on 20. Oktober 2007, 15:50
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */



import java.io.File;
import java.io.FileInputStream;
import javax.swing.JFileChooser;

/**
 *
 * @author Administrator
 */
public class FileTransferHandler
{
    MainFrame parent;
    static File last_dir;
    
    private static final int WF_NAME_LEN = 1024;
    
    /** Creates a new instance of FileWriter */
    public FileTransferHandler(MainFrame _parent)
    {
        parent = _parent;
        last_dir = null;
    }
    
    public boolean do_action()
    {
        if (!(parent.get_comm() instanceof TCP_Communicator))
            return false;
        
        TCP_Communicator comm = (TCP_Communicator) parent.get_comm();
        
        JFileChooser jf = new JFileChooser();
        jf.setDialogType(JFileChooser.SAVE_DIALOG);
        jf.setMultiSelectionEnabled( true );
        if (last_dir != null)
            jf.setCurrentDirectory( last_dir );
        
        jf.showOpenDialog( parent );
        
        File[] f = jf.getSelectedFiles();
        if (f == null || f.length == 0)
            return false;

        last_dir = jf.getSelectedFile().getParentFile();
        
        
        TargetPathDlg dlg = new TargetPathDlg( parent );
        dlg.setLocation( parent.getLocationOnScreen().x + 20, parent.getLocationOnScreen().y + 50 );
        dlg.setVisible( true );
    
        if (!dlg.is_okay())
            return false;

        String trg_path = dlg.get_targ_path();
        
        for (int i = 0; i < f.length; i++)
        {
            long flen = f[i].length();
        
            // USER CANNOT ENTER MORE THAN 1024 BYTE, WE DO NOT TEST
            String cmd = "WRITEFILE PATH:'" + trg_path +  "/" + f[i].getName() + "'";
            byte[] cmd_data = cmd.getBytes();
            byte[] data = new byte[WF_NAME_LEN];
            for (int j = 0; j < cmd_data.length; j++)
            {
                data[j] = cmd_data[j];
            }
            for (int k = cmd_data.length; k <data.length; k++)
            {
                data[k] = ' ';
            }
            
            
            cmd = new String(data);
           
            byte[] file_data = new byte[(int)flen];
            try
            {
                FileInputStream fis = new FileInputStream( f[i] );            
                fis.read(file_data);

                fis.close();
                
                if (!comm.send_tcp_byteblock(cmd, file_data))            
                {
                    parent.errm_ok("Die Datei " + f[i].getName() + " konnte nicht geschrieben werden " + comm.get_answer_err_text() );
                    return false;
                }
            }
            catch (Exception exc )
            {
                parent.errm_ok("Daten konnten nicht gelesen werden: " + exc.getMessage() );
                return false;
            }            
        }  
        
        if (f.length == 1)
            parent.errm_ok("Eine Datei wurd erfolgreich uebertragen");
        else
            parent.errm_ok( f.length + " Dateien wurden erfolgreich uebertragen");
        
        return true;
    }    
}
