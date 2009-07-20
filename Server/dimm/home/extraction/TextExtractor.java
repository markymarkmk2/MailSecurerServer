

package dimm.home.extraction;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.nio.charset.Charset;
import java.io.*;

public interface TextExtractor
{
    public abstract Reader getText(InputStream is, Charset charset) throws ExtractionException;
}