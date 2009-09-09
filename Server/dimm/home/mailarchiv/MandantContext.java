/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.serverconnect.TCPCallConnect;
import dimm.home.index.IndexManager;
import home.shared.hibernate.DiskArchive;
import home.shared.hibernate.Mandant;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author mw
 */
public class MandantContext
{
    private MandantPreferences prefs;
    private Mandant mandant;
    private ArrayList<Vault> vaultArray;
    private TempFileHandler tempFileHandler;
    private TCPCallConnect tcp_conn;
    private IndexManager index_manager;
    
    
    

    public MandantContext(  MandantPreferences _prefs, Mandant _m )
    {
        prefs = _prefs;
        mandant = _m;
        vaultArray = new ArrayList<Vault>();
        tempFileHandler = new TempFileHandler( this );
    }

    /**
     * @return the mandant
     */
    public Mandant getMandant()
    {
        return mandant;
    }

    public TempFileHandler getTempFileHandler()
    {
        return tempFileHandler;
    }
    

    public DiskArchive get_da_by_id( long id )
    {
        Iterator <DiskArchive> it = getMandant().getDiskArchives().iterator();

        while( it.hasNext() )
        {
            DiskArchive da =  it.next();
            if (da.getId() == id)
                return da;
        }
        return null;
    }
    public DiskVault get_vault_by_da_id( long id )
    {
        getVaultArray().iterator();

        for (int i = 0; i < vaultArray.size(); i++)
        {
            Vault vault = vaultArray.get(i);
            if (vault instanceof DiskVault)
            {
                DiskVault dv = (DiskVault)vault;
                if (dv.get_da().getId() == id)
                    return dv;
            }
        }
        return null;
    }
    
    public void build_vault_list()
    {
        Iterator <DiskArchive> it = getMandant().getDiskArchives().iterator();

        while( it.hasNext() )
        {
            DiskArchive da =  it.next();
            getVaultArray().add( new DiskVault( this, da ));
        }
    }

    /**
     * @return the vaultArray
     */
    public ArrayList<Vault> getVaultArray()
    {
        return vaultArray;
    }

    public MandantPreferences getPrefs()
    {
        return prefs;
    }

    void build_mandant_struct()
    {

    }
    public static boolean has_trail_slash( String path)
    {
        int len = path.length();
        if (len > 0)
        {
            char lc = path.charAt(len - 1);
            if (lc == '/' || lc == '\\')
                return true;
        }
        return false;
    }
    public static String add_trail_slash( String path)
    {
        if (has_trail_slash(path))
            return path;

        return path + "/";
    }
    public static String del_trail_slash( String path)
    {
        if (!has_trail_slash(path))
            return path;

        return path.substring(0, path.length() - 1);
    }

    public File get_tmp_path()
    {
        String path = prefs.get_prop(MandantPreferences.TEMPFILEDIR, Main.get_prop(GeneralPreferences.TEMPFILEDIR, Main.TEMP_PATH));

        path = add_trail_slash(path);

        File tmp_path = new File( path + mandant.getId() );

        if (tmp_path.exists() == false)
            tmp_path.mkdirs();

        return tmp_path;
    }

    void set_tcp_conn( TCPCallConnect _tcp_conn )
    {
        tcp_conn = _tcp_conn;
    }

    public TCPCallConnect get_tcp_call_connect()
    {
        return tcp_conn;
    }

    void set_index_manager( IndexManager idx_util )
    {
        index_manager = idx_util;
        
    
    }
    public IndexManager get_index_manager()
    {
        return index_manager;
    }

    public void flush_index()
    {
        for (int i = 0; i < vaultArray.size(); i++)
        {
            Vault vault = vaultArray.get(i);
            vault.flush();
        }        
    }




}
