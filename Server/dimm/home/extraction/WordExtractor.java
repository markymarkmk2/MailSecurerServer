
package dimm.home.extraction;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import java.io.*;
import java.nio.charset.Charset;

import org.textmining.extraction.word.*;

public class WordExtractor implements TextExtractor, Serializable
{
    MandantContext m_ctx;

    public WordExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }

    @Override
    public Reader getText( InputStream is, Charset charset ) throws ExtractionException
    {
        try
        {
            File file = m_ctx.getTempFileHandler().create_temp_file("WordExtract", "extract", "tmp");
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            WordTextExtractorFactory wtef = new WordTextExtractorFactory();
            wtef.textExtractor(is).getText(out);
            out.close();
            return new InputStreamReader(new FileInputStream(file));
        }
        catch (Exception io)
        {
            throw new ExtractionException("failed to extract text from word document:" + io.getMessage());
        }
    }
}
