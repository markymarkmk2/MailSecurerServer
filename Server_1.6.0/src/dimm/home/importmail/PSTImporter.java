/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.importmail;

import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTObject;
import com.pff.DescriptorIndexNode;
import com.pff.PSTMessage;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author mw
 */
public class PSTImporter
{

    PSTFolder root;
    private PSTImporter( String arg ) throws FileNotFoundException, PSTException, IOException
    {
        PSTFile pf = new PSTFile(arg);
        
        root = pf.getRootFolder();

        HashMap<DescriptorIndexNode, PSTObject> map = root.getSubFolders();

        Set<DescriptorIndexNode> keys = map.keySet();

        Iterator<DescriptorIndexNode> it = keys.iterator();
        while (it.hasNext())
        {
                PSTObject obj = map.get(it.next());
                if (obj instanceof PSTMessage)
                {
                    PSTMessage msg = (PSTMessage)obj;
                    LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_IMPORT, msg.getSubject());
                    LogManager.msg(LogManager.LVL_DEBUG, LogManager.TYP_IMPORT, msg.getSentRepresentingName());
                   
                }
        }



   }


public static void main( String[] args ) throws Exception
    {
        String arg = "Z:\\ExchangeTestdaten\\outlook.pst";
      /*  if (args.length > 0)
        {
            arg = args[0];
        }
        */



       
        String type;

        try
        {
            PSTImporter pst = new PSTImporter( arg );

    /*        dbx.open();

            int cnt = dbx.get_message_count();

            for (int i = 0; i < cnt; i++)
            {
                Message msg = dbx.get_message(i);
                System.out.println("Betreff: " + msg.getSubject());
                type = msg.getContentType();
                System.out.println("Type   : " + type);
                String charset = "ISO-8859-1";
                int idx = type.toLowerCase().indexOf(cs_token);
                if (idx > 0)
                {
                    StringTokenizer str = new StringTokenizer(type.substring(idx + cs_token.length()), "\"\';\n\r,");
                    if (str.hasMoreElements())
                    {
                        charset = dbx.detect_charset(str.nextToken());
                    }
                    else
                    {
                        System.out.println("Chrset Syntax: " + type);
                    }
                }

                DataHandler dh = msg.getDataHandler();
                DataSource ds = dh.getDataSource();
                InputStream is = ds.getInputStream();


                if (is != null && is instanceof InputStream)
                {
                    InputStreamReader istr = new InputStreamReader(is, charset);
                    BufferedReader br = new BufferedReader(istr);

                    System.out.println("Msgcntn: ");
                    while (true)
                    {
                        String txt = br.readLine();
                        if (txt == null)
                        {
                            break;
                        }
                        System.out.println(txt);
                        break;
                    }
                    istr.close();
                    br.close();
                }

                msg = null;
                //System.gc();

            }
            dbx.close();*/
        }
        catch (Exception iOException)
        {
            iOException.printStackTrace();
           
        }
    }

}
