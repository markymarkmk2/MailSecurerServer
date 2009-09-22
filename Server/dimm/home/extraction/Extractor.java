package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.util.*;
import java.nio.charset.Charset;
import java.io.*;

public class Extractor implements Serializable
{
    MandantContext m_ctx;
    protected Map<String, TextExtractor> handlers;

    public Extractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
        handlers = new HashMap<String, TextExtractor>();
        TextExtractor plain = new PlainTextExtractor();
        handlers.put("text/plain", plain);
        handlers.put("txt", plain);
        TextExtractor html = new HTMLExtractor();
        handlers.put("text/html", html);
        handlers.put("html", html);
        TextExtractor pdf = new PDFExtractor(m_ctx);
        handlers.put("application/pdf", pdf);
        handlers.put("pdf", pdf);
        TextExtractor word = new WordExtractor(m_ctx);
        handlers.put("application/msword", word);
        handlers.put("application/vnd.ms-word", word);
        handlers.put("application/vnd.msword", word);
        handlers.put("doc", word);
        TextExtractor excel = new ExcelExtractor();
        handlers.put("application/excel", excel);
        handlers.put("application/msexcel", excel);
        handlers.put("application/vnd.ms-excel", excel);
        handlers.put("xls", excel);
        TextExtractor ppt = new PowerpointExtractor(m_ctx);
        handlers.put("application/vnd.ms-powerpoint", ppt);
        handlers.put("application/mspowerpoint", ppt);
        handlers.put("application/powerpoint", ppt);
        handlers.put("ppt", ppt);
        TextExtractor rtf = new RTFExtractor(m_ctx);
        handlers.put("application/rtf", rtf);
        handlers.put("rtf", rtf);
        TextExtractor oo = new OOExtractor(m_ctx);
        handlers.put("application/vnd.oasis.opendocument.text", oo);
        handlers.put("application/vnd.oasis.opendocument.spreadsheet", oo);
        handlers.put("application/vnd.oasis.opendocument.presentation", oo);
        handlers.put("odt", oo);
        handlers.put("ods", oo);
        handlers.put("odp", oo);
        TextExtractor eml = new EMLExtractor(m_ctx);
        handlers.put("eml", eml);
        handlers.put("msg", eml);

    }




    public Reader getText( InputStream is, DocumentWrapper doc, String mimetype, Charset fromCharset ) throws ExtractionException
    {
        TextExtractor extractor;
        extractor = handlers.get(mimetype.toLowerCase(Locale.ENGLISH));
        if (extractor == null)
        {
            LogManager.err_log_fatal("Cannot get text handler from document of this type: " + mimetype);
            return new StringReader("");
        }
        else
        {
            try
            {
                return extractor.getText(is, doc, fromCharset);
            }
            catch (Exception ee)
            {
                throw new ExtractionException("Extraction failed from document of this type: " + mimetype);
//                return new StringReader("");
            }
        }
    }

    
}
