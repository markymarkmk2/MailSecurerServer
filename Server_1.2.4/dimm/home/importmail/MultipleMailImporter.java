/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 *
 * @author mw
 */
public interface MultipleMailImporter
{


    public File get_msg_file();

    public Message get_message( int idx ) throws ExtractionException, MessagingException, IOException;

    int get_message_count();

    void open() throws ExtractionException, FileNotFoundException, IOException;

    void close() throws IOException;

    public void delete();

}
