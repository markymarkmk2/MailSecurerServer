/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.exchange;

import com.microsoft.schemas.exchange.services._2006.messages.ExchangeServicePortType;
import com.microsoft.schemas.exchange.services._2006.types.ArrayOfRealItemsType;
import com.microsoft.schemas.exchange.services._2006.types.BaseFolderType;
import com.microsoft.schemas.exchange.services._2006.types.ConnectingSIDType;
import com.microsoft.schemas.exchange.services._2006.types.DistinguishedFolderIdNameType;
import com.microsoft.schemas.exchange.services._2006.types.DistinguishedFolderIdType;
import com.microsoft.schemas.exchange.services._2006.types.ExchangeImpersonationType;
import com.microsoft.schemas.exchange.services._2006.types.ExchangeVersionType;
import com.microsoft.schemas.exchange.services._2006.types.ItemIdType;
import com.microsoft.schemas.exchange.services._2006.types.ItemType;
import com.microsoft.schemas.exchange.services._2006.types.MessageType;
import home.shared.exchange.ExchangeAuthenticator;
import home.shared.exchange.dao.ItemTypeDAO;
import home.shared.exchange.util.ExchangeEnvironmentSettings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;


/**
 * Throw-away class for doing quick tests of features and classes during
 * development.
 *
 * @author Reid Miller
 */
public class ExchangeDevelopmentTest
{


    /**
     * Main method so we can quickly test things.
     * @param args
     */
    public static void main( String[] args )
    {        
        ItemTypeDAO itemTypeDAO;
        com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump=true;
        com.sun.xml.ws.transport.http.HttpAdapter.dump=true;
        //com.sun.xml.ws.transport.http.server.ServerAdapter.dump = true;


        try
        {
            ExchangeAuthenticator.reduce_ssl_security();
           
            ExchangeServicePortType port = ExchangeAuthenticator.open_exchange_port( "ServiceAccount", "12345", "w2k8becom.dimm.home", "192.168.1.121" );
            ExchangeEnvironmentSettings settings = new ExchangeEnvironmentSettings( ExchangeEnvironmentSettings.get_cultures()[0], ExchangeVersionType.EXCHANGE_2010 );

            // Test out the new ItemTypeDAO functionality.
            itemTypeDAO = new ItemTypeDAO(settings);

                        // SET IMPERSONATION
            ExchangeImpersonationType impersonation = new ExchangeImpersonationType();
            ConnectingSIDType cid = new ConnectingSIDType();
            cid.setPrincipalName("raddatz@w2k8becom.dimm.home");
            //cid.setSID("S-1-5-21-647177006-1055827929-3996742586-1129");
            //cid.setSID("S-1-5-21-647177006-1055827929-3996742586-1135");

            impersonation.setConnectingSID(cid);


            //itemTypeDAO.setImpersonation(impersonation);
            
             DistinguishedFolderIdType dft_inbox = new DistinguishedFolderIdType();
             dft_inbox.setId(DistinguishedFolderIdNameType.INBOX);
             

            List<ItemType> lf = itemTypeDAO.getFolderItems( port, dft_inbox );

            itemTypeDAO.setImpersonation(impersonation);

            for (int i = 0; i < 500; i++)
            {
                try
                {

                    itemTypeDAO.GetFolders(port);
//                    lf = itemTypeDAO.getFolderItems(port, dft_inbox);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    System.out.println(".");
                }
            }


            List<BaseFolderType> all_folder_list = new ArrayList<BaseFolderType>();

//            all_folder_list = itemTypeDAO.GetFolders(port);

  //          itemTypeDAO.getFolderItems(port);


            List<BaseFolderType>folders =  itemTypeDAO.GetFolders( port );

            for (Iterator<BaseFolderType> it = folders.iterator(); it.hasNext();)
            {
                BaseFolderType baseFolderType = it.next();

                print_folder_content( itemTypeDAO, port, baseFolderType );

            }
        }
        catch (MalformedURLException ex)
        {
            // Catch any errors that may occur.
            Logger.getLogger(ExchangeDevelopmentTest.class.getName()).log(Level.SEVERE, null, ex);
        }
/*        catch (InaccessibleWSDLException ex)
        {
            // Catch any errors that may occur.
            List<Throwable> list = ex.getErrors();
            for (Iterator<Throwable> it = list.iterator(); it.hasNext();)
            {
                Throwable throwable = it.next();
                throwable.printStackTrace();
            }
        }*/
        catch (Exception ex)
        {
            // Catch any errors that may occur.
            Logger.getLogger(ExchangeDevelopmentTest.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        }       
    }
    static void print_folder_content(ItemTypeDAO itemTypeDAO, ExchangeServicePortType port, BaseFolderType baseFolderType ) throws IOException
    {
        System.out.println("\nContents of Folder " +  baseFolderType.getDisplayName() + ":");

        List<ItemType> mails = itemTypeDAO.getFolderItems( port, baseFolderType.getFolderId() );

        List<ItemIdType> full_mail_list = new ArrayList<ItemIdType>();
        long total_size = 0;
        for (Iterator<ItemType> it1 = mails.iterator(); it1.hasNext();)
        {
            ItemType mail = it1.next();
            Integer size = mail.getSize();
            String s = mail.getSubject();
            System.out.println("Mail: " + s  + " Size: " + size);
            total_size += size.intValue();

            List<ItemIdType> mail_list = new ArrayList<ItemIdType>();
            mail_list.add(mail.getItemId());
            full_mail_list.add(mail.getItemId());
            ArrayOfRealItemsType rfc_mails = itemTypeDAO.getItem(port, mail_list);
            System.out.println(rfc_mails.getItemOrMessageOrCalendarItem().size());
        }

        ArrayOfRealItemsType rfc_mails = itemTypeDAO.getItem(port, full_mail_list);
        
        for (int i = 0; i < rfc_mails.getItemOrMessageOrCalendarItem().size(); i++)
        {
            ItemType msg_type = (MessageType) rfc_mails.getItemOrMessageOrCalendarItem().get(i);
            String mime_txt = msg_type.getMimeContent().getValue();
            String charset = msg_type.getMimeContent().getCharacterSet();
            
            byte[] data = Base64.decodeBase64(mime_txt.getBytes());
            String mailtext = new String( data, charset );
            System.out.println("mail:" + mailtext);
            
        }

        System.out.println("Folder: " + baseFolderType.getDisplayName()  + " Size: " + total_size);

        if (baseFolderType.getChildFolderCount() > 0)
        {
            List<BaseFolderType>folders =  itemTypeDAO.GetFoldersbyParent(port, baseFolderType.getFolderId() );

            for (Iterator<BaseFolderType> it = folders.iterator(); it.hasNext();)
            {
                BaseFolderType sub_folder = it.next();
                
                print_folder_content( itemTypeDAO, port, sub_folder );
            }
        }
    }
}
