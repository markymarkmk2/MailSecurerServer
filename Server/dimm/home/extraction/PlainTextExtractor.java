

package dimm.home.extraction;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import java.nio.charset.Charset;
import org.apache.lucene.document.Document;

public class PlainTextExtractor implements TextExtractor, Serializable
{
	

	public PlainTextExtractor()
	{
	}

    @Override
	public Reader getText(InputStream is,  Document doc, Charset charset) throws ExtractionException
	{
	   Reader r = new InputStreamReader(is,charset);
	   return r;
	}


}
