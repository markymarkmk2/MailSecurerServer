

package dimm.home.extraction;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.nio.charset.Charset;
import java.io.*;
import org.apache.lucene.document.Document;

public interface TextExtractor
{
    public abstract Reader getText(InputStream is, Document doc, Charset charset) throws ExtractionException;
}