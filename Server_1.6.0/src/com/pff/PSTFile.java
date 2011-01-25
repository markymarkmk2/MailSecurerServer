/**
 * 
 */
package com.pff;

import java.io.*;
import java.util.*;

/**
 * @author toweruser
 *
 */
public class PSTFile
{

    public static final int ENCRYPTION_TYPE_NONE = 0;
    public static final int ENCRYPTION_TYPE_COMPRESSIBLE = 1;
    private static final int MESSAGE_STORE_DESCRIPTOR_IDENTIFIER = 33;
    private static final int ROOT_FOLDER_DESCRIPTOR_IDENTIFIER = 290;
    // the type of encryption the files uses.
    private int encryptionType = 0;
    // our all important tree.
    private LinkedHashMap<Integer, HashMap<Integer, DescriptorIndexNode>> childrenDescriptorTree = new LinkedHashMap<Integer, HashMap<Integer, DescriptorIndexNode>>();
    private HashMap<Integer, Integer> nameToId = new HashMap<Integer, Integer>();
    private int itemCount = 0;
    private RandomAccessFile in;

    /*MW*/
    static boolean use_32bit;

    /**
     * constructor
     * @param fileName
     * @throws FileNotFoundException
     * @throws PSTException
     * @throws IOException
     */
    public PSTFile( String fileName )
            throws FileNotFoundException, PSTException, IOException
    {
        // attempt to open the file.
        in = new RandomAccessFile(fileName, "r");
        use_32bit = false;

        // get the first 4 bytes, should be !BDN
        try
        {
            byte[] temp = new byte[4];
            in.read(temp);
            String strValue = new String(temp);
            if (!strValue.equals("!BDN"))
            {
                throw new PSTException("Invalid file header: " + strValue + ", expected: !BDN");
            }

            // make sure we are using a 64bit version of a PST...
            temp = new byte[2];
            in.seek(10);
            in.read(temp);
            if ((temp[0] != 0x15 && temp[0] != 0x17) ||
                    temp[1] != 0x00)
            {


                if ((temp[0] != 0x0E && temp[0] != 0x0F) || temp[1] != 0x00)
                {
                    throw new PSTException("Only 64bit PST files are supported at this time");
                }
                use_32bit = true;
            }

            // make sure encryption is turned off at this stage...
            in.seek(513);
            encryptionType = in.readByte();
            if (encryptionType == 0x02)
            {
                throw new PSTException("Only unencrypted and compressable PST files are supported at this time");
            }

            // process the descriptor tree and create our children
            buildDescriptorTree(in);

            // build out name to id map.
            processNameToIdMap(in);

        }
        catch (IOException err)
        {
            throw new PSTException("Unable to read PST Sig", err);
        }

    }

