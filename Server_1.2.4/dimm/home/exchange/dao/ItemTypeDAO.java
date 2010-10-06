/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.exchange.dao;

import javax.xml.bind.JAXBElement;
import javax.xml.ws.Holder;

import com.microsoft.schemas.exchange.services._2006.messages.*;
import com.microsoft.schemas.exchange.services._2006.types.*;
import dimm.home.exchange.util.ExchangeEnvironmentSettings;
import java.util.*;

/**
 * Data access object related to operations involving ItemType objects.
 *
 * @author Reid Miller
 */
public class ItemTypeDAO
{

    private ExchangeEnvironmentSettings exchangeEnvironmentSettings = new ExchangeEnvironmentSettings();

    /**
     * Gets the Exchange Items for a desired folder and returns them as a
     * List of ItemType objects.
     *
     * This method was adopted from C# documentatino at
     * http://msdn.microsoft.com/en-us/library/bb508824.aspx
     *
     * At this point the method is very basic to illustrate concepts. From
     * here it's not too much of a stretch refactor this method to retreive
     * arbitrary FolderIdType, integrate pagination, and run a search over
     * and arbitrary folder. Exchange Web Services makes all these tasks
     * pretty easy actually.
     *
     * @param port
     * @return List<ItemType>
     */
    public List<ItemType> getFolderItems( ExchangeServicePortType port )
    {
        /* Tell it you only want to look in the Inbox folder by using the
         * DistinguishedFoldIdType.INBOX constant.
         */
        final DistinguishedFolderIdType distinguishedFolderIdType = new DistinguishedFolderIdType();
        distinguishedFolderIdType.setId(DistinguishedFolderIdNameType.INBOX);

        /* We could build a NonEmptyArrayOfBaseFolderIdsType object to search
         * over multiple folders, but for now we will only add the Inbox
         * DistinguishedFolderIdType from above.
         */
        NonEmptyArrayOfBaseFolderIdsType nonEmptyArrayOfBaseFolderIdsType = new NonEmptyArrayOfBaseFolderIdsType();
        nonEmptyArrayOfBaseFolderIdsType.getFolderIdOrDistinguishedFolderId().add(distinguishedFolderIdType);

        /* Tell Exchange you want the response objects to contain all the
         * ItemType properties available to a findItem request. See limitations
         * of findItem requests at http://msdn.microsoft.com/en-us/library/bb508824.aspx
         */
        final ItemResponseShapeType itemResponseShapeType = new ItemResponseShapeType();
        itemResponseShapeType.setBaseShape(DefaultShapeNamesType.ALL_PROPERTIES);

        /* Now make use of both the objects from above to construct a minimal
         * FindItemType request to be sent.
         */
        FindItemType request = new FindItemType();
        request.setTraversal(ItemQueryTraversalType.SHALLOW); // SHALLOW means it doesn't look for "soft deleted" items.
        request.setItemShape(itemResponseShapeType);
        request.setParentFolderIds(nonEmptyArrayOfBaseFolderIdsType);

        /* Create a FindItemResponseType to be sent with the SOAP request. The
         * Holder is filled with the response from Exchange and sent back through
         * the SOAP response.
         */
        FindItemResponseType findItemResponse = new FindItemResponseType();


        Holder<FindItemResponseType> findItemResult = new Holder<FindItemResponseType>(findItemResponse);

        /* Make SOAP request for ItemTypes to EWS */

        port.findItem(request, exchangeEnvironmentSettings.getMailboxCulture(), exchangeEnvironmentSettings.getRequestServerVersion(), findItemResult, exchangeEnvironmentSettings.getServerVersionInfoHolder());

        /* Create List of ItemTypes to be returned by the method */
        List<ItemType> items = new ArrayList<ItemType>();

        /* Evaluate the FindItemResponseType sent back by Exchange */
        FindItemResponseType response = (FindItemResponseType) findItemResult.value;
        ArrayOfResponseMessagesType arrayOfResponseMessagesType = response.getResponseMessages();
        List responseMessageTypeList = arrayOfResponseMessagesType.getCreateItemResponseMessageOrDeleteItemResponseMessageOrGetItemResponseMessage(); // Note: Best method name... ever!

        /* Response is a List of JAXBElement objects. Iterate through them */
        Iterator responseMessagesIterator = responseMessageTypeList.iterator();

        /* In this basic example, there's only one response message
         * (JAXBElement) from Exchange.
         */
        while (responseMessagesIterator.hasNext())
        {
            JAXBElement jaxBElement = (JAXBElement) responseMessagesIterator.next();
            FindItemResponseMessageType findItemResponseMessageType = (FindItemResponseMessageType) jaxBElement.getValue();
            FindItemParentType findItemParentType = findItemResponseMessageType.getRootFolder();

            /* FindItemParentType "Contains the results of a search of a single
             * root folder during a FindItem operation."
             * http://msdn.microsoft.com/en-us/library/microsoft.servicemodel.channels.mail.exchangewebservice.exchange2007.finditemparenttype.aspx
             *
             * If it is null (empty) then the folder does not contain any elements
             */
            if (findItemParentType != null)
            {
                /* Our one response message contained a FindItemParentType with
                 * an ArrayOfRealItemsType (an array of ItemTypes)
                 */
                ArrayOfRealItemsType arrayOfRealItemsType = findItemParentType.getItems();
                List itemList = arrayOfRealItemsType.getItemOrMessageOrCalendarItem();

                /* Iterate through the List of ItemTypes. */
                Iterator itemListIter = itemList.iterator();
                while (itemListIter.hasNext())
                {
                    ItemType itemType = (ItemType) itemListIter.next();

                    /* Watch the console, the subject for each item will stream
                     * by, evidence that our List<ItemType> is being populated.
                     */
                    System.out.println(itemType.getSubject());

                    items.add(itemType);
                }
            }
            else
            {
                /* TODO: Handle failed requests and empty responses. I'm
                 * glossing over this for now.
                 */
            }
        }

        return items;
    }
}
