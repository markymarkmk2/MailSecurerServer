/**
 * The RssReader takes a feed and creates emails from it.
 */
package rssImapServer;

import java.io.*;
import java.util.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import java.text.*;
import org.w3c.util.*;

import javax.xml.parsers.*;

/**
 * @author Richard Johnson <rssimap@rjohnson.id.au>
 */
public class RssReader extends DefaultHandler
{
	private BufferedReader in = null;

	private Map<String,String> folderURLs = new HashMap<String,String>();
	// all the items linked to folders 
	private Map<String,List> folderMessages = new HashMap<String,List>();
	
	private StringBuffer textBuffer = null;
	
	// the current item we are adding data to
	private Map<String,String> currentItem = new HashMap<String,String>();
	// a listing of all items in this feed
	private List<Map> itemList = new ArrayList<Map>();
	
	private Map<String,Integer> oldHashes = new HashMap<String,Integer>();
	
	private int uid = 0;
	
	private String uidFile = "uids.txt";
	
	/**
	 * the constructor sets up the client handler and starts the thread..
	 */
	public RssReader(File configFile)
	{
		String username = configFile.getName().substring(0, configFile.getName().length()-4);
		this.uidFile = Server.PATH+"\\uids-"+username+".txt";
		
		// let's load up that file...
		try
		{
			in = new BufferedReader(new FileReader(configFile));
			String line = in.readLine();
			while (line != null)
			{
				// okay, try and split the line at a comma...
				String[] bits = line.split(",");
				if (bits.length == 2)
				{
					folderURLs.put(bits[0].trim(), bits[1].trim());
				}
				
				line = in.readLine();
			}
			
		}
		catch (Exception err)
		{
			err.printStackTrace();
		}
	}
	
	
	////////////////////////////
	
	/**
	 * this loads the messages and puts them into the client handler
	 * this is not done by the thread in the first instance 'cause
	 * we want the client to waid until the messages are loaded before
	 * continuing...
	 */
	public void loadMessages()
	{
		// first let's get a listing of all the current message hashes...
		this.uid = 0;
		try 
		{
			BufferedReader fileIn = new BufferedReader(new FileReader(uidFile));
			String line = fileIn.readLine();
			String[] bits = null;
			while (line != null)
			{
				bits = line.split(",");
				if (bits.length == 2)
				{
					this.oldHashes.put(bits[0], new Integer(bits[1]));
					// okay, if this larger than the current uid, update it
					int temp = Integer.parseInt(bits[1]);
					if (this.uid < temp)
					{
						this.uid = temp;
					}
				}
				line = fileIn.readLine();
			}
			fileIn.close();
		}
		catch (Exception err)
		{
			// we could not read the hashes for some reason...
			err.printStackTrace();
		}
		
		// add one onto the uid...
		this.uid++;
		
		System.out.println("UID: "+this.uid);
		
		// okay, we go through the entries and fetch the feed...
		Iterator iterator = folderURLs.keySet().iterator();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		while (iterator.hasNext())
		{
			String key = (String)iterator.next();
			
			// get the content from that feed...
			String value = (String)folderURLs.get(key);

			try
			{
				// wow, this is way too easy...
				SAXParser parser = factory.newSAXParser();
				parser.parse(value, this);
			}
			catch (Exception err)
			{
				err.printStackTrace();
			}
			//new DefaultHandler().endElement(uri, localName, qName)d
			
			// okay, add all of those items to the folder...
			this.folderMessages.put(key, convertRssToMsg(this.itemList));
			this.itemList = new ArrayList<Map>();
		}
		
		// first let's get a listing of all the current message hashes...
		try 
		{
			PrintWriter fileOut = new PrintWriter(new FileWriter(uidFile));
			// okay, save the listing of message hashes...
			iterator = this.oldHashes.keySet().iterator();
			while (iterator.hasNext())
			{
				String key = (String)iterator.next();
				fileOut.println(key+","+this.oldHashes.get(key));
			}
			fileOut.close();
		}
		catch (Exception err)
		{
			// we could not read the hashes for some reason...
			err.printStackTrace();
		}
		
	}
	