    /**
     * read the name-to-id map from the file and load it in
     * @param in
     * @throws IOException
     * @throws PSTException
     */
    private void processNameToIdMap( RandomAccessFile in )
            throws IOException, PSTException
    {
        // process the name to id map
        DescriptorIndexNode nameToIdMapDescriptorNode = (PSTObject.getDescriptorIndexNode(in, 97));

        // get the data for the map
        OffsetIndexItem nameToIdMapOffset = PSTObject.getOffsetIndexNode(in, nameToIdMapDescriptorNode.dataOffsetIndexIdentifier);
        byte[] nameToIdByte = new byte[nameToIdMapOffset.size];
        in.seek(nameToIdMapOffset.fileOffset);
        in.read(nameToIdByte);
        if (PSTObject.isPSTArray(nameToIdByte))
        {
            nameToIdByte = PSTObject.processArray(in, nameToIdByte);
        }
        if (this.encryptionType == PSTFile.ENCRYPTION_TYPE_COMPRESSIBLE)
        {
            nameToIdByte = PSTObject.decode(nameToIdByte);
        }

        // get the descriptors if we have them
        HashMap<Integer, PSTDescriptorItem> localDescriptorItems = null;
        if (nameToIdMapDescriptorNode.localDescriptorsOffsetIndexIdentifier != 0)
        {
            PSTDescriptor descriptor = new PSTDescriptor(this, nameToIdMapDescriptorNode.localDescriptorsOffsetIndexIdentifier);
            localDescriptorItems = descriptor.getChildren();
        }

        // process the map
        PSTTableBC bcTable = new PSTTableBC(nameToIdByte);
        HashMap<Integer, PSTTableBCItem> tableItems = (bcTable.getItems());

        // if we have a reference to an internal descriptor
        PSTTableBCItem mapEntries = tableItems.get(3);
        nameToIdByte = mapEntries.data;
        if (nameToIdByte.length == 0)
        {
            PSTDescriptorItem mapDescriptorItem = localDescriptorItems.get(mapEntries.entryValueReference);
            OffsetIndexItem tempoffset = PSTObject.getOffsetIndexNode(in, mapDescriptorItem.offsetIndexIdentifier);
            nameToIdByte = new byte[tempoffset.size];
            in.seek(tempoffset.fileOffset);
            in.read(nameToIdByte);
        }
        if (this.encryptionType == PSTFile.ENCRYPTION_TYPE_COMPRESSIBLE)
        {
            nameToIdByte = PSTObject.decode(nameToIdByte);
        }

        // process the entries
        for (int x = 0; x + 8 < nameToIdByte.length; x += 8)
        {
            int mapEntryValue = (int) PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x, x + 4);
//			int mapEntryType = (int)PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x+4, x+6);
            int mapEntryNumber = (int) PSTObject.convertLittleEndianBytesToLong(nameToIdByte, x + 6, x + 8);
            this.nameToId.put(mapEntryValue, mapEntryNumber + 0x8000);
        }
//		System.out.println(this.nameToId);
    }

    int getNameToIdMapItem( int key )
    {
        return this.nameToId.get(key);
    }

    /**
     * destructor just closes the file handle...
     */
    protected void finalize()
            throws IOException
    {
        in.close();
    }

    /**
     * get the type of encryption the file uses
     * @return
     */
    public int getEncryptionType()
    {
        return this.encryptionType;
    }

    /**
     * get the handle to the file we are currently accessing
     * @return
     */
    public RandomAccessFile getFileHandle()
    {
        return this.in;
    }

    /**
     * Build the children descriptor tree
     * This goes through the entire descriptor B-Tree and adds every item to the childrenDescriptorTree.
     * Was looking for an existing data structure in the PST file for this, but apparently they don't exist!
     * @param in
     * @throws IOException
     * @throws PSTException
     */
    private void buildDescriptorTree( RandomAccessFile in )
            throws IOException, PSTException
    {
        if (PSTFile.use_32bit == false)
        {
            long btreeStartOffset = PSTObject.extractLEFileOffset(in, 224);
            processTree64(in, btreeStartOffset);
        }
        else
        {
            long btreeStartOffset = PSTObject.extractLEFileOffset(in, 188);
            processTree32(in, btreeStartOffset);
        }
    }

    /**
     * Recursive function for building the descriptor tree, used by buildDescriptorTree
     * @param in
     * @param btreeStartOffset
     * @throws IOException
     * @throws PSTException
     */
    private void processTree64( RandomAccessFile in, long btreeStartOffset )
            throws IOException, PSTException
    {
        byte[] temp = new byte[2];
        in.seek(btreeStartOffset + 496);
        in.read(temp);

        if ((temp[0] == 0xffffff81 && temp[1] == 0xffffff81))
        {

            in.seek(btreeStartOffset + 488);

            int numberOfItems = in.read();
            in.read(); // maxNumberOfItems
            in.read(); // itemSize
            int levelsToLeaf = in.read();

            if (levelsToLeaf > 0)
            {

                    for (int x = 0; x < numberOfItems; x++)
                    {
                        long branchNodeItemStartIndex = (btreeStartOffset + (24 * x));
                        long nextLevelStartsAt = PSTObject.extractLEFileOffset(in, branchNodeItemStartIndex + 16);
                        processTree64(in, nextLevelStartsAt);
                    }              
            }
            else
            {
                for (int x = 0; x < numberOfItems; x++)
                {
                    // The 64-bit descriptor index b-tree leaf node item
                    // give me the offset index please!
                    in.seek(btreeStartOffset + (x * 32));
                    temp = new byte[32];
                    in.read(temp);

                    DescriptorIndexNode tempNode = new DescriptorIndexNode(temp);

                    // we don't want to be children of ourselves...
                    if (tempNode.parentDescriptorIndexIdentifier == tempNode.descriptorIdentifier)
                    {
                        // skip!
                    }
                    else if (childrenDescriptorTree.containsKey(tempNode.parentDescriptorIndexIdentifier))
                    {
                        // add this entry to the existing list of children
                        LinkedHashMap<Integer, DescriptorIndexNode> children =
                                (LinkedHashMap<Integer, DescriptorIndexNode>) childrenDescriptorTree.get(tempNode.parentDescriptorIndexIdentifier);
                        children.put(tempNode.descriptorIdentifier, tempNode);
                    }
                    else
                    {
                        // create a new entry and add this one to that
                        LinkedHashMap<Integer, DescriptorIndexNode> children = new LinkedHashMap<Integer, DescriptorIndexNode>();
                        children.put(tempNode.descriptorIdentifier, tempNode);
                        childrenDescriptorTree.put(tempNode.parentDescriptorIndexIdentifier, children);
                    }

                    this.itemCount++;

                }
            }
        }
        else
        {
            PSTObject.printHexFormatted(temp, true);
            throw new PSTException("Unable to read descriptor node, is not a descriptor");
        }
    }
    private void processTree32( RandomAccessFile in, long btreeStartOffset )
            throws IOException, PSTException
    {
        byte[] temp = new byte[2];
            in.seek(btreeStartOffset + 500);
        in.read(temp);

        if ((temp[0] == 0xffffff81 && temp[1] == 0xffffff81))
        {

            in.seek(btreeStartOffset + 496);

            int numberOfItems = in.read();
            in.read(); // maxNumberOfItems
            in.read(); // itemSize
            int levelsToLeaf = in.read();

            if (levelsToLeaf > 0)
            {
                    for (int x = 0; x < numberOfItems; x++)
                    {
                        long branchNodeItemStartIndex = (btreeStartOffset + (12 * x));
                        long nextLevelStartsAt = PSTObject.extractLEFileOffset(in, branchNodeItemStartIndex + 8);
                        processTree32(in, nextLevelStartsAt);
                    }
            }
            else
            {
                for (int x = 0; x < numberOfItems; x++)
                {
                    // The 32-bit descriptor index b-tree leaf node item
                    // give me the offset index please!
                    in.seek(btreeStartOffset + (x * 16));
                    temp = new byte[16];
                    in.read(temp);

                    DescriptorIndexNode tempNode = new DescriptorIndexNode(temp);

                    // we don't want to be children of ourselves...
                    if (tempNode.parentDescriptorIndexIdentifier == tempNode.descriptorIdentifier)
                    {
                        // skip!
                    }
                    else if (childrenDescriptorTree.containsKey(tempNode.parentDescriptorIndexIdentifier))
                    {
                        // add this entry to the existing list of children
                        LinkedHashMap<Integer, DescriptorIndexNode> children =
                                (LinkedHashMap<Integer, DescriptorIndexNode>) childrenDescriptorTree.get(tempNode.parentDescriptorIndexIdentifier);
                        children.put(tempNode.descriptorIdentifier, tempNode);
                    }
                    else
                    {
                        // create a new entry and add this one to that
                        LinkedHashMap<Integer, DescriptorIndexNode> children = new LinkedHashMap<Integer, DescriptorIndexNode>();
                        children.put(tempNode.descriptorIdentifier, tempNode);
                        childrenDescriptorTree.put(tempNode.parentDescriptorIndexIdentifier, children);
                    }

                    this.itemCount++;

                }
            }
        }
        else
        {
            PSTObject.printHexFormatted(temp, true);
            throw new PSTException("Unable to read descriptor node, is not a descriptor");
        }
    }

    /**
     * get the child item descriptors for a specific descriptor
     * @return
     */
    public LinkedHashMap<Integer, DescriptorIndexNode> getChildrenDescriptors( int descriptorIdentifier )
    {
        if (!this.childrenDescriptorTree.containsKey(descriptorIdentifier))
        {
            return new LinkedHashMap<Integer, DescriptorIndexNode>();
        }
        return (LinkedHashMap<Integer, DescriptorIndexNode>) this.childrenDescriptorTree.get(descriptorIdentifier);
    }

    /**
     * get the message store of the PST file.
     * Note that this doesn't really have much information, better to look under the root folder
     * @return
     * @throws PSTException
     * @throws IOException
     */
    public PSTMessageStore getMessageStore()
            throws PSTException, IOException
    {
        DescriptorIndexNode messageStoreDescriptor = PSTObject.getDescriptorIndexNode(in, MESSAGE_STORE_DESCRIPTOR_IDENTIFIER);
        return new PSTMessageStore(this, messageStoreDescriptor);
    }

    public PSTFolder getRootFolder()
            throws PSTException, IOException
    {
        DescriptorIndexNode rootFolderDescriptor = PSTObject.getDescriptorIndexNode(in, ROOT_FOLDER_DESCRIPTOR_IDENTIFIER);
        return new PSTFolder(this, rootFolderDescriptor);
    }
}
