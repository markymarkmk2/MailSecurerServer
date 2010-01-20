/*
 * HelloCommand.java
 *
 * Created on 8. Oktober 2007, 14:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Commands;

import dimm.home.mailarchiv.LogicControl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.ParseToken;
import home.shared.CS_Constants;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 *
 * @author Administrator
 */
public class GetLog extends AbstractCommand
{

    File dump;

    /** Creates a new instance of HelloCommand */
    public GetLog()
    {
        super("show_log");
        dump = null;
    }

    @Override
    public boolean do_command( String data )
    {
        boolean ok = true;
        answer = "";

        String opt = get_opts(data);

        ParseToken pt = new ParseToken(opt);

        dump = null;

        String command = pt.GetString("CMD:");

        if (command.compareTo("read") == 0)
        {
            // show_log CMD:read LG:L4 LI:" + LINES_PER_CALL + " OF:" + offset
            String log_type = pt.GetString("LG:");
            int lines = pt.GetLong("LI:").intValue();
            long offset = pt.GetLong("OF:");

            File log_file = LogManager.get_file_by_type(log_type);
            if (log_file == null || !log_file.exists())
            {
                answer = "1: not exist";
                return ok;
            }
            long len = log_file.length();
            if (offset >= len)
            {
                answer = "42: eof";
                return ok;
            }
            Vector<String> v = tail(log_file.getAbsolutePath(), offset, lines);
            if (v == null)
            {
                answer = "2: tail gave null";
                return ok;
            }
            StringBuffer sb = new StringBuffer();

            Enumeration e = v.elements();
            while (e.hasMoreElements())
            {
                sb.append(e.nextElement());
                sb.append("\n");
            }

            answer = "0: " + sb.toString();
        }
        else
            answer = "1: invalid command";


        return ok;
    }

    /**
     * Given a byte array this method:
     * a. creates a String out of it
     * b. reverses the string
     * c. extracts the lines
     * d. characters in extracted line will be in reverse order,
     *    so it reverses the line just before storing in Vector.
     *
     *  On extracting required numer of lines, this method returns TRUE,
     *  Else it returns FALSE.
     *
     * @param bytearray
     * @param lineCount
     * @param lastNlines
     * @return
     */
    private static boolean parseLinesFromLast( byte[] bytearray, int lineCount, Vector<String> lastNlines )
    {
        String lastNChars = new String(bytearray);
        StringBuffer sb = new StringBuffer(lastNChars);
        lastNChars = sb.reverse().toString();
        StringTokenizer tokens = new StringTokenizer(lastNChars, "\n");
        while (tokens.hasMoreTokens())
        {
            StringBuffer sbLine = new StringBuffer((String) tokens.nextToken());
            lastNlines.add(sbLine.reverse().toString());
            if (lastNlines.size() == lineCount)
            {
                return true;//indicates we got 'lineCount' lines
            }
        }
        return false; //indicates didn't read 'lineCount' lines
    }

    /**
     * Reads last N lines from the given file. File reading is done in chunks.
     *
     * Constraints:
     * 1 Minimize the number of file reads -- Avoid reading the complete file
     * to get last few lines.
     * 2 Minimize the JVM in-memory usage -- Avoid storing the complete file
     * info in in-memory.
     *
     * Approach: Read a chunk of characters from end of file. One chunk should
     * contain multiple lines. Reverse this chunk and extract the lines.
     * Repeat this until you get required number of last N lines. In this way
     * we read and store only the required part of the file.
     *
     * 1 Create a RandomAccessFile.
     * 2 Get the position of last character using (i.e length-1). Let this be curPos.
     * 3 Move the cursor to fromPos = (curPos - chunkSize). Use seek().
     * 4 If fromPos is less than or equal to ZERO then go to step-5. Else go to step-6
     * 5 Read characters from beginning of file to curPos. Go to step-9.
     * 6 Read 'chunksize' characters from fromPos.
     * 7 Extract the lines. On reading required N lines go to step-9.
     * 8 Repeat step 3 to 7 until
     *			a. N lines are read.
     *		OR
     *			b. All lines are read when num of lines in file is less than N.
     * Last line may be a incomplete, so discard it. Modify curPos appropriately.
     * 9 Exit. Got N lines or less than that.
     *
     * @param fileName
     * @param lineCount
     * @param chunkSize
     * @return
     */
    public static Vector<String> tail( String fileName, long offset, int lineCount )
    {
        int chunkSize = CS_Constants.STREAM_BUFFER_LEN;
        RandomAccessFile raf = null;
        try
        {
            raf = new RandomAccessFile(fileName, "r");
            Vector<String> lastNlines = new Vector<String>();
            int delta = 0;
            long curPos = raf.length() - 1 - offset;
            long fromPos;
            byte[] bytearray;
            while (true)
            {
                fromPos = curPos - chunkSize;
                if (fromPos <= 0)
                {
                    raf.seek(0);
                    bytearray = new byte[(int) curPos];
                    raf.readFully(bytearray);
                    parseLinesFromLast(bytearray, lineCount, lastNlines);
                    break;
                }
                else
                {
                    raf.seek(fromPos);
                    bytearray = new byte[chunkSize];
                    raf.readFully(bytearray);
                    if (parseLinesFromLast(bytearray, lineCount, lastNlines))
                    {
                        break;
                    }
                    delta = ((String) lastNlines.get(lastNlines.size() - 1)).length();
                    lastNlines.remove(lastNlines.size() - 1);
                    curPos = fromPos + delta;
                }
            }

            return lastNlines;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
        finally
        {
            if (raf != null)
            {
                try
                {
                    raf.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
    }

    @Override
    public InputStream get_stream()
    {
        FileInputStream fis;
        try
        {
            fis = new FileInputStream(dump);
        }
        catch (FileNotFoundException ex)
        {
            return null;
        }
        return fis;
    }

    @Override
    public long get_data_len()
    {
        return dump.length();
    }

    @Override
    public boolean has_stream()
    {
        return (dump != null);
    }
}
