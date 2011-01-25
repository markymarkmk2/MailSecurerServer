
package dimm.home.extraction;

import dimm.home.index.DocumentWrapper;
import dimm.home.mailarchiv.Exceptions.ExtractionException;
import dimm.home.mailarchiv.MandantContext;
import home.shared.CS_Constants;
import home.shared.mail.RFCMimeMail;
import java.io.*;
import java.nio.charset.Charset;

import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;

public class EMLExtractor implements TextExtractor, Serializable
{
    MandantContext m_ctx;

    public EMLExtractor( MandantContext m_ctx )
    {
        this.m_ctx = m_ctx;
    }

    @Override
    public Reader getText( InputStream is, DocumentWrapper doc, Charset charset ) throws ExtractionException
    {
        try
        {
            RFCMimeMail mail = new RFCMimeMail();
            mail.parse(is);

            String unique_id = doc.get(CS_Constants.FLD_UID_NAME);

            MimeMessage msg = mail.getMsg();
            Object content = msg.getContent();
            if (content instanceof Multipart)
            {
                Multipart mp = (Multipart) content;

                m_ctx.get_index_manager().index_mp_content(doc, unique_id, mp);
            }
            else if (content instanceof Part)
            {
                Part p = (Part) content;
                m_ctx.get_index_manager().index_part_content(doc, unique_id, p);
            }

            String s = msg.getSubject();
            if (s == null)
                s = "";

            return new StringReader(s);
        }
        catch (Exception io)
        {
            throw new ExtractionException("failed to extract text from mail document:" + io.getMessage());
        }
    }
}
