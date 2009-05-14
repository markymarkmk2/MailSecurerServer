/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.hibernate.Mandant;

/**
 *
 * @author mw
 */
public class MContext
{

    private Preferences prefs;
    private Mandant mandant;

    public MContext( Preferences _prefs, Mandant _mandant)
    {
        prefs = _prefs;
        mandant = _mandant;
    }

    /**
     * @return the prefs
     */
    public Preferences getPrefs()
    {
        return prefs;
    }

    /**
     * @return the mandant
     */
    public Mandant getMandant()
    {
        return mandant;
    }
}
