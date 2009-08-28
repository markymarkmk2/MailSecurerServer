package dimm.home.extraction;

import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import java.io.*;

import org.apache.poi.poifs.eventfilesystem.POIFSReaderEvent;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.eventfilesystem.POIFSReader;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderListener;
import org.apache.poi.util.LittleEndian;
import java.nio.charset.Charset;

public class PowerpointExtractor implements TextExtractor, POIFSReaderListener, Serializable
{

    MandantContext m_ctx;
    private OutputStream output = null;

    public PowerpointExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }

    @Override
    public Reader getText( InputStream is, Charset charset ) throws ExtractionException
    {
        File file = null;
        try
        {
            POIFSReader reader = new POIFSReader();
            file = m_ctx.getTempFileHandler().create_temp_file("PPExtract", "extract", "tmp");
            
            output = new FileOutputStream(file);
            reader.registerListener(this);
            reader.read(is);
        }
        catch (Exception ex)
        {
            throw new ExtractionException("failed to extract text from powerpoint document");
        }
        finally
        {
            if (output != null)
            {
                try
                {
                    output.close();
                }
                catch (IOException ioe)
                {
                }
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

    @Override
    public void processPOIFSReaderEvent( POIFSReaderEvent event )
    {
        try
        {
            if (event.getName().compareToIgnoreCase("PowerPoint Document") != 0)
            {
                return;
            }

            DocumentInputStream input = event.getStream();

            byte[] buffer = new byte[input.available()];
            input.read(buffer, 0, input.available());
            for (int i = 0; i < buffer.length - 20; i++)
            {
                long type = LittleEndian.getUShort(buffer, i + 2);
                long size = LittleEndian.getUInt(buffer, i + 4);
                if (type == 4008L)
                {

                    output.write(buffer, i + 4 + 1, (int) size + 3);
                    i = i + 4 + 1 + (int) size - 1;
                }
            }
        }
        catch (Exception ex)
        {
            // logger.debug(ex.getMessage());
        }
    }
}
