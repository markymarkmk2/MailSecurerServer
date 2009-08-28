package dimm.home.extraction;

import org.pdfbox.encryption.DocumentEncryption;
import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.util.PDFTextStripper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import java.io.*;
import java.nio.charset.Charset;

public class PDFExtractor implements TextExtractor, Serializable
{
    MandantContext m_ctx;

    public PDFExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }


    @Override
    public Reader getText( InputStream is, Charset charset ) throws ExtractionException
    {

        File file = null;
        PDDocument document = null;
        try
        {
            PDFParser parser = new PDFParser(is);
            parser.parse();
            document = parser.getPDDocument();
            if (document.isEncrypted())
            {
                DocumentEncryption decryptor = new DocumentEncryption(document);
                decryptor.decryptDocument("");
            }
            file = m_ctx.getTempFileHandler().create_temp_file("PDFExtract", "extract", "tmp");

            Writer output = null;
            output = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.writeText(document, output);
            output.close();
        }
        catch (Exception e)
        {
            throw new ExtractionException("failed to extract pdf (probable password protected document)");
        }
        finally
        {
            try
            {
                if (document != null)
                {
                    document.close();
                }
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
            throw new ExtractionException("failed to extract text from powerpoint document");
        }
    }
}


