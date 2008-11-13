/*
 * BoxListModel.java
 *
 * Created on 15. Oktober 2007, 10:54
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package dimm.home.mailproxyclient;

import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Administrator
 */
public class BoxListModel extends AbstractTableModel
{
    MainFrame parent;
    String[] name_list = {"Station", "Gerät", "Version", "IP", "OK" };
    Class[] class_list = {String.class, String.class, String.class, String.class, Boolean.class };
    
    /** Creates a new instance of BoxListModel */
    public BoxListModel(MainFrame _parent)
    {
        parent = _parent;
    }
    @Override
    public String getColumnName( int i )
    {
        return name_list[i];
    }

    public int getRowCount()
    {
        return parent.get_station_entries();        
    }

    public int getColumnCount()
    {
        return name_list.length;
    }

    public Object getValueAt(int i, int i0)
    {
        StationEntry ste = parent.get_station( i );
        switch ( i0 )
        {
            case 0: return ste.get_id();
            case 1: return ste.get_name();
            case 2: return ste.get_version();
            case 3: return ste.get_ip();
            case 4: return ste.is_active();
        }
        return null;
    }

    @Override
    public Class<?> getColumnClass(int i )
    {
        return class_list[i];
    }
    
}
