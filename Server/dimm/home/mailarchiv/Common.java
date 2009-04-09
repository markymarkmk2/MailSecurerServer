package dimm.home.mailarchiv;

import java.awt.Component;
import java.awt.Container;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * The Common class contains general static methods that
 * can be used 
 * 
 * @version 1.00, 05/05/12
 * @author Jocca Jocaf
 *
 */
public final class Common {

    // Constants    
	public static final String LINE_FEED = System.getProperty( "line.separator" );	// line separator of the operational system

	public static final String APP_TITLE = "JSpamAssassin";
	public static final String APP_VERSION ="1.0.0";
	
	public static Component m_mainWindow;									// main window to show the messages
	
	/**
     * Initializes the logger engine.
     * 
     * @param filename The log file to set.
     */
	public static void initializeLogger(String filename)
	{
//    	MoreLog.initialize(filename);
//        MoreLog.setLogger(MoreLog.class);
	}
	
	 public static final void showMSG ( String text) {
	 	showMSG( m_mainWindow, text);
	 }
	 
	 public static final void showMSG (Object Vater, String text) {
	 	  Component parent=null;

	 	   if (isComponent (Vater))
	 	      parent=(Component) Vater;

	 	        JOptionPane.showMessageDialog (parent,
	 	          text,
	 	          APP_TITLE,
	 	          JOptionPane.INFORMATION_MESSAGE
	 	     );
	 	 }
	 public static final void showError ( String text) {
	 	showError(m_mainWindow,text);
	 }
	 
	 public static final void showError (Object Vater, String text) {
	 	  Component parent=null;

	 	   if (isComponent (Vater))
	 	      parent=(Component) Vater;

	 	        JOptionPane.showMessageDialog (parent,
	 	          text,
	 	          APP_TITLE,
	 	          JOptionPane.ERROR_MESSAGE
	 	     );
	 	 }
	 
	 public static final int showQuestion (String text) {
	 	return showQuestion (m_mainWindow , text);
	 }
	 
	 public static final int showQuestion (Object Vater, String text) {
	 	  int Antwort;
	 	  Component parent=null;

	 	   if (isComponent (Vater))
	 	      parent=(Component) Vater;

	 	  Antwort= JOptionPane.showConfirmDialog (parent,
	 	          text,
	 	          APP_TITLE,
	 	          JOptionPane.YES_NO_OPTION,
	 	          JOptionPane.QUESTION_MESSAGE
	 	     );
	 	   return Antwort;
	 	 }
	 
	 private static boolean isComponent (Object obj) {
	 	  if (obj instanceof Component)
	 	     return true;
	 	  else
	 	     return false;
	 	 }
	 
	 /**
	  * Verifies if a file or directory exists.
	 * @param sPath Directory path or filename path.
	 * @return true, if it exists. Otherwise, false.
	 */
	public static final boolean DirFileExists (String sPath)
	 {
	   File fl = new File (sPath);
	   return fl.exists();
	 }

	 /**
	  * Verifies if a file or directory exists.
	 * @param sPath Directory path or filename path.
	 * @return true, if it exists. Otherwise, false.
	 */
	public static final void setMainWindow (Component obj)
	 {
		m_mainWindow = obj;
	 }
	

	public  static final void setAllComponentsEnabled (Container pContainer, boolean status) {
	  setAllComponentsEnabled (pContainer,false,false,status);
	}

	public  static final void setAllComponentsEnabled (Container pContainer, 
	                                                    boolean bLabels,
	                                                    boolean bButtons,
	                                                    boolean status) {
	  // Diese Prozedur sperrt oder nicht die Kontrolle
	  Component x;
	  JScrollPane jspAux;

	   for (int i=0 ; i < pContainer.getComponentCount() ; i++)
	    {
	      x = pContainer.getComponent(i);
//	      System.out.println (x.toString ());

	      // wenn es ein JTextArea, JList, u.A , die in einem JScrollPane sind, dann
	      // man kriegt die Kontrolle, die dahin gesteckt ist
	      if ( x instanceof JScrollPane)
	       {
	         jspAux = (JScrollPane)x;
	         x = jspAux.getViewport().getComponent(0) ;
	       } else if ( x instanceof JPanel) 
	       {
	         JPanel jpAux = (JPanel)x;
	         setAllComponentsEnabled (jpAux,status);
	       }

	      // man sperrt oder laesst die Kontrolle frei
	      if ( (x instanceof JLabel) && bLabels) 
	          x.setEnabled (status);
	      else if ( (x instanceof JButton) && bButtons )
	         x.setEnabled (status);
	      else
	         x.setEnabled (status);

	    }
	 } // setAllComponentsEnabled
	
}  // Common
