/**
 * The message class is a representation of an email with utility functions for the various things
 * that may be requested by the client
 */
package rssImapServer;

import java.security.MessageDigest;
import java.util.*;
import java.security.*;

/**
 * @author toweruser
 *
 */
public class Message 
{
	private int UID = 0;
	private int SEQ = 0;
	private String flags = "\\Seen";
	private String internalDate = "";
	private String body = "";
	private Map headers = null;
	private String text = "";
	private String envelope = "";
	private String msgId = "";
	
	/**
	 * constructor...
	 */
	public Message()
	{
		this.UID = 1;
		this.SEQ = 1;
		this.flags = "\\Seen";
		this.internalDate = "11-MAY-2006 00:12:12 +1200";
		this.headers = new LinkedHashMap<String,String>();
		//this.headers.put("Return-Path", "<richard@rjohnson.id.au>");
		//this.headers.put("Received", "from mail8.tpgi.com.au (mail8.tpgi.com.au [203.12.160.46])\r\n\tby smarty.smartysite6.com (8.12.10/8.12.10) with ESMTP id kA52A0cV020668\r\n\tfor <richard@rjohnson.id.au>; Sun, 5 Nov 2006 13:10:00 +1100");
		//this.headers.put("Message-Id", "<1234@localhost>");
		//this.headers.put("X-TPG-Antivirus", "Passed");
		//this.headers.put("Received", "from tower (60-241-4-58.tpgi.com.au [60.241.4.58]) by mail8.tpgi.com.au (envelope-from richard@rjohnson.id.au) (8.13.6/8.13.6) with ESMTP id kA529kHd013940 for <richard@rjohnson.id.au>; Sun, 5 Nov 2006 13:09:49 +1100");
		//this.headers.put("Message-Id", "<200611050209.kA529kHd013940@mail8.tpgi.com.au>");
		this.headers.put("From", "rss2imap <rss2imap@localhost.localdomain>");
		this.headers.put("To", "You <localuser@localhost.localdomain>");
		this.headers.put("Subject", "Test");
		this.headers.put("Date", "Sun, 5 Nov 2006 12:09:56 +1000");
		this.headers.put("MIME-Version", "1.0");
		this.headers.put("Content-Type", "text/html; charset=\"us-ascii\"");
		this.headers.put("Content-Transfer-Encoding", "8bit");
		//this.headers.put("X-Mailer", "Microsoft Office Outlook, Build 11.0.5510");
		//this.headers.put("thread-index", "AccAf31se9uzN6rERf6pT0xckl8OLg");
		//this.headers.put("X-MimeOLE", "Produced By Microsoft MimeOLE V6.00.2900.2962");
		//this.headers.put("X-Spam-Status", "No, hits=4.9 required=10.0 tests=AWL,FORGED_MUA_OUTLOOK,MISSING_OUTLOOK_NAME, MSG_ID_ADDED_BY_MTA_3 version=2.55");
		//this.headers.put("X-Spam-Level", "****");
		//this.headers.put("X-Spam-Checker-Version", "SpamAssassin 2.55 (1.174.2.19-2003-05-19-exp)");
		
		this.text = "No Content!";
		this.body = "RFC822 (\"TEXT\" \"HTML\" (\"CHARSET\" \"us-ascii\") NIL NIL \"7BIT\" "+(this.text.length())+" 1)";
		//this.envelope = "Sun, 5 Nov 2006 12:09:56 +1000\" \"Test\" ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((NIL NIL \"richard\" \"rjohnson.id.au\")) NIL NIL NIL \"<200611050209.kA529kHd013940@mail8.tpgi.com.au>\"";
		this.envelope = "Sun, 5 Nov 2006 12:09:56 +1000\" \"Test\" ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((\"Richard Johnson\" NIL \"richard\" \"rjohnson.id.au\")) ((NIL NIL \"richard\" \"rjohnson.id.au\")) NIL NIL NIL \"<200611050209.kA529kHd013940@mail8.tpgi.com.au>\"";
	}
	
