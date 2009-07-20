

package dimm.home.extraction;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import javax.swing.text.rtf.RTFEditorKit;

import javax.swing.text.DefaultStyledDocument;
import java.nio.charset.Charset;

public class RTFExtractor implements TextExtractor,Serializable
{


	public RTFExtractor() {}

    @Override
	public Reader getText(InputStream is,  Charset charset) throws ExtractionException {
	    
	        Reader reader = null;
	        FileWriter writer = null;
	        File file = null;
	        try {
	            reader = new InputStreamReader(is);
	            file = File.createTempFile("extract", ".tmp");
	            file.deleteOnExit();
	            writer = new FileWriter(file);
	            DefaultStyledDocument doc = new DefaultStyledDocument();
	            new RTFEditorKit().read(reader, doc, 0);
	            writer.write(doc.getText(0, doc.getLength()));
	        } catch (Exception ioe)
                {
	            throw new ExtractionException("failed to parse rtf document");
	        }
	        finally {
	            if (reader != null) {
	                try {
	                    reader.close();
	                } catch (IOException ioe) {}
	            }

	            if (writer != null) {
	                try {
	                    writer.close();
	                } catch (IOException ioe) {}
	            }
	        }
	        try {
		        return new FileReader(file);
		    } catch(Exception ex) {
		        throw new ExtractionException("failed to extract text from powerpoint document");
		    }
	        
	    }

}
