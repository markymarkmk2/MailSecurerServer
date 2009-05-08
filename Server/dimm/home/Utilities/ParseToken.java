package dimm.home.Utilities;


public class ParseToken extends Object
{
    String str;
    int err;
    int from_index;
    boolean _is_ordered;
    
    public boolean is_ordered()
    {
        return _is_ordered;
    }
    
    public void set_ordered( boolean f )
    {
        _is_ordered = f;
    }
    
    public ParseToken( String _str)
    {
        from_index = 0;
        err = 0;
        str = _str;
    }
    
    public String GetString(String token)
    {
        err = 0;
        boolean found_escaped_chars = false;
        int pos = str.indexOf( token, from_index );
        
        if ((pos >= 0) && ((pos + token.length()) < str.length()))
        {
            pos += token.length();
            char delim = str.charAt( pos );
            int end = pos;
            if (delim == '\'' || delim == '\"')
            {
                pos++;
                end = pos;
                // FIND END OF 
                while(end != -1)
                {                
                    end = str.indexOf( delim, end );
                    
                    // IS THIS DELIMITER ESCAPED ?
                    if (str.charAt(end-1) != '\\')
                        break;
                    
                    found_escaped_chars = true;
                    // OK, LOOK FOR NEXT ONE
                    end++;
                }
                if (end == -1)
                {
                    err = 2;                
                    return "";
                }
            }
            else
            {
                end = pos;
                while( end < str.length() )
                {
                    char ch = str.charAt( end );
                    if (ch == ' ')
                        break;
                    if (ch == '\n')
                        break;
                    if (ch == '\t')
                        break;
                    end++;
                }
            }
            // SHIFT STARTINDEX IF WE WANT TO SCAN ORDERED
            if (is_ordered())
                from_index = end;
            
            if (found_escaped_chars)
            {
                StringBuffer sb = new StringBuffer(str.substring( pos, end ));
                int l = sb.length();
                for (int i = 0; i < l; i++)
                {
                    if ((sb.charAt(i) == '\\') && sb.charAt(i+1) != '\\')
                    {
                        sb.replace(i, i+1, "");
                        l = sb.length();
                    }
                }
                return sb.toString();
            }
                
            return str.substring( pos, end );
        }
        err = 1;
        return "";
    }
    public String GetStringDelim(String token, char token_delim)
    {
        err = 0;
        boolean found_escaped_chars = false;
        int pos = str.indexOf( token, from_index );
        
        if ((pos >= 0) && ((pos + token.length()) < str.length()))
        {
            pos += token.length();
            while (pos < str.length() && str.charAt( pos ) == token_delim)
                pos++;
            
            char delim = str.charAt( pos );
            int end = pos;
            if (delim == '\'' || delim == '\"')
            {
                pos++;
                end = pos;
                // FIND END OF 
                while(end != -1)
                {                
                    end = str.indexOf( delim, end );
                    
                    // IS THIS DELIMITER ESCAPED ?
                    if (str.charAt(end-1) != '\\')
                        break;
                    
                    found_escaped_chars = true;
                    // OK, LOOK FOR NEXT ONE
                    end++;
                }
                if (end == -1)
                {
                    err = 2;                
                    return "";
                }
            }
            else
            {
                end = pos;
                while( end < str.length() )
                {
                    char ch = str.charAt( end );
                    if (ch == ' ')
                        break;
                    if (ch == '\n')
                        break;
                    if (ch == '\t')
                        break;
                    end++;
                }
            }
            // SHIFT STARTINDEX IF WE WANT TO SCAN ORDERED
            if (is_ordered())
                from_index = end;
            
            if (found_escaped_chars)
            {
                StringBuffer sb = new StringBuffer(str.substring( pos, end ));
                int l = sb.length();
                for (int i = 0; i < l; i++)
                {
                    if ((sb.charAt(i) == '\\') && sb.charAt(i+1) != '\\')
                    {
                        sb.replace(i, i+1, "");
                        l = sb.length();
                    }
                }
                return sb.toString();
            }
                
            return str.substring( pos, end );
        }
        err = 1;
        return "";
    }
    
    
    public Long GetLong(String token)
    {
        String tmp = GetString(token);
        if (tmp.length() == 0)
            return new Long(0);
        
        return new Long(tmp);
    }
    public long GetLongValue(String token)
    {
        String tmp = GetString(token);
        if (tmp.length() == 0)
            return 0;
        
        return Long.parseLong(tmp);
    }
        
    public Double GetDouble(String token)
    {
        String tmp = GetString(token);
        if (tmp.length() == 0)
            return new Double(0);
        
        return new Double(tmp);
    }
    
    public double GetDoubleValue(String token)
    {
        String tmp = GetString(token);
        if (tmp.length() == 0)
            return 0.0;
        
        return Double.parseDouble(tmp);
    }
        
    
        
    public boolean GetBoolean(String token)
    {
        String tmp = GetString(token);
        if (tmp.length() == 0)
            return false;
        
        if (tmp.charAt(0) == '1')
            return true;
        
        return false;
    }
    
    public int getErr()
    {
        return err;
    }
    
    public String get_remaining_string()
    {
        return str.substring(from_index, str.length());
    }
}

