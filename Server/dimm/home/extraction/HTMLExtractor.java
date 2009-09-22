
package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import java.nio.charset.Charset;

public class HTMLExtractor implements TextExtractor
{

    public HTMLExtractor()
    {
    }

    @Override
    public Reader getText( InputStream is, DocumentWrapper doc, Charset charset ) throws ExtractionException
    {
        return new RemoveHTMLReader(new InputStreamReader(is, charset));
    }

    class RemoveHTMLReader extends FilterReader
    {

         public RemoveHTMLReader( Reader in )
        {
            super(in);
        }
        boolean intag = false;    

        @Override
        public int read( char[] buf, int from, int len ) throws IOException
        {
            int numchars = 0;

            while (numchars == 0)
            {
                numchars = in.read(buf, from, len);    
                if (numchars == -1)
                {
                    return -1;          
                }		      
                int last = from;                          
                for (int i = from; i < from + numchars; i++)
                {
                    if (!intag)
                    {                           
                        if (buf[i] == '<')
                        {
                            intag = true;      
                        }
                        else
                        {
                            buf[last++] = buf[i];
                        }
                    }
                    else if (buf[i] == '>')
                    {
                        intag = false;  
                    }
                }
                numchars = last - from; 
            }                           
            return numchars;            
        }

        @Override
        public int read() throws IOException
        {
            char[] buf = new char[1];
            int result = read(buf, 0, 1);
            if (result == -1)
            {
                return -1;
            }
            else
            {
                return buf[0];
            }
        }
    }
}


