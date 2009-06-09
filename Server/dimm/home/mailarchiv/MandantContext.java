/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.hibernate.DiskArchive;
import dimm.home.hibernate.Mandant;
import dimm.home.vault.DiskVault;
import dimm.home.vault.Vault;
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

}
