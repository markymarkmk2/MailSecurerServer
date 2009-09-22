package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

public class ExcelExtractor implements TextExtractor, Serializable
{

    public ExcelExtractor()
    {
    }

    @Override
    public Reader getText( InputStream is, DocumentWrapper doc, Charset charset ) throws ExtractionException
    {
        try
        {
            POIFSFileSystem fs;
            HSSFWorkbook workbook;
            fs = new POIFSFileSystem(is);
            workbook = new HSSFWorkbook(fs);
            StringBuilder builder = new StringBuilder();
            for (int numSheets = 0; numSheets < workbook.getNumberOfSheets(); numSheets++)
            {
                HSSFSheet sheet = workbook.getSheetAt(numSheets);
                Iterator rows = sheet.rowIterator();
                while (rows.hasNext())
                {
                    HSSFRow row = (HSSFRow) rows.next();
                    Iterator cells = row.cellIterator();
                    while (cells.hasNext())
                    {
                        HSSFCell cell = (HSSFCell) cells.next();
                        processCell(cell, builder);
                    }
                }
            }
            return new StringReader(builder.toString());
        }
        catch (Exception ee)
        {
            throw new ExtractionException("failed to extract excel document: " + ee.getMessage());
        }

    }

    private void processCell( HSSFCell cell, StringBuilder builder )
    {
        switch (cell.getCellType())
        {
            case HSSFCell.CELL_TYPE_STRING:
                builder.append(cell.getStringCellValue());
                builder.append(" ");
                break;
            default:
                break;
        }
    }
}
