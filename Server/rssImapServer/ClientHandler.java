/**
 * The ClientHandler class is responsible for replying to client requests.
 */
package rssImapServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * @author Richard Johnson <rssimap@rjohnson.id.au>
 */
public class ClientHandler implements Runnable
{
	// some globals, we need the client socket here...
	private Socket client = null;
	//private DataOutputStream out = null;
	private PrintWriter out = null;
	private BufferedReader in = null;
	private int state = 0;
	private String currentMailbox = "";
	private String username = "";
	private Server server = null;
	
	public static final int NOT_AUTHENTICATED_STATE = 0;
	public static final int AUTHENTICATED_STATE = 1;
	public static final int SELECTED_STATE = 2;
	public static final int LOGOUT_STATE = 3;
	public static int UIDVALIDITY = 1;
	
	//private List<Message> messages = new ArrayList<Message>();
	private Map<String,List> messages = new HashMap<String,List>();
	
	/**
	 * Constructor, saves the socket to a local var and starts the thread...
	 * @param client
	 */
	public ClientHandler(Socket client, Server server)
	{
		this.client = client;
		this.server = server;
		
		Thread myThread = new Thread(this);
		myThread.start();
		
	}
	
	////////////////////////////
	
	/**
	 * this is the entry point of the converation thread...
	 */
	public void run()
	{
		if (this.client == null || this.client.isConnected() == false)
		{
			System.out.println("Unable to start conversation, invalid connection passed to client handler");
		}
		else
		{
			System.out.println("client handler thread started for client "+client.getInetAddress().getHostAddress());

			try
			{
				this.out = new PrintWriter(client.getOutputStream());
				//this.out = new DataOutputStream(client.getOutputStream());
				//new BufferedOutputStream(client.getOutputStream());
				//new java.io.DataOutputStream().wr
				this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				
				// send a greeting...
				println("* OK [CAPABILTY IMAP4rev1] RSS2IMAP Copyright Richard Johnson 2006");
				out.flush();
				
				// now we wait for input from the client and fire off responses as we need to...
				String line = in.readLine();
				Pattern pattern = Pattern.compile("([a-zA-Z0-9]+) ([a-zA-Z0-9]+)(.*)");
				while (line != null)
				{
					System.out.println(line);
					
					// see if this is a valid command...
					Matcher matcher = pattern.matcher(line);
					if (matcher.find())
					{
						// we have our bits, get them from the regex...
						String tag = matcher.group(1);
						String command = matcher.group(2);
						String params = matcher.group(3);
						handleCommand(tag, command, params);
					}
					else
					{
						// we are sooo not talking the same language here...
						this.client.close();
						return;
					}
					line = in.readLine();
				}
				System.out.println("connection to client "+client.getInetAddress().getHostAddress()+" closed by client");
				
			}
			catch (Exception err)
			{
				//err.printStackTrace();
			}
			
		}
	}
	
