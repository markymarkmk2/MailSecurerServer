/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import home.shared.hibernate.DiskArchive;
import dimm.home.mail.RFCGenericMail;
import dimm.home.mailarchiv.Exceptions.ArchiveMsgException;
import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.MandantContext;

/**
 *
 * @author mw
 */
public interface Vault
{
    boolean archive_mail( RFCGenericMail msg, MandantContext mandant, DiskArchive diskArchive ) throws ArchiveMsgException, VaultException, IndexException;

    void flush();
    void close() throws VaultException;

    public String get_password();
}
