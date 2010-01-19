package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.io.*;


import java.nio.charset.Charset;

public class OOExtractor implements TextExtractor, Serializable
{
    MandantContext m_ctx;

    public OOExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }


  
    public void processElement( StringBuffer textBuffer, Object o )
    {

        if (o instanceof Element)
        {

            Element e = (Element) o;
            String elementName = e.getQualifiedName();

            if (elementName.startsWith("text"))
            {

                if (elementName.equals("text:tab")) // add tab for text:tab
                {
                    textBuffer.append("\t");
                }
                else if (elementName.equals("text:s"))  // add space for text:s
                {
                    textBuffer.append(" ");
                }
                else
                {
                    List children = e.getContent();
                    Iterator iterator = children.iterator();

                    while (iterator.hasNext())
                    {

                        Object child = iterator.next();
                        //If Child is a Text Node, then append the text
                        if (child instanceof Text)
                        {
                            Text t = (Text) child;
                            textBuffer.append(t.getValue());
                        }
                        else
                        {
                            processElement(textBuffer, child); // Recursively process the child element
                        }
                    }
                }
                if (elementName.equals("text:p"))
                {
                    textBuffer.append("\n");
                }
            }
            else
            {
                List non_text_list = e.getContent();
                Iterator it = non_text_list.iterator();
                while (it.hasNext())
                {
                    Object non_text_child = it.next();
                    processElement(textBuffer, non_text_child);
                }
            }
        }
    }

    @Override
    public Reader getText( InputStream is, DocumentWrapper ldoc, Charset charset ) throws ExtractionException
    {
        File f = null;
        try
        {
            StringBuffer textBuffer = new StringBuffer();


            f = m_ctx.getTempFileHandler().writeTemp("Extract", "OO", "tmp", is, /*del_on_exit*/true);
            ZipFile zipFile = new ZipFile( f );
            Enumeration entries = zipFile.entries();
            ZipEntry entry;
            while (entries.hasMoreElements())
            {
                entry = (ZipEntry) entries.nextElement();
                if (entry.getName().equals("content.xml"))
                {
                    textBuffer = new StringBuffer();
                    SAXBuilder sax = new SAXBuilder();
                    InputStream zis = zipFile.getInputStream( entry);
                    Document doc = sax.build( zis );
                    Element rootElement = doc.getRootElement();
                    processElement(textBuffer, rootElement);
                    break;
                }
            }
            m_ctx.getTempFileHandler().delete(f);
            return new StringReader(textBuffer.toString());
        }
        catch (Exception io)
        {
            if (f != null && f.exists())
            {
                f.delete();
            }

            throw new ExtractionException("failed to extract text from open office:" + io.getMessage());
        }
    }
}
