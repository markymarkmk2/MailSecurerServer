

package dimm.home.extraction;
import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.nio.charset.Charset;
import java.io.*;

public interface TextExtractor
{
    public abstract Reader getText(InputStream is, DocumentWrapper doc, Charset charset) throws ExtractionException;
}