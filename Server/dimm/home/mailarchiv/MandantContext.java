/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

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

    public MandantContext(  MandantPreferences _prefs, Mandant _m )
    {
        prefs = _prefs;
        mandant = _m;
        vaultArray = new ArrayList<Vault>();
    }

    /**
     * @return the mandant
     */
    public Mandant getMandant()
    {
        return mandant;
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
    public File get_tmp_path()
    {
        File tmp_path = new File( Main.TEMP_PATH + mandant.getId() );
        if (tmp_path.exists() == false)
            tmp_path.mkdirs();

        return tmp_path;
    }





}
