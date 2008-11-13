package dimm.home.mailproxyclient.Utilities;

import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;

public class StandardTable extends JTable
{
    public final static int TABLE_TEXT_SIZE = 12;
    public final static int MIN_THD_SIZE = 20;
    
    public StandardTable()
    {
        super();

/*        setShowGrid(false);
        setShowVerticalLines(false);
        setShowHorizontalLines(false);
        setCellSelectionEnabled(false);
        setColumnSelectionAllowed(false);
 */
        setRowSelectionAllowed(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // FIX BROKEN JAVA HEADER SIZE
        javax.swing.table.JTableHeader thd = this.getTableHeader();
        thd.setPreferredSize( new java.awt.Dimension(thd.getPreferredSize().width, MIN_THD_SIZE) );
        
        setFont( new java.awt.Font("Dialog", java.awt.Font.PLAIN, TABLE_TEXT_SIZE ) );
        
    }
    // JLIIST BUGFIX FOR ERROR IN BACKGROUND IN JTABLE
    /**
     * If this JList is displayed in a JViewport, don't change its width
     * when the viewports width changes. This allows horizontal
     * scrolling if the JViewport is itself embedded in a JScrollPane.
     *
     * @return False - don't track the viewports width.
     * @see Scrollable#getScrollableTracksViewportWidth
     */
    public boolean getScrollableTracksViewportWidth()
    {
        if (getParent() instanceof JViewport)
        {
            return (((JViewport)getParent()).getWidth() >
            getPreferredSize().width);
        }
        return false;
    }
    
    /**
     * If this JList is displayed in a JViewport, don't change its height
     * when the viewports height changes. This allows vertical
     * scrolling if the JViewport is itself embedded in a JScrollPane.
     *
     * @return False - don't track the viewports width.
     * @see Scrollable#getScrollableTracksViewportWidth
     */
    public boolean getScrollableTracksViewportHeight()
    {
        if (getParent() instanceof JViewport)
        {
            return (((JViewport)getParent()).getHeight() >
            getPreferredSize().height);
        }
        return false;
    }
}

