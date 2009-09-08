package dimm.home.index;

import org.apache.lucene.analysis.*;
import java.io.*;

public class EmailAnalyzer extends Analyzer implements Serializable
{
    public class LowercaseDelimiterAnalyzer extends Analyzer
    {
        private final char fDelim;

        public LowercaseDelimiterAnalyzer( char delim )
        {
            fDelim = delim;
        }

        @Override
        public TokenStream tokenStream( String fieldName, Reader reader )
        {
            TokenStream result = new CharTokenizer(reader)
            {
                @Override
                protected boolean isTokenChar( char c )
                {
                    return c != fDelim;
                }
            };
            result = new LowerCaseFilter(result);
            return result;
        }
    }

    @Override
    public final TokenStream tokenStream( String fieldName, final Reader reader )
    {
        TokenStream result = new LowercaseDelimiterAnalyzer(',').tokenStream(fieldName, reader);
        result = new EmailFilter(result);
        return result;
    }
}

