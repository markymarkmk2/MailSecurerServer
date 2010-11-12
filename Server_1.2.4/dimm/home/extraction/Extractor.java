package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.GeneralPreferences;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Extractor implements Serializable, Runnable
{
    MandantContext m_ctx;
    protected Map<String, TextExtractor> handlers;

    int extr_timeout_s = 60;

    ThreadPoolExecutor service;
    

    public Extractor( MandantContext m_ctx )
    {

        service = m_ctx.getThreadWatcher().create_blocking_thread_pool("Extractor", 4, 10);
//        OfferBlockingQueue<Runnable> run_queue = new OfferBlockingQueue<Runnable>(1);
//        service = new ThreadPoolExecutor(4, 4, 10, TimeUnit.MINUTES, run_queue);
        //service =  Executors.newSingleThreadExecutor();

        extr_timeout_s = (int)Main.get_long_prop(GeneralPreferences.INDEX_TIMEOUT, 180);
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



    public static Map<String,String> mime_not_supported_list = Collections.synchronizedMap(new HashMap<String,String>());

    public Reader getText( final InputStream is, final DocumentWrapper doc, final String mimetype, final Charset fromCharset ) throws ExtractionException
    {
        
        final TextExtractor extractor = handlers.get(mimetype.toLowerCase(Locale.ENGLISH));
        if (extractor == null)
        {
            if (!mime_not_supported_list.containsKey(mimetype))
            {
                LogManager.msg_extract( LogManager.LVL_DEBUG, "Cannot get text handler from document of this type: " + mimetype);
                mime_not_supported_list.put(mimetype, null);
            }
            return new StringReader("");
        }
        else
        {
            try
            {
                LogManager.msg_extract( LogManager.LVL_VERBOSE, "Extracting with extractor " + extractor.getClass().getName());
                

                Future<Object> result = service.submit(new Callable<Object>() {

                    @Override
                    public Object call() throws Exception
                    {
                       try
                       {
/*                           if (extractor == null)
                               extractor = null;
                           if (is == null)
                               extractor = extractor;
                           if (doc == null)
                               extractor = extractor;
*/
                            Reader _rdr =  extractor.getText(is, doc, fromCharset);
                            return _rdr;
                       }
                       catch (Exception ee)
                       {
                           LogManager.msg_extract( LogManager.LVL_WARN, "Extraction error: ",  ee );
                           return ee;
                       }
                    }
                });


                Object o = result.get(10, TimeUnit.MINUTES);
                

                if (o != null)
                {
                    if ( o instanceof Reader)
                    {
                        return (Reader)o;
                    }
                    if ( o instanceof Exception)
                    {
                        Exception ex = (Exception) o;
                        throw new ExtractionException("Extraction failed from document of this type: " + mimetype + ": " + ex.getMessage());
                    }
                }
                throw new ExtractionException("Extraction failed from document of this type: " + mimetype);
            }
            catch (Exception ee)
            {
                throw new ExtractionException("Extraction failed from document of this type: " + mimetype, ee);
//                return new StringReader("");
            }
        }
    }

    @Override
    public void run()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    
}
