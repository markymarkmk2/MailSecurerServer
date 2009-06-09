/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import dimm.home.hibernate.DiskArchive;
import dimm.home.mail.RFCFileMail;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.MandantContext;

/**
 *
 * @author mw
 */
public interface Vault
{
    boolean archive_mail( RFCFileMail msg, MandantContext mandant, DiskArchive diskArchive ) throws ArchiveMsgException;
}
