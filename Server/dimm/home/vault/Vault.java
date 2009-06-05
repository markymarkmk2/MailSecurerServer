/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hibernate.DiskArchive;
import dimm.home.hibernate.Mandant;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import java.io.File;

/**
 *
 * @author mw
 */
public interface Vault
{
    boolean archive_mail( File msg, Mandant mandant, DiskArchive diskArchive ) throws ArchiveMsgException;
}
