/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.hibernate.Mandant;

/**
 *
 * @author mw
 */
public class Notification
{
    public static final int NF_INFORMATIVE = 1;
    public static final int NF_WARNING = 2;
    public static final int NF_ERROR = 3;
    public static final int NF_FATAL_ERROR = 4;

    public static void throw_notification( Mandant m, int lvl, String t )
    {
        System.out.println( "Notification for " + m.getName() + " Level " + lvl + ": " + t );
    }
}
