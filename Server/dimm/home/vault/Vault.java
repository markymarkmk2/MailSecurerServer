/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.vault;

import home.shared.hibernate.DiskArchive;
import home.shared.mail.RFCGenericMail;
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
    boolean archive_mail( RFCGenericMail msg, MandantContext mandant, DiskArchive diskArchive, boolean index_background ) throws ArchiveMsgException, VaultException, IndexException;

    void flush() throws IndexException, VaultException;
    void close() throws VaultException;

    public String get_password();
    public String get_name();

    public boolean has_sufficient_space();

    public boolean is_in_rebuild();
}