	/**
	 * gets the contents of the rss items and converts it into a message set
	 */
	private List convertRssToMsg(List rssMsgSet)
	{
		Iterator iterator = rssMsgSet.iterator();
		Map<Integer, Message> out = new HashMap<Integer, Message>();
		int seq = 1;
		
		while (iterator.hasNext())
		{
			Map rssMsg = (Map) iterator.next();
			Message msg = new Message();
			String subject = (String)rssMsg.get("title");
			msg.setHeader("Subject", subject);

			if (rssMsg.containsKey("dc:creator"))
			{
				msg.setHeader("From", (String)rssMsg.get("dc:creator")+" <rss2imap@localhost.localdomain>");
			}
			
			if (rssMsg.containsKey("dc:date"))
			{
				// dublin core uses a different date format...
				SimpleDateFormat simpleFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
				SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMddHHmmss");
				Date tempDate = new Date();
				try
				{
					// thanks to the w3c jigsaw project for providing me with this
					// handy ISO 8601 parser....
					tempDate = DateParser.parse((String)rssMsg.get("dc:date"));
				}
				catch (Exception err)
				{ }
				msg.setHeader("Date", simpleFormat.format(tempDate));
				msg.setInternalDate(simpleFormat.format(tempDate));
				msg.setMsgId(idFormat.format(tempDate)+"@localhost");
				
			}
			else if (rssMsg.containsKey("pubDate"))
			{
				// the format for the date should be correct!
				SimpleDateFormat simpleFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
				SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMddHHmmss");
				try {
					Date parsedDate = simpleFormat.parse((String)rssMsg.get("pubDate"));
					msg.setMsgId(idFormat.format(parsedDate)+"@localhost");
				}
				catch (Exception err) { }
				String tempDate = (String)rssMsg.get("pubDate");
				msg.setHeader("Date", tempDate);
				msg.setInternalDate(tempDate);
			}
			
			String text = "<div style=\"line-height: 130%;\"><font face=\"Arial,Verdana\" size=\"2\">";
			
			// first we start with the content...
			if (rssMsg.containsKey("content:encoded"))
			{
				text += (String)rssMsg.get("content:encoded");
			}
			else if (rssMsg.containsKey("description"))
			{
				//System.out.println("SUCCESS!");
				text += (String)rssMsg.get("description");
			}
			
			// add link to the end..
			text += "<br /><a href=\""+rssMsg.get("link")+"\">Read more...</a></div>";
			
			text += "</font>";
			msg.setText(text);
			msg.setSEQ(seq++);
			msg.updateEnvelope();
			
			// okay, see if we have seen this message before and if so, set the id...
			String hash = msg.getHash();
			if (this.oldHashes.containsKey(hash))
			{
				int temp = this.oldHashes.get(hash).intValue();
				msg.setUID(temp);
			}
			else
			{
				// we don't have this message saved...
				// add it to the list...
				this.oldHashes.put(hash, new Integer(this.uid));
				msg.setUID(this.uid);
				//msg.clearFlags();
				// increment for the next message...
				this.uid++;
			}
			
			out.put(new Integer(msg.getUID()), msg);
		}
		List keyList = new ArrayList(out.keySet());
		List output = new ArrayList();
		Collections.sort(keyList);
		iterator = keyList.iterator();
		seq = 0;
		while (iterator.hasNext())
		{
			Integer key = (Integer)iterator.next();
			Message msg = (Message)out.get(key);
			seq++;
			msg.setSEQ(seq);
			output.add(msg);
		}
		return output;
	}
	
	////////////////////
	
	/**
	 * start of an element!!
	 */
	public void startElement(String uri, String localName, String qName, Attributes attributes)
	{
		this.textBuffer = new StringBuffer();
		if (qName.equals("item"))
		{
			this.currentItem = new HashMap<String,String>();
		}
	}
	
	/**
	 * get some text
	 */
	public void characters(char[] buf, int offset, int len)
	{
		this.textBuffer.append(buf, offset, len);
	}
	
	/**
	 * end of an element!!
	 */
	public void endElement(String uri, String localName, String qName)
	{
		if (qName.equals("title"))
		{
			//System.out.println(uri + this.textBuffer);
		}
		
		// if we are an item, we save this entry to the set of items...
		if (this.currentItem != null)
		{
			if (qName.equals("item"))
			{
				this.itemList.add(this.currentItem);
				this.currentItem = null;
			}
			else
			{
				this.currentItem.put(qName, this.textBuffer.toString());
				//System.out.println(qName+"->"+this.textBuffer);
			}
		}
	}
	
	/**
	 * get all of the messages that we have loaded into folders...
	 */
	public Map<String, List> getMessages()
	{
		return this.folderMessages;
	}
}
