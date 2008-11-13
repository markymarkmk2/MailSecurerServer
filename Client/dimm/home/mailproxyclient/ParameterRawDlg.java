package dimm.home.mailproxyclient;

/*
 * ParameterRawDlg.java
 *
 * Created on 9. Oktober 2007, 18:32
 */



import java.util.StringTokenizer;

/**
 *
 * @author  Administrator
 */
public class ParameterRawDlg extends javax.swing.JDialog
{
    MainFrame parent;
    static boolean warned = false;
    boolean in_init;
    
    /** Creates new form ParameterRawDlg */
    public ParameterRawDlg(MainFrame _parent)
    {
        super(_parent, true);
        parent = _parent;
        
        initComponents();
        
        get_params();
    }
    
    void get_params()
    {
        CB_VAR.removeAllItems();
        
        String txt = this.CB_VAR.getEditor().getItem().toString();
        
        if (parent.send_cmd("LISTOPTIONS"))
        {
            in_init = true;
            StringTokenizer sto = new StringTokenizer( parent.get_answer(), "\n" );
            while (sto.hasMoreTokens() )
            {
                CB_VAR.addItem( sto.nextToken() );
            }
            in_init = false;
        }
    }

    void get_param()
    {
        if (in_init)
            return;
                    
        String txt = this.CB_VAR.getEditor().getItem().toString();
        if (txt.length() > 0)
        {
            if (parent.send_cmd("GETSETOPTION CMD:GET NAME:" + txt))
            {
               TXT_VAL.setText( parent.get_answer() );
               TXT_NEWVAL.setText( "" );
            }
        }
    }
    void set_param()
    {
        if (!warned)
        {
            parent.errm_ok("Bitte setzen Sie keine Variablen ohne deren Bedeutung zu kennen, Sie k�nnten die Funktionn der Box gef�hrden!");
            warned = true;
            return;
        }
            
        
        String txt = this.CB_VAR.getEditor().getItem().toString();
        String val = TXT_NEWVAL.getText();
        
        if (parent.send_cmd("GETSETOPTION CMD:SET NAME:" + txt + " VAL:" + val))
        {
            get_param();
        }
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
        CB_VAR = new javax.swing.JComboBox();
        TXT_VAL = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        BT_SET = new javax.swing.JButton();
        BT_OK = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        TXT_NEWVAL = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Parameter setzen");
        jLabel1.setText("Parameter");

        CB_VAR.setEditable(true);
        CB_VAR.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Eintrag 1", "Eintrag 2", "Eintrag 3", "Eintrag 4" }));
        CB_VAR.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CB_VARActionPerformed(evt);
            }
        });
        CB_VAR.addInputMethodListener(new java.awt.event.InputMethodListener()
        {
            public void caretPositionChanged(java.awt.event.InputMethodEvent evt)
            {
            }
            public void inputMethodTextChanged(java.awt.event.InputMethodEvent evt)
            {
                CB_VARInputMethodTextChanged(evt);
            }
        });

        TXT_VAL.setEditable(false);

        jLabel2.setText("Aktueller Wert");

        BT_SET.setText("Setzen");
        BT_SET.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_SETActionPerformed(evt);
            }
        });

        BT_OK.setText("Schliessen");
        BT_OK.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_OKActionPerformed(evt);
            }
        });

        jLabel3.setText("Neuer Wert");

        TXT_NEWVAL.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                TXT_NEWVALActionPerformed(evt);
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
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(jLabel1)
                                    .add(jLabel2))
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                    .add(TXT_VAL, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE)
                                    .add(CB_VAR, 0, 129, Short.MAX_VALUE)
                                    .add(TXT_NEWVAL, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE)))
                            .add(jLabel3))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(BT_SET))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, BT_OK))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {BT_OK, BT_SET}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(CB_VAR, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel2)
                    .add(TXT_VAL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(7, 7, 7)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(BT_SET)
                    .add(TXT_NEWVAL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(BT_OK)
                .addContainerGap())
        );
        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void TXT_NEWVALActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TXT_NEWVALActionPerformed
    {//GEN-HEADEREND:event_TXT_NEWVALActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
         set_param();
    }//GEN-LAST:event_TXT_NEWVALActionPerformed

    private void BT_OKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_OKActionPerformed
    {//GEN-HEADEREND:event_BT_OKActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        this.setVisible( false );
        this.dispose();
    }//GEN-LAST:event_BT_OKActionPerformed

    private void BT_SETActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_SETActionPerformed
    {//GEN-HEADEREND:event_BT_SETActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        set_param();
    }//GEN-LAST:event_BT_SETActionPerformed

    private void CB_VARInputMethodTextChanged(java.awt.event.InputMethodEvent evt)//GEN-FIRST:event_CB_VARInputMethodTextChanged
    {//GEN-HEADEREND:event_CB_VARInputMethodTextChanged
// TODO Ihre Ereignisbehandlung hier einf�gen:
        get_param();
    }//GEN-LAST:event_CB_VARInputMethodTextChanged

    private void CB_VARActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_CB_VARActionPerformed
    {//GEN-HEADEREND:event_CB_VARActionPerformed
       get_param();
    }//GEN-LAST:event_CB_VARActionPerformed
    
    /**
     * @param args the command line arguments
     */
 
    
    // Variablendeklaration - nicht modifizieren//GEN-BEGIN:variables
    private javax.swing.JButton BT_OK;
    private javax.swing.JButton BT_SET;
    private javax.swing.JComboBox CB_VAR;
    private javax.swing.JTextField TXT_NEWVAL;
    private javax.swing.JTextField TXT_VAL;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    // Ende der Variablendeklaration//GEN-END:variables
    
}