	public void updateEnvelope()
	{
		this.envelope = 
			"\""+this.headers.get("Date")+"\" \""+
			this.headers.get("Subject")+"\" ((\""+
			this.headers.get("From")+"\" NIL))";
		this.envelope = "\""+this.headers.get("Date")+"\" \""+this.headers.get("Subject")+"\" ((NIL NIL \""+this.headers.get("From")+"\" \"localhost\")) ((NIL NIL \""+this.headers.get("From")+"\" \"localhost\")) ((NIL NIL \""+this.headers.get("From")+"\" \"localhost\")) ((NIL NIL \"your name\" \"localhost\")) NIL NIL NIL \"<"+this.msgId+">\"";
	}
	
	/**
	 * get the flags that this message currently has set...
	 * @return
	 */
	public String getFlags()
	{
		return this.flags;
	}
	
	public String getInternalDate()
	{
		return this.internalDate;
	}
	
	public String getBody()
	{
		return this.body;
	}
	
	public String getEnvelope()
	{
		return this.envelope;
	}
	
	public String getText()
	{
		return this.text;
	}
	
	public String getHeaders(String[] params, boolean exclude)
	{
		// okay, are we excluding or including?
		if (exclude)
		{
			Map temp = this.headers;
			for (int x = 0; x < params.length; x++)
			{
				if (this.headers.containsKey(params[x]))
				{
					temp.remove(params[x]);
				}
			}
			
			Iterator iterator = this.headers.keySet().iterator();
			String output = "";
			while (iterator.hasNext())
			{
				String key = (String)iterator.next();
				output += key+": "+this.headers.get(key)+"\r\n";
			}
			return output;
		}
		else
		{
			Map temp = this.headers;
			String output = "";
			for (int x = 0; x < params.length; x++)
			{
				if (this.headers.containsKey(params[x]))
				{
					output += params[x]+": "+this.headers.get(params[x])+"\r\n";
				}
			}
			return output;
		}
	}
	
	public String getHeaders()
	{
		Iterator iterator = this.headers.keySet().iterator();
		String output = "";
		while (iterator.hasNext())
		{
			String key = (String)iterator.next();
			output += key+": "+this.headers.get(key)+"\r\n";
		}
		return output;
	}
	
	public int getSize()
	{
		String temp = this.getHeaders()+"\r\n"+this.getBody();
		return temp.length();
	}
	
	public int getUID()
	{
		return this.UID;
	}
	
	public void setUID(int uid) {this.UID = uid; }
	
	public int getSEQ()
	{
		return this.SEQ;
	}
	public void setSEQ(int seq) {this.SEQ = seq; }
	public void setHeader(String header, String value)
	{
		this.headers.put(header, value);
	}
	public void setText(String text)
	{
		this.text = text;
	}
	public void setInternalDate(String date)
	{
		this.internalDate = date;
	}
	public void setMsgId(String msgId)
	{
		this.msgId = msgId;
	}
	
	public void clearFlags()
	{
		this.flags = "";
	}
	
	/**
	 * get a hash of the headers and text of the post...
	 * @return
	 */
	public String getHash()
	{
//		 get a uid from the subject...
		String hashStr = "";
		try
		{
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			Iterator iterator = this.headers.keySet().iterator();
			md5.update(this.body.getBytes());
			while (iterator.hasNext())
			{
				String key = (String)iterator.next();
				String value = (String)this.headers.get(key);
				md5.update((key+": "+value).getBytes("UTF-8"));
			}
			md5.update((this.internalDate).getBytes());
			byte[] hash = md5.digest();
			for (int x = 0; x < hash.length; x++)
			{
				//uid += hash[x];
				hashStr += Integer.toHexString(hash[x] & 0xff);
			}
			//System.out.println(hashStr);
		}
		catch (Exception err)
		{
			err.printStackTrace();
		}
		return hashStr;
	}
}
