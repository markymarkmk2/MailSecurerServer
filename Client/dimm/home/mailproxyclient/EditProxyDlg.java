/*
 * EdirProxyDlg.java
 *
 * Created on 27. November 2008, 12:42
 */

package dimm.home.mailproxyclient;

/**
 
 @author  Administrator
 */
public class EditProxyDlg extends javax.swing.JDialog
{
    MainFrame parent;
    ProxyEntry pe;
    boolean in_init = false;
    
    /** Creates new form EdirProxyDlg */
    public EditProxyDlg(MainFrame parent, ProxyEntry pe)
    {
        super(parent, true);
        this.parent = parent;
        this.pe = pe;
            
        
        initComponents();
        
        in_init = true;
        
        if (pe.getLocalPort() == 0 || pe.getProtokoll().length() == 0)
        {
            CB_PROTOKOLL.setSelectedIndex(0);
            TXT_LOCALPORT.setText("110");
            TXT_REMOTEPORT.setText("110");
            TXT_HOST.setText("");
        }
        else
        {
            TXT_LOCALPORT.setText(Integer.toString(pe.getLocalPort()));
            TXT_REMOTEPORT.setText(Integer.toString(pe.getRemotePort()));
            TXT_HOST.setText(pe.getHost());
            if (pe.getProtokoll().equals("POP3"))
                CB_PROTOKOLL.setSelectedIndex(0);
            if (pe.getProtokoll().equals("SMTP"))
                CB_PROTOKOLL.setSelectedIndex(1);
            if (pe.getProtokoll().equals("IMAP"))
                CB_PROTOKOLL.setSelectedIndex(2);
        }
        in_init = false;
        
    }
    
    public ProxyEntry get_proxy_entry()
    {
        return pe;
    }
    boolean check_params()
    {
        try
        {
            int lport = Integer.parseInt(TXT_LOCALPORT.getText());
            int rport = Integer.parseInt(TXT_REMOTEPORT.getText());
            String host = TXT_REMOTEPORT.getText();
            if (host.length() == 0)
            {
                parent.errm_ok("Bitte geben sie einen gültigen Hostnamen an");
                return false;
            }
            return true;

        }
        catch (NumberFormatException numberFormatException)
        {
            parent.errm_ok("Bitte geben sie gültige Portnummern an");            
        }
        return false;
    }
    
        
    /** This method is called from within the constructor to
     initialize the form.
     WARNING: Do NOT modify this code. The content of this method is
     always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        jButton1 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        TXT_LOCALPORT = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        TXT_HOST = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        TXT_REMOTEPORT = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        CB_PROTOKOLL = new javax.swing.JComboBox();
        BT_OK = new javax.swing.JButton();
        BT_ABORT = new javax.swing.JButton();

        jButton1.setText("jButton1");

        jButton3.setText("jButton3");

        jLabel2.setText("jLabel2");

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("ProxyParameter");

        jLabel1.setText("LocalPort");

        jLabel3.setText("Server");

        jLabel4.setText("Serverport");

        jLabel5.setText("Protokoll");

        CB_PROTOKOLL.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "POP3", "IMAP", "SMTP" }));
        CB_PROTOKOLL.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CB_PROTOKOLLActionPerformed(evt);
            }
        });
        CB_PROTOKOLL.addPropertyChangeListener(new java.beans.PropertyChangeListener()
        {
            public void propertyChange(java.beans.PropertyChangeEvent evt)
            {
                CB_PROTOKOLLPropertyChange(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel1)
                    .add(jLabel3)
                    .add(jLabel4)
                    .add(jLabel5))
                .add(31, 31, 31)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(TXT_HOST, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 172, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                        .add(org.jdesktop.layout.GroupLayout.LEADING, CB_PROTOKOLL, 0, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .add(org.jdesktop.layout.GroupLayout.LEADING, TXT_LOCALPORT, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE))
                    .add(TXT_REMOTEPORT, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 67, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {CB_PROTOKOLL, TXT_LOCALPORT, TXT_REMOTEPORT}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(CB_PROTOKOLL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(TXT_LOCALPORT, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(TXT_HOST, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel4)
                    .add(TXT_REMOTEPORT, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        BT_OK.setText("Okay");
        BT_OK.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_OKActionPerformed(evt);
            }
        });

        BT_ABORT.setText("Abbruch");
        BT_ABORT.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_ABORTActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(BT_ABORT)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(BT_OK)))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {BT_ABORT, BT_OK}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(BT_OK)
                    .add(BT_ABORT))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BT_OKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_OKActionPerformed
    {//GEN-HEADEREND:event_BT_OKActionPerformed
        // TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_params())
        {
            int lport = Integer.parseInt(TXT_LOCALPORT.getText());
            int rport = Integer.parseInt(TXT_REMOTEPORT.getText());
            String host = TXT_HOST.getText();
            String prot = CB_PROTOKOLL.getSelectedItem().toString();
            
            pe = new ProxyEntry( host, lport, rport, 0, prot );
            
            this.setVisible(false);
            this.dispose();
        }
    }//GEN-LAST:event_BT_OKActionPerformed

    private void BT_ABORTActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_ABORTActionPerformed
    {//GEN-HEADEREND:event_BT_ABORTActionPerformed
        // TODO Ihre Ereignisbehandlung hier einf�gen:
        pe = null;
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_BT_ABORTActionPerformed

    private void CB_PROTOKOLLActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_CB_PROTOKOLLActionPerformed
    {//GEN-HEADEREND:event_CB_PROTOKOLLActionPerformed
        // TODO add your handling code here:
        if (in_init)
            return;
        
        
        String prot = CB_PROTOKOLL.getSelectedItem().toString();
        if (prot.equals("POP3"))
        {
            TXT_LOCALPORT.setText("110");
            TXT_REMOTEPORT.setText("110");
        }
        if (prot.equals("SMTP"))
        {
            TXT_LOCALPORT.setText("25");
            TXT_REMOTEPORT.setText("25");
        }
        if (prot.equals("IMAP"))
        {
            TXT_LOCALPORT.setText("143");
            TXT_REMOTEPORT.setText("143");
        }

            
        
    }//GEN-LAST:event_CB_PROTOKOLLActionPerformed

    private void CB_PROTOKOLLPropertyChange(java.beans.PropertyChangeEvent evt)//GEN-FIRST:event_CB_PROTOKOLLPropertyChange
    {//GEN-HEADEREND:event_CB_PROTOKOLLPropertyChange
        // TODO add your handling code here:
        CB_PROTOKOLLActionPerformed(null);
    }//GEN-LAST:event_CB_PROTOKOLLPropertyChange
    
   
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BT_ABORT;
    private javax.swing.JButton BT_OK;
    private javax.swing.JComboBox CB_PROTOKOLL;
    private javax.swing.JTextField TXT_HOST;
    private javax.swing.JTextField TXT_LOCALPORT;
    private javax.swing.JTextField TXT_REMOTEPORT;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    // End of variables declaration//GEN-END:variables
    
}