	/**
	 * process the command and send a response if appropriate...
	 * @param line
	 */
	public void handleCommand(String tag, String command, String params)
	{
		// just make all commands upper case...
		command = command.toUpperCase();
		
		// initally we try some commands valid for all states...
		if (command.equals("CAPABILITY"))
		{
			println("* CAPABILITY IMAP4rev1");
			println(tag+" OK CAPABILITY completed");
		}
		else if (command.equals("NOOP"))
		{
			println(tag+" OK NOOP completed (doo di doo)");
		}
		else if (command.equals("LOGOUT"))
		{
			println("* BYE IMAP4rev1 Server logging out");
			println(tag+" OK LOGOUT completed (cya!)");
			flush();
			// kill our connection to the client...
			try 
			{
				client.close();
				return;
			}
			catch (Exception err)
			{
				err.printStackTrace();
			}
		}
		else
		{
			// now we go through the states...
			if (this.state == NOT_AUTHENTICATED_STATE)
			{
				if (command.equals("LOGIN"))
				{
					// split up the params...
					String username = "";
					try
					{
						String[] bits = params.split(" ");
						// kill any quotes...
						username = bits[1].replace("\"", "");
						this.username = username;
						this.messages = server.getMessages(username);

					}
					catch (Exception err)
					{
						// we got a bad command...
						println(tag+" BAD parameters");
						flush();
						return;
					}
					
					// we should have the username and password, see if they are correct...
					File configFile = new File(Server.PATH+"\\"+username+".txt");
					if (configFile.exists())
					{
						this.state = AUTHENTICATED_STATE;
						println(tag+" OK LOGIN completed");
					}
					else
					{
						// we failed
						println(tag+" NO LOGIN failed.");
					}
				}
				else
				{
					// that is all the commands we accept in this state...
					unrecognizedCommand(tag, command);
				}
			}
			else if (this.state == AUTHENTICATED_STATE || this.state == SELECTED_STATE)
			{
				if (command.equals("SELECT"))
				{
					// we are asking for some info on the mail box...
					// get the mailbox name...
					params = params.replace("\"", "");
					params = params.trim().toUpperCase();
					if (params.equals("INBOX"))
					{
						this.currentMailbox = params;
						println("* 0 EXISTS");
						println("* 0 RECENT");
						println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
						println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
						// now we are in the selected state...
						this.state = SELECTED_STATE;
						this.currentMailbox = "INBOX";
					}
					else
					{
						Iterator folderIterator = this.messages.keySet().iterator();
						this.currentMailbox = "";
						while (folderIterator.hasNext())
						{
							String folder = (String)folderIterator.next();
							String folderUpper = folder.toUpperCase();
							if (params.equals("INBOX/"+folderUpper))
							{
								this.currentMailbox = folder;
								List messageList = this.messages.get(folder);
								
								println("* "+messageList.size()+" EXISTS");
								println("* 0 RECENT");
								println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
								println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
								this.state = SELECTED_STATE;
							}
						}
						if (this.currentMailbox.equals(""))
						{
							println(tag+" NO SELECT failed, no mailbox with that name");
						}
					}
				}
				else if (command.equals("STATUS"))
				{
					// we are asking for some info on the mail box...
					// get the mailbox name...
					params = params.replace("\"", "");
					params = params.replace("(UNSEEN)", "");
					params = params.replace("(MESSAGES UNSEEN)", "");
					params = params.trim().toUpperCase();
					if (params.equals("INBOX"))
					{
						this.currentMailbox = params;
						println("* 0 EXISTS");
						println("* 0 RECENT");
						println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
						println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
					}
					else
					{
						Iterator folderIterator = this.messages.keySet().iterator();
						boolean found = false;
						while (folderIterator.hasNext())
						{
							String folder = (String)folderIterator.next();
							String folderUpper = folder.toUpperCase();
							if (params.equals("INBOX/"+folderUpper))
							{
								List messageList = this.messages.get(folder);
								println("* "+messageList.size()+" EXISTS");
								println("* 0 RECENT");
								println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
								println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
								this.state = SELECTED_STATE;
								found = true;
							}
						}
						if (!found)
						{
							println(tag+" NO STATUS failed, no mailbox with that name");
						}
					}
				}
				else if (command.equals("EXAMINE"))
				{
					// we are asking for some info on the mail box...
					// get the mailbox name...
					params = params.replace("\"", "");
					params = params.trim().toUpperCase();
					if (params.equals("INBOX"))
					{
						println("* 0 EXISTS");
						println("* 0 RECENT");
						println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
						println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
					}
					else
					{
						Iterator folderIterator = this.messages.keySet().iterator();
						boolean found = false;
						while (folderIterator.hasNext())
						{
							String folder = (String)folderIterator.next();
							String folderUpper = folder.toUpperCase();
							if (params.equals("INBOX/"+folderUpper))
							{
								List messageList = this.messages.get(folder);
								println("* "+messageList.size()+" EXISTS");
								println("* 0 RECENT");
								println("* OK [UIDVALIDITY "+UIDVALIDITY+"] UID validity status");
								println(tag+" OK [READ-ONLY] "+command+" COMPLETED");
							}
						}
						if (!found)
						{
							println(tag+" NO EXAMINE failed, no mailbox with that name");
						}
					}
				}
				else if (command.equals("CREATE"))
				{
					println(tag+" NO you like totally cannot create a mailbox here.");
				}
				else if (command.equals("DELETE"))
				{
					println(tag+" NO ummm, cannot delete either.");
				}
				else if (command.equals("RENAME"))
				{
					println(tag+" NO renaming is right out.");
				}
				else if (command.equals("LIST"))
				{
					// okay, let's have a look at the params before we do anything...
					if (params.equals(" \"\" \"\""))
					{
						// right, this means the client is after the delimeter
						println("* LIST (\\Noselect) \"/\" \"\"");
						println(tag+" OK LIST Completed");
					}
					else if (params.equals(" \"\" \"INBOX\""))
					{
						// print out a general description for the inbox...
						println("* LIST (\\Unmarked) \"/\" \"INBOX\"");
						println(tag+" OK LIST Completed");
					}
					else if (params.equals(" \"\" \"*\"") || params.equals(" \"\" \"INBOX*\""))
					{
						println("* LIST (\\Unmarked) \"/\" \"INBOX\"");
						// get a listing of the folders...
						Iterator folderIterator = this.messages.keySet().iterator();
						while (folderIterator.hasNext())
						{
							println("* LIST () \"/\" \"INBOX/"+folderIterator.next()+"\"");
						}
						println(tag+" OK LIST completed");
					}
					else
					{
						println(tag+" OK LIST Completed");
					}
				}
				else if (command.equals("LSUB"))
				{
					// get a listing of the folders...
					Iterator folderIterator = this.messages.keySet().iterator();
					while (folderIterator.hasNext())
					{
						println("* LSUB () \"/\" \"INBOX/"+folderIterator.next()+"\"");
					}
					println(tag+" OK LSUB completed");
				}
				else if (command.equals("SUBSCRIBE"))
				{
					System.out.println("----------");
					System.out.println(params);
					println(tag+" OK SUBSCRIBE completed");
				}
				else if (command.equals("UNSUBSCRIBE"))
				{
					System.out.println("----------");
					System.out.println(params);
					println(tag+" OK UNSUBSCRIBE completed");
				}
				/*
				else if (command.equals("STATUS"))
				{
					// status lets you query a mailbox that is not selected...
				}
				*/
				else if (command.equals("APPEND"))
				{
					println(tag+" NO pfft don't even try it.");
				}
				else if (this.state == SELECTED_STATE)
				{
					// okay, see if we have called any of these functions
					if (command.equals("CHECK"))
					{
						println(tag+" OK CHECK Completed");
					}
					else if (command.equals("CLOSE"))
					{
						// we don't need to delete anything, so we just go back to the authenticated state
						println(tag+" OK CLOSE Completed");
						this.state = AUTHENTICATED_STATE;
					}
					else if (command.equals("EXPUNGE"))
					{
						println(tag+" NO EXPUNGE i don't think so!");
					}
					else if (command.equals("SEARCH"))
					{
						println(tag+" NO SEARCH maybe in a later version");
					}
					else if (command.equals("FETCH"))
					{
						// this is where some fun stuff happens...
						// get what we can out of the request...
						Pattern pattern = Pattern.compile(" ([0-9*]+):([0-9*]+) \\((.+)\\)");
						Matcher matcher = pattern.matcher(params);
						if (matcher.find())
						{
							// cool, the inital stuff was valid...
							String startMsg = matcher.group(1);
							String endMsg = matcher.group(2);
							String fetchParams = matcher.group(3);
							
							int startMsgInt = -1;
							int endMsgInt = -1;
							
							if (!startMsg.equals("*"))
							{
								startMsgInt = Integer.parseInt(startMsg);
							}
							if (!endMsg.equals("*"))
							{
								endMsgInt = Integer.parseInt(endMsg);
							}
							
							List messages = this.getMessagesBetweenSEQs(startMsgInt, endMsgInt);
							Iterator msgIterator = messages.iterator();
							while (msgIterator.hasNext())
							{
								Message msg = (Message)msgIterator.next();
								List commands = getFetchCommands(fetchParams);
								Iterator iterator = commands.iterator(); 
								String output = "* "+msg.getSEQ()+" FETCH (";
								while (iterator.hasNext())
								{
									String temp = (String)iterator.next();
									output += processFetchCommand(temp, msg)+" ";
								}
								output = output.substring(0, output.length()-1);
								output += ")";
								println(output);
							}
							
							println(tag+" OK FETCH completed");
						}
						else
						{
							pattern = Pattern.compile(" ([0-9*]+) \\((.+)\\)");
							matcher = pattern.matcher(params);
							if (matcher.find())
							{
								// cool, the inital stuff was valid...
								String startMsg = matcher.group(1);
								int startMsgInt = Integer.parseInt(startMsg);
								String fetchParams = matcher.group(2);
								
								List commands = getFetchCommands(fetchParams);
								Iterator iterator = commands.iterator(); 
								
								Message msg = this.getMessageBySEQ(startMsgInt);
								
								String output = "* "+msg.getSEQ()+" FETCH (";
								while (iterator.hasNext())
								{
									String temp = (String)iterator.next();
									output += processFetchCommand(temp, msg)+" ";
								}
								output = output.substring(0, output.length()-1);
								output += ")";
								println(output);
								
								println(tag+" OK UID completed");
							}
							
						}
					}
					else if (command.equals("STORE"))
					{
						println(tag+" NO STORE, talk to the hand");
					}
					else if (command.equals("COPY"))
					{
						println(tag+" NO COPY I think that once is enough for you");
					}
					else if (command.equals("UID"))
					{
						// more fun stuff...
						// see what the next param was, remember we only support the fetch command...
						if (!params.trim().substring(0, 5).toUpperCase().equals("FETCH"))
						{
							//println(tag+" NO COPY, STORE or SEARCH with this server, sorry");
							println(tag+" OK UID completed");
						}
						else
						{
							
							// we are fetching!!
							// remove the fetch command from the params string...
							params = params.substring(5).trim();
							// get the UIDs...
							Pattern pattern = Pattern.compile(" ([0-9*]+):([0-9*]+) \\((.+)\\)");
							Matcher matcher = pattern.matcher(params);
							if (matcher.find())
							{
								
								// cool, the inital stuff was valid...
								String startUID = matcher.group(1);
								String endUID = matcher.group(2);
								String fetchParams = matcher.group(3);
								
								// if the uid is not in the params, we just add it to start with...
								if (fetchParams.indexOf("uid") == -1 && fetchParams.indexOf("UID") == -1)
								{
									fetchParams = "UID "+fetchParams;
								}
								
								int startMsgInt = -1;
								int endMsgInt = -1;
								
								if (!startUID.equals("*"))
								{
									startMsgInt = Integer.parseInt(startUID);
								}
								if (!endUID.equals("*"))
								{
									endMsgInt = Integer.parseInt(endUID);
								}
								
								List messages = this.getMessagesBetweenUIDs(startMsgInt, endMsgInt);
								Iterator msgIterator = messages.iterator();
								while (msgIterator.hasNext())
								{
									Message msg = (Message)msgIterator.next();
									List commands = getFetchCommands(fetchParams);
									Iterator iterator = commands.iterator(); 
									String output = "* "+msg.getSEQ()+" FETCH (";
									while (iterator.hasNext())
									{
										String temp = (String)iterator.next();
										output += processFetchCommand(temp, msg)+" ";
									}
									output = output.substring(0, output.length()-1);
									output += ")";
									println(output);
								}
								
								
								println(tag+" OK UID completed");
								System.out.println("Done");
							}
							else
							{
								// get the UIDs...
								pattern = Pattern.compile(" ([0-9*]+) \\((.+)\\)");
								matcher = pattern.matcher(params);
								if (matcher.find())
								{
									// cool, the inital stuff was valid...
									String startUID = matcher.group(1);
									int startUIDInt = Integer.parseInt(startUID);
									String fetchParams = matcher.group(2);
									
									// if the uid is not in the params, we just add it to start with...
									if (fetchParams.indexOf("uid") == -1 && fetchParams.indexOf("UID") == -1)
									{
										fetchParams = "UID "+fetchParams;
									}
									
									List commands = getFetchCommands(fetchParams);
									Iterator iterator = commands.iterator(); 

									Message msg = this.getMessageByUID(startUIDInt);
									
									String output = "* "+msg.getSEQ()+" FETCH (";
									while (iterator.hasNext())
									{
										String temp = (String)iterator.next();
										output += processFetchCommand(temp, msg)+" ";
									}
									output = output.substring(0, output.length()-1);
									output += ")";
									println(output);
									
									println(tag+" OK UID completed");
								}
							}
						}
					}
				}
				else
				{
					// that is all the commands we accept in this state...
					unrecognizedCommand(tag, command);
				}
			}
		}
		flush();
	}
	
