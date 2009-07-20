package dimm.home.extraction;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.util.*;
import java.nio.charset.Charset;
import java.io.*;

public class Extractor implements Serializable
{

    protected static final Map<String, TextExtractor> handlers;
    protected ArrayList<String> fileDeleteList = new ArrayList<String>();

    static
    {
        handlers = new HashMap<String, TextExtractor>();
        TextExtractor plain = new PlainTextExtractor();
        handlers.put("text/plain", plain);
        handlers.put("txt", plain);
        TextExtractor html = new HTMLExtractor();
        handlers.put("text/html", html);
        handlers.put("html", html);
        TextExtractor pdf = new PDFExtractor();
        handlers.put("application/pdf", pdf);
        handlers.put("pdf", pdf);
        TextExtractor word = new WordExtractor();
        handlers.put("application/msword", word);
        handlers.put("application/vnd.ms-word", word);
        handlers.put("application/vnd.msword", word);
        handlers.put("doc", word);
        TextExtractor excel = new ExcelExtractor();
        handlers.put("application/excel", excel);
        handlers.put("application/msexcel", excel);
        handlers.put("application/vnd.ms-excel", excel);
        handlers.put("xls", excel);
        TextExtractor ppt = new PowerpointExtractor();
        handlers.put("application/vnd.ms-powerpoint", ppt);
        handlers.put("application/mspowerpoint", ppt);
        handlers.put("application/powerpoint", ppt);
        handlers.put("ppt", ppt);
        TextExtractor rtf = new RTFExtractor();
        handlers.put("application/rtf", rtf);
        handlers.put("rtf", rtf);
        TextExtractor oo = new OOExtractor();
        handlers.put("application/vnd.oasis.opendocument.text", oo);
        handlers.put("application/vnd.oasis.opendocument.spreadsheet", oo);
        handlers.put("application/vnd.oasis.opendocument.presentation", oo);
        handlers.put("odt", oo);
        handlers.put("ods", oo);
        handlers.put("odp", oo);
    }

    public Extractor()
    {
    }

    public static Reader getText( InputStream is, String mimetype, Charset fromCharset )
    {
        TextExtractor extractor;
        extractor = handlers.get(mimetype.toLowerCase(Locale.ENGLISH));
        if (extractor == null)
        {
            //throw new ExtractionException("failed to extract text (mimetype not supported) {mimetype='"+mimetype+"'}",logger);
            return null;
        }
        else
        {
            try
            {
                return extractor.getText(is, fromCharset);
            }
            catch (Exception ee)
            {
                LogManager.debug("failed to extract text from document:" + ee.getMessage(), ee);
                return new StringReader("");
            }
        }
    }

    // helper
    public static String writeTemp( InputStream is ) throws IOException
    {
        File file = File.createTempFile("extract", ".tmp");
        file.deleteOnExit();
        //logger.debug("writing temporary file for text extraction {filename='"+file.getPath()+"'}");
        OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        BufferedInputStream bis = new BufferedInputStream(is);
        int c;
        while ((c = bis.read()) != -1)
        {
            os.write(c);
        }
        os.close();
        return file.getPath();
    }
}
