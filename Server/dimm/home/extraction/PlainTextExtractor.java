

package dimm.home.extraction;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import java.nio.charset.Charset;

public class PlainTextExtractor implements TextExtractor, Serializable
{
	

	public PlainTextExtractor()
	{
	}

    @Override
	public Reader getText(InputStream is,  Charset charset) throws ExtractionException
	{
	   Reader r = new InputStreamReader(is,charset);
	   return r;
	}


}