	/**
	 * This function simply tells the client that the command they tried to use
	 * is not implemented.  This is useful because there are a few places where
	 * this needs to exist in the maze of if statements above us... 
	 * @param tag
	 * @param command
	 */
	public void unrecognizedCommand(String tag, String command)
	{
		System.out.println("Could not: "+tag+" " + command);
		println(tag+" BAD "+command+" - i can't do that dave...");
	}
	
	/**
	 * As both the UID and FETCH commands require parameter processing, it makes sense to
	 * put this functionality into a seperate function...
	 * @param fetchParams
	 */
	public List getFetchCommands(String fetchParams)
	{
		// okay, params could be something like:
		// FLAGS BODY[HEADER.FIELDS (DATE FROM)]
		// anything between square brackets needs to be atomic
		// gotta love crazy regexps...
		
		if (fetchParams.equals("ALL"))
		{
			fetchParams = "FLAGS INTERNALDATE RFC822.SIZE ENVELOPE";
		}
		else if (fetchParams.equals("FAST"))
		{
			fetchParams = "FLAGS INTERNALDATE RFC822.SIZE";
		}
		else if (fetchParams.equals("FULL"))
		{
			fetchParams = "FLAGS INTERNALDATE RFC822.SIZE ENVELOPE BODY";
		}
		
		Pattern fetchPattern = Pattern.compile("[a-zA-Z0-9\\.]+(\\[\\])?(\\[[a-zA-Z0-9\\.()\\- ]+\\])?");
		Matcher fetchMatcher = fetchPattern.matcher(fetchParams);
		List<String> fetchCommands = new ArrayList<String>();
		while (fetchMatcher.find())
		{
			String newCommand = fetchMatcher.group(0);
			fetchCommands.add(newCommand);
			fetchParams = fetchParams.substring(newCommand.length()).trim();
			fetchMatcher = fetchPattern.matcher(fetchParams);
		}
		return fetchCommands;
	}
	
