/*
 * LogDlg.java
 *
 * Created on 9. Oktober 2007, 20:31
 */

package dimm.home.mailproxyclient;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.util.StringTokenizer;
import javax.swing.JFileChooser;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/**
 *
 * @author  Administrator
 */
public class LogDlg extends javax.swing.JDialog  implements ActionListener
{    
    MainFrame parent;    
    Timer timer;
    String last_line;
    int sq = 0;
    boolean in_init = false;
    
    /** Creates new form LogDlg */
    public LogDlg(MainFrame _parent)
    {
        super(_parent, true);
        
        in_init = true;
        
        
        parent = _parent;
        
        initComponents();
        
        if (parent.get_is_udp())
        {
            this.BT_GETLOG.setEnabled(false);
        }
        
        SP_LINES.setModel( new SpinnerNumberModel(100, 0, 10000, 100)  );
        
        CB_LOG.removeAllItems();
        CB_LOG.addItem( "Info" );
        CB_LOG.addItem( "Warnungen" );
        CB_LOG.addItem( "Fehler" );
        CB_LOG.addItem( "Debug" );
        CB_LOG.addItem( "System" );
        
        in_init = false;
        
        reread_log();        
                
        timer = new Timer( 3000, this );
        timer.start();
        
    }

    void reread_log()
    {
        int lines = ((Integer)SP_LINES.getValue()).intValue();
        
        String txt = reread_log(lines);
        
        StringBuffer sb = new StringBuffer();
        StringTokenizer sto = new StringTokenizer( txt, "\n\r");
        
        while (sto.hasMoreTokens())
        {
            String str  = sto.nextToken() + "\n";
            //System.out.println( "Line: " + str );
            sb.insert(0, str );            
            if (!sto.hasMoreTokens())
                last_line = new String(str);
        }
        //System.out.println( "Len: " + sb.length() );
        
        TXT_LOG.setText( sb.toString());
        TXT_LOG.setCaretPosition(0);
    }
    
    String reread_log(int lines)
    {
        int idx = CB_LOG.getSelectedIndex();
        String log = "info.log";
        if (idx == 1)
            log = "warn.log";
        if (idx == 2)
            log = "error.log";
        if (idx == 3)
            log = "debug.log";
        if (idx == 4)
            log = "messages";
                    
        String cmd = "READLOG LOG:" + log + " LINES:" + Integer.toString(lines/* + sq++*/);
        
        parent.send_cmd( cmd );
        
        return parent.get_answer();        
    }
        
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        jLabel1 = new javax.swing.JLabel();
        CB_LOG = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        SP_LINES = new javax.swing.JSpinner();
        jScrollPane1 = new javax.swing.JScrollPane();
        TXT_LOG = new javax.swing.JTextArea();
        BT_OK = new javax.swing.JButton();
        BT_GETLOG = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Log Dateien ansehen");
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosed(java.awt.event.WindowEvent evt)
            {
                formWindowClosed(evt);
            }
        });

        jLabel1.setText("Logdatei");

        CB_LOG.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Eintrag 1", "Eintrag 2", "Eintrag 3", "Eintrag 4" }));
        CB_LOG.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CB_LOGActionPerformed(evt);
            }
        });

        jLabel2.setText("Zeilen");

        TXT_LOG.setColumns(20);
        TXT_LOG.setEditable(false);
        TXT_LOG.setFont(new java.awt.Font("Monospaced", 0, 12));
        TXT_LOG.setRows(5);
        jScrollPane1.setViewportView(TXT_LOG);

        BT_OK.setText("Schliessen");
        BT_OK.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_OKActionPerformed(evt);
            }
        });

        BT_GETLOG.setText("Log Download (TCP/IP)");
        BT_GETLOG.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_GETLOGActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(CB_LOG, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 117, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jLabel2)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(SP_LINES, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 65, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(BT_GETLOG)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 152, Short.MAX_VALUE)
                        .add(BT_OK)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(CB_LOG, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel2)
                    .add(SP_LINES, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 221, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(BT_OK)
                    .add(BT_GETLOG))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BT_GETLOGActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_GETLOGActionPerformed
    {//GEN-HEADEREND:event_BT_GETLOGActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (in_init)
            return;
        
        JFileChooser jf = new JFileChooser();
        jf.setDialogType(JFileChooser.SAVE_DIALOG);
        jf.setSelectedFile( new File( Main.SERVERAPP + "_" + parent.get_selected_box().get_id() + ".tgz") );
        jf.showSaveDialog( this );
        
        File f = jf.getSelectedFile();
        if (f == null)
            return;

        if (!f.getName().endsWith(".tgz"))
            f = new File(f.getAbsolutePath() + ".tgz");
        
            
        String cmd;
        if (parent.errm_ok_cancel("Wollen Sie die Log-Dateien anschliessend von der Box entfernen?"))
            cmd = "GETLOG DEL:1";
        else
            cmd = "GETLOG DEL:0";
                
        try
        {
            FileOutputStream fos = new FileOutputStream( f );            
        
            parent.send_cmd( cmd, fos  );
            
            fos.close();
        }
        catch (Exception exc )
        {
            parent.errm_ok("Daten konnten nicht geholt werden: " + exc.getMessage() );
        }

          
    }//GEN-LAST:event_BT_GETLOGActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowClosed
    {//GEN-HEADEREND:event_formWindowClosed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        timer.stop();
    }//GEN-LAST:event_formWindowClosed

    private void CB_LOGActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_CB_LOGActionPerformed
    {//GEN-HEADEREND:event_CB_LOGActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (in_init)
            return;
        
        reread_log();
    }//GEN-LAST:event_CB_LOGActionPerformed

    private void BT_OKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_OKActionPerformed
    {//GEN-HEADEREND:event_BT_OKActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        this.setVisible( false );
        timer.stop();
        this.dispose();
    }//GEN-LAST:event_BT_OKActionPerformed

    public void actionPerformed(ActionEvent actionEvent)
    {
        timer.stop();
        String act_last_line = "";
        
        String txt = reread_log(5);
        
        StringTokenizer sto = new StringTokenizer( txt, "\n\r");
        
        while (sto.hasMoreTokens())
        {
            String str = sto.nextToken() + "\n";
            if (!sto.hasMoreTokens())
                act_last_line = new String(str);
            
        }
        
        if (act_last_line.compareTo( last_line ) != 0)
        {
            reread_log();
        }
        timer.start();
    }
    
  
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BT_GETLOG;
    private javax.swing.JButton BT_OK;
    private javax.swing.JComboBox CB_LOG;
    private javax.swing.JSpinner SP_LINES;
    private javax.swing.JTextArea TXT_LOG;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
    
}
