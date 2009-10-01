package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import java.io.*;
import java.nio.charset.Charset;
import org.apache.pdfbox.encryption.DocumentEncryption;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class PDFExtractor implements TextExtractor, Serializable
{
    MandantContext m_ctx;

    public PDFExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }


    @Override
    public Reader getText( InputStream is, DocumentWrapper doc, Charset charset ) throws ExtractionException
    {

        File file = null;
        PDDocument document = null;
/*        File pdf_file = null;
        FileInputStream fis = null;*/
        try
        {
           /* pdf_file = m_ctx.getTempFileHandler().writeTemp("PDFExtract", "extract", "txt", is);
            fis = new FileInputStream( pdf_file );*/

            PDFParser parser = new PDFParser(is);
            parser.parse();
            document = parser.getPDDocument();
            if (document.isEncrypted())
            {
                DocumentEncryption decryptor = new DocumentEncryption(document);
                decryptor.decryptDocument("");
            }
            file = m_ctx.getTempFileHandler().create_temp_file("PDFExtract", "extract", "txt");

            Writer output = null;
            output = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.writeText(document, output);
            output.close();
        }
        catch (Exception e)
        {
            throw new ExtractionException("Failed to parse PDF",  e);
        }
        finally
        {
            try
            {
                if (document != null)
                {
                    document.close();
                }
           /*     if (pdf_file != null)
                {
                     m_ctx.getTempFileHandler().delete( pdf_file );
                }*/
            }
            catch (IOException io)
            {
            }
        }
        try
        {
            return new FileReader(file);
        }
        catch (Exception ex)
        {
            throw new ExtractionException("Failed to strip text from PDF", ex);
        }
    }
}


