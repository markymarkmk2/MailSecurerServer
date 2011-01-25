

package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import java.nio.charset.Charset;

public class PlainTextExtractor implements TextExtractor, Serializable
{
	

	public PlainTextExtractor()
	{
	}

    @Override
	public Reader getText(InputStream is,  DocumentWrapper doc, Charset charset) throws ExtractionException
	{
	   Reader r = new InputStreamReader(is,charset);
	   return r;
	}


}