	/**
	 * This function allows us to process the fetch component of a command (either a UID or a FETCH)
	 * and provide the correct output.
	 * @param params
	 * @param tempMessage is the message to get the details of...
	 * @return the processed fetch command ready for returning to the client.
	 */
	public String processFetchCommand(String params, Message tempMessage)
	{
		// get the command out...
		String command = params;
		if (params.indexOf(" ") != -1)
		{
			command = params.substring(0, params.indexOf(" "));
			params = params.substring(params.indexOf(" ")).trim();
		}
		command = command.toUpperCase();
		//System.out.println("Command: "+command);
		if (command.equals("FLAGS"))
		{
			return "FLAGS ("+tempMessage.getFlags()+")";
		}
		else if (command.equals("INTERNALDATE"))
		{
			return "INTERNALDATE \""+tempMessage.getInternalDate()+"\"";
		}
		else if (command.equals("BODY"))
		{
			System.out.println(params);
			return "BODY("+tempMessage.getBody()+")";
		}
		else if (command.equals("RFC822"))
		{
			String temp = tempMessage.getHeaders()+"\r\n"+tempMessage.getText();
			return "RFC822 {"+temp.length()+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("RFC822.HEADER"))
		{
			String temp = tempMessage.getHeaders();
			return "RFC822 {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("RFC822.SIZE"))
		{
			String temp = tempMessage.getHeaders()+"\r\n"+tempMessage.getText();
			return "RFC822.SIZE "+temp.length();
		}
		else if (command.equals("RFC822.TEXT"))
		{
			String temp = tempMessage.getText();
			return "RFC822.TEXT {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("UID"))
		{
			return "UID "+tempMessage.getUID();
		}
		else if (command.equals("BODY[]") || command.equals("BODY.PEEK[]"))
		{
			String temp = tempMessage.getHeaders()+"\r\n"+tempMessage.getText();
			return "BODY[] {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("BODY[HEADER]"))
		{
			String temp = tempMessage.getHeaders();
			return "BODY[HEADER] {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("BODY[TEXT]"))
		{
			String temp = tempMessage.getText();
			return "BODY[TEXT] {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("BODY[HEADER.FIELDS"))
		{
			params = params.replace("]", "");
			params = params.replace("(", "");
			params = params.replace(")", "");
			params = params.replace("\"", "");
			
			String output = "BODY[HEADER.FIELDS (";
			
			String[] bits = params.split(" ");
			for (int y = 0; y < bits.length; y++)
			{
				//System.out.println(bits[y]);
				output += "\""+bits[y].toUpperCase()+"\" ";
			}
			output = output.substring(0, output.length()-1);
			output += ")] ";
			String temp = tempMessage.getHeaders(bits, false);
			output += "{"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
			return output;
		}
		else if (command.equals("BODY[HEADER.FIELDS.NOT"))
		{
			// clean up the params...
			String output = command+params;
			params = params.replace("]", "");
			params = params.replace("(", "");
			params = params.replace(")", "");
			params = params.replace("\"", "");
			
			String[] bits = params.split(" ");
			String temp = tempMessage.getHeaders(bits, true);
			output += " {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
			return output;
		}
		else if (command.equals("BODY.PEEK[TEXT]"))
		{
			String temp = tempMessage.getText();
			return "BODY[TEXT] {"+(temp.length()+2)+"}\r\n"+temp;
		}
		else if (command.equals("BODY.PEEK[HEADER]"))
		{
			String temp = tempMessage.getHeaders();
			return "BODY[HEADER] {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
		}
		else if (command.equals("BODY.PEEK[HEADER.FIELDS"))
		{
			// clean up the params...
			params = params.replace("]", "");
			params = params.replace("(", "");
			params = params.replace(")", "");
			params = params.replace("\"", "");
			
			String output = "BODY[HEADER.FIELDS (";
			
			String[] bits = params.split(" ");
			for (int y = 0; y < bits.length; y++)
			{
				//System.out.println(bits[y]);
				output += "\""+bits[y].toUpperCase()+"\" ";
			}
			output = output.substring(0, output.length()-1);
			output += ")] ";
			String temp = tempMessage.getHeaders(bits, false);
			output += "{"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
			return output;
		}
		else if (command.equals("BODY.PEEK[HEADER.FIELDS.NOT"))
		{
			// clean up the params...
			String output = "BODY[HEADER.FIELDS.NOT"+params;
			params = params.replace("]", "");
			params = params.replace("(", "");
			params = params.replace(")", "");
			params = params.replace("\"", "");
			
			String[] bits = params.split(" ");
			String temp = tempMessage.getHeaders(bits, true);
			output += " {"+(temp.length()+2)+"}\r\n"+temp+"\r\n";
			return output;
		}
		else if (command.equals("BODYSTRUCTURE"))
		{
			return "BODYSTRUCTURE "+tempMessage.getBody();
		}
		else if (command.equals("ENVELOPE"))
		{
			return "ENVELOPE ("+tempMessage.getEnvelope()+")";
		}
		return "";
	}
	
	/**
	 * this functions gets all the messages that match a seq no...
	 * @return
	 */
	private List getMessagesBetweenSEQs(int startSeq, int endSeq)
	{
		List<Message> output = new ArrayList<Message>();
		Iterator iterator = this.messages.get(this.currentMailbox).iterator();
		while (iterator.hasNext())
		{
			Message msg = (Message)iterator.next();
			if (startSeq > 0 && endSeq > 0)
			{
				// between
				if (startSeq >= msg.getSEQ() && endSeq <= msg.getSEQ())
				{
					output.add(msg);
				}
			}
			else if (startSeq > 0)
			{
				// gt startUid
				if (msg.getSEQ() >= startSeq)
				{
					output.add(msg);
				}
			}
			else if (endSeq > 0)
			{
				// lt endUid
				if (msg.getSEQ() <= endSeq)
				{
					output.add(msg);
				}
			}
			else
			{
				// all uids!
				output.add(msg);
			}
		}
		return output;
	}
	
	/**
	 * this functions gets all the messages that match a uid...
	 * @return
	 */
	private List getMessagesBetweenUIDs(int startUid, int endUid)
	{
		List<Message> output = new ArrayList<Message>();
		Iterator iterator = this.messages.get(this.currentMailbox).iterator();
		while (iterator.hasNext())
		{
			Message msg = (Message)iterator.next();
			if (startUid > 0 && endUid > 0)
			{
				// between
				if (startUid <= msg.getUID() && endUid >= msg.getUID())
				{
					output.add(msg);
				}
			}
			else if (startUid > 0)
			{
				// gt startUid
				if (msg.getUID() >= startUid)
				{
					output.add(msg);
				}
			}
			else if (endUid > 0)
			{
				// lt endUid
				if (msg.getUID() <= endUid)
				{
					output.add(msg);
				}
			}
			else
			{
				// all uids!
				output.add(msg);
			}
		}
		return output;
	}
	
	/**
	 * Get the message that matches a specific UID...
	 * @param uid
	 * @return
	 */
	private Message getMessageByUID(int uid)
	{
		Iterator iterator = this.messages.get(this.currentMailbox).iterator();
		while (iterator.hasNext())
		{
			Message temp = (Message)iterator.next();
			//System.out.println("UID: "+temp.getUID());
			if (temp.getUID() == uid)
			{
				return temp;
			}
		}
		//System.out.println("No message found!!");
		return null;
	}

	/**
	 * Get the message with that sequence number...
	 * @param seq
	 * @return
	 */
	private Message getMessageBySEQ(int seq)
	{
		Iterator iterator = this.messages.get(this.currentMailbox).iterator();
		while (iterator.hasNext())
		{
			Message temp = (Message)iterator.next();
			if (temp.getSEQ() == seq)
			{
				return temp;
			}
		}
		return null;
	}
	
	/**
	 * This attempts to write a string to the client through a data socket.
	 * @param theString
	 */
	private synchronized void println(String theString)
	{
		
		try
		{
			//System.out.println("about to write:\n");
			System.out.println(theString);
			//System.out.println("starting write");
			//this.out.writeBytes(theString+"\r\n");
			this.out.print(theString+"\r\n");
			//System.out.println("written");
			this.out.flush();
			//System.out.println("flushed");
		}
		catch (Exception err)
		{
			// we don't care about error messages from closed sockets...
			if (theString.indexOf("LOGOUT") == -1 && theString.indexOf("BYE") == -1)
			{
				System.err.println("Tried to write: "+theString);
				err.printStackTrace();
			}
		}
	}
	
	/**
	 * Flush the data socket connection to the client.
	 */
	private void flush()
	{
		try
		{
			this.out.flush();
		}
		catch (Exception err)
		{
			err.printStackTrace();
		}
	}
	
	/**
	 * this is called by the RSS listener thread to update the message list.
	 * @param messages
	 */
	public void setMessages(Map<String, List> messages)
	{
		this.messages = messages;
	}
	
	/**
	 * called by the RSS Reader listener thread to ensure it is not wasting its time
	 * and a client is still connected to this socket.
	 * @return
	 */
	public boolean isConnected()
	{
		return (this.client.isConnected());
	}
	
	public String getUsername()
	{
		return this.username;
	}
}
