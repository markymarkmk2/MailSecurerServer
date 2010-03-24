/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import home.shared.mail.RFCGenericMail;
import home.shared.mail.RFCGenericMail.FILENAME_MODE;

/**
 *
 * @author mw
 */
public class DiskSpaceInfo
{
    long capacity;
    long firstEntryTS;
    long lastEntryTS;
    String language;
    private int encMode;
    RFCGenericMail.FILENAME_MODE fmode;



    public DiskSpaceInfo()
    {
        capacity = 0;
        firstEntryTS = 0;
        lastEntryTS = 0;
        language = "de";
        encMode = RFCGenericMail.ENC_NONE;
        fmode = RFCGenericMail.FILENAME_MODE.HMS_FILE;
    }

    
    /**
     * @return the capacity
     */
    public long getCapacity()
    {
        return capacity;
    }

    /**
     * @param capacity the capacity to set
     */
    public void setCapacity( long capacity )
    {
        this.capacity = capacity;
    }
    public void incCapacity( long capacity )
    {
        this.capacity += capacity;
    }
    public void decCapacity( long capacity )
    {
        this.capacity -= capacity;
    }

    /**
     * @return the firstEntryTS
     */
    public long getFirstEntryTS()
    {
        return firstEntryTS;
    }

    /**
     * @param firstEntryTS the firstEntryTS to set
     */
    public void setFirstEntryTS( long firstEntryTS )
    {
        this.firstEntryTS = firstEntryTS;
    }

    /**
     * @return the lastEntryTS
     */
    public long getLastEntryTS()
    {
        return lastEntryTS;
    }

    /**
     * @param lastEntryTS the lastEntryTS to set
     */
    public void setLastEntryTS( long lastEntryTS )
    {
        this.lastEntryTS = lastEntryTS;
    }

    /**
     * @return the language
     */
    public String getLanguage()
    {
        return language;
    }

    /**
     * @param language the language to set
     */
    public void setLanguage( String language )
    {
        this.language = language;
    }

    /**
     * @return the encMode
     */
    public int getEncMode()
    {
        return encMode;
    }

    /**
     * @param encMode the encMode to set
     */
    public void setEncMode( int encMode )
    {
        this.encMode = encMode;
    }

    
    public FILENAME_MODE getFmode()
    {
        return fmode;
    }

    public void setFmode( FILENAME_MODE fmode )
    {
        this.fmode = fmode;
    }
}