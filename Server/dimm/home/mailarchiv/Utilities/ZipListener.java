/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

/**
 *
 * @author Administrator
 */
public interface ZipListener 
{
    public static final int ST_STARTED = 0;
    public static final int ST_BUSY = 1;
    public static final int ST_READY = 2;
    public static final int ST_ERROR = 3;
    
    void act_file_name( String name );
    void total_percent( int pc );
    void file_percent( int pc );
    void new_status( int code, String st );

}
