/*
 * LoginDialog.java
 *
 * Created on 11. Oktober 2007, 13:47
 */

package dimm.home.mailproxyclient;

/**
 *
 * @author  Administrator
 */
public class LoginDialog extends javax.swing.JDialog
{
    boolean ok;
    
    /** Creates new form LoginDialog */
    public LoginDialog(MainFrame parent)
    {
        super(parent, true);
        initComponents();
    }
    
    String get_pwd()
    {
        if (ok)
        {
            String pwd = new String(TXP_PWD.getPassword());
            return pwd;
        }
        return null;
    }
    @Override
    public void setVisible( boolean b )
    {
        if (b)
        {
            TXP_PWD.setCaretPosition(0);
            TXP_PWD.setText("" );
        }
        super.setVisible( b );
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Erzeugter Quelltext ">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        jLabel1 = new javax.swing.JLabel();
        TXP_PWD = new javax.swing.JPasswordField();
        BT_OK = new javax.swing.JButton();
        BT_ABORT = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Login");
        jLabel1.setText("Passwort");

        TXP_PWD.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                TXP_PWDActionPerformed(evt);
            }
        });

        BT_OK.setText("OK");
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
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(TXP_PWD, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 178, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(BT_ABORT)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(BT_OK)))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {BT_ABORT, BT_OK}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(TXP_PWD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(BT_OK)
                    .add(BT_ABORT))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void TXP_PWDActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TXP_PWDActionPerformed
    {//GEN-HEADEREND:event_TXP_PWDActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        BT_OKActionPerformed( null );
    }//GEN-LAST:event_TXP_PWDActionPerformed

    private void BT_OKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_OKActionPerformed
    {//GEN-HEADEREND:event_BT_OKActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        ok = true;
        this.setVisible( false );
    }//GEN-LAST:event_BT_OKActionPerformed

    private void BT_ABORTActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_ABORTActionPerformed
    {//GEN-HEADEREND:event_BT_ABORTActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        ok = false;
        this.setVisible( false );
    }//GEN-LAST:event_BT_ABORTActionPerformed
    
   
    
    // Variablendeklaration - nicht modifizieren//GEN-BEGIN:variables
    private javax.swing.JButton BT_ABORT;
    private javax.swing.JButton BT_OK;
    private javax.swing.JPasswordField TXP_PWD;
    private javax.swing.JLabel jLabel1;
    // Ende der Variablendeklaration//GEN-END:variables
    
}
