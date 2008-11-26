package dimm.home.mailproxyclient;

/*
 * StatusDialog.java
 *
 * Created on 10. Oktober 2007, 13:29
 */



import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.StringTokenizer;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.Timer;
import dimm.home.mailproxyclient.Utilities.ParseToken;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;

class ProxyEntry
{
    
    private String host;
    private int localPort;
    private int remotePort;
    private String protokoll;
    private int instanceCnt;
    
    ProxyEntry( String _host, long l, long r, long ic, String p )
    {
        host = _host;
        localPort =  (int)l;
        remotePort =  (int)r;
        instanceCnt = (int)ic;
        protokoll = p;
    }
  
    String getHost()
    {
        return host;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public int getRemotePort()
    {
        return remotePort;
    }

    public String getProtokoll()
    {
        return protokoll;
    }
   
    public int getInstanceCnt()
    {
        return instanceCnt;
    }   
    public void setInstanceCnt(int i)
    {
        instanceCnt = i;
    }   
    
    public boolean is_equal( ProxyEntry pe )
    {
        if (pe.getLocalPort() != getLocalPort())
            return false;
        if (pe.getRemotePort() != getRemotePort())
            return false;
        
        if (!pe.getHost().equals(host))
            return false;
        if (!pe.getProtokoll().equals(getProtokoll()))
            return false;
        
        return true;
    }
        
}
class ProxyTableModel extends AbstractTableModel
{
    ArrayList<ProxyEntry> proxy_list;
    String col_names[] = {"Protokoll", "LocalPort", "Host", "RemotePort", "Tasks" };
    Class col_classes[] = {String.class, Integer.class, String.class, Integer.class, Integer.class };

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return super.getColumnClass(columnIndex);
    }

    @Override
    public String getColumnName(int column)
    {
        return col_names[column];
    }
    
    ProxyTableModel( ArrayList<ProxyEntry> _proxy_list )
    {
        proxy_list = _proxy_list;
    }

    public int getRowCount()
    {
        return proxy_list.size();
    }

    public int getColumnCount()
    {
        return col_names.length;
    }

    public Object getValueAt(int rowIndex, int columnIndex)
    {
        ProxyEntry pe = proxy_list.get(rowIndex);
        switch ( columnIndex )
        {
            case 0: return pe.getProtokoll();
            case 1: return pe.getLocalPort();
            case 2: return pe.getHost();
            case 3: return pe.getRemotePort();
            case 4: return pe.getInstanceCnt();
        }
        return "?";
    }
}
/**
 *
 * @author  Administrator
 */
public class StatusDialog extends javax.swing.JDialog implements ActionListener
{
    
    MainFrame parent;
    Timer timer;
    
    Color ok_color = new Color(153, 255, 153 );
    Color nok_color = new Color(255, 153, 153 );
    ProxyTableModel model;
    ArrayList<ProxyEntry> proxy_list;
    
    
    /** Creates new form StatusDialog */
    public StatusDialog(MainFrame _parent)
    {
        super(_parent, false);
        parent = _parent;
        initComponents();
               
        
        proxy_list = new ArrayList<ProxyEntry>();
        
        timer = new Timer( 1000, this );
        timer.start();
        
        
        model = new ProxyTableModel( proxy_list);
        
        TB_TASKS.setModel(model);
        TB_TASKS.setShowVerticalLines(false);
        TB_TASKS.setGridColor(Color.LIGHT_GRAY);
        TB_TASKS.getColumnModel().getColumn(0).setPreferredWidth(50);
        TB_TASKS.getColumnModel().getColumn(1).setPreferredWidth(50);
        TB_TASKS.getColumnModel().getColumn(2).setPreferredWidth(150);
        TB_TASKS.getColumnModel().getColumn(3).setPreferredWidth(50);
        TB_TASKS.getColumnModel().getColumn(4).setPreferredWidth(50);
        TB_TASKS.setEnabled(false);

        parent.get_comm().comm_open();
       
        read_status();        
    }
    public void reset_status()
    {
        proxy_list.clear();
        model.fireTableDataChanged();
        
        TXT_TIME.setText( "" );
        TXT_RESOURCE.setText( "" );
        TXT_CM.setText( "" );
        TXT_PS.setText( "" );
        TXT_SD.setText( "" );                                     
        TXT_MA.setText( "" );
        
    }
    
    boolean add_to_proxy_list( ProxyEntry pe )
    {
        int i;
        for (i = 0; i < proxy_list.size(); i++)
        {
            if (proxy_list.get(i).is_equal(pe))
            {
                if (proxy_list.get(i).getInstanceCnt() != pe.getInstanceCnt())
                {
                    proxy_list.get(i).setInstanceCnt( pe.getInstanceCnt());
                    return true;
                }
                return false;
            }
        }
        if (i == proxy_list.size())
        {
            proxy_list.add( pe );
            return true;
        }
        return false;

    }
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        jPanel2 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        TXT_MA = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        TXT_PS = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        TXT_SD = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        TXT_TIME = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        TXT_RESOURCE = new javax.swing.JTextField();
        TXT_CM = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        BT_OK = new javax.swing.JButton();
        PN_TASKS = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        TB_TASKS = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosed(java.awt.event.WindowEvent evt)
            {
                formWindowClosed(evt);
            }
        });

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Allgemein"));

        jLabel5.setText("MailArchiver");

        TXT_MA.setEditable(false);
        TXT_MA.setDoubleBuffered(true);

        jLabel6.setText("ProxyServer");

        TXT_PS.setEditable(false);
        TXT_PS.setDoubleBuffered(true);

        jLabel9.setText("Status");

        TXT_SD.setEditable(false);
        TXT_SD.setDoubleBuffered(true);

        jLabel27.setText("Datum Uhrzeit");

        TXT_TIME.setEditable(false);
        TXT_TIME.setDoubleBuffered(true);

        jLabel10.setText("Resourcen");

        TXT_RESOURCE.setEditable(false);
        TXT_RESOURCE.setDoubleBuffered(true);

        TXT_CM.setEditable(false);
        TXT_CM.setDoubleBuffered(true);

        jLabel11.setText("Communicator");

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jLabel5)
                    .add(jLabel6)
                    .add(TXT_PS, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                    .add(TXT_MA, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                    .add(jLabel27)
                    .add(TXT_TIME, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 185, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel10)
                    .add(TXT_RESOURCE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                    .add(jLabel9)
                    .add(TXT_SD, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE)
                    .add(jLabel11)
                    .add(TXT_CM, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel5)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_MA, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel6)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_PS, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jLabel9)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_SD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jLabel11)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_CM, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(42, 42, 42)
                .add(jLabel10)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_RESOURCE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(44, 44, 44)
                .add(jLabel27)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(TXT_TIME, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        BT_OK.setText("Schliessen");
        BT_OK.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_OKActionPerformed(evt);
            }
        });

        PN_TASKS.setBorder(javax.swing.BorderFactory.createTitledBorder("Tasks"));

        TB_TASKS.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String []
            {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(TB_TASKS);

        org.jdesktop.layout.GroupLayout PN_TASKSLayout = new org.jdesktop.layout.GroupLayout(PN_TASKS);
        PN_TASKS.setLayout(PN_TASKSLayout);
        PN_TASKSLayout.setHorizontalGroup(
            PN_TASKSLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(PN_TASKSLayout.createSequentialGroup()
                .addContainerGap()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 506, Short.MAX_VALUE)
                .addContainerGap())
        );
        PN_TASKSLayout.setVerticalGroup(
            PN_TASKSLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(PN_TASKSLayout.createSequentialGroup()
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 365, Short.MAX_VALUE)
                .addContainerGap())
        );

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(PN_TASKS, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, BT_OK))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, PN_TASKS, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(BT_OK))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosed(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowClosed
    {//GEN-HEADEREND:event_formWindowClosed
// TODO Ihre Ereignisbehandlung hier einf�gen:
         parent.get_comm().comm_close();
    }//GEN-LAST:event_formWindowClosed

    public JButton getBT_OK()
    {
        return BT_OK;
    }

    private void BT_OKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_OKActionPerformed
    {//GEN-HEADEREND:event_BT_OKActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        timer.stop();
        parent.get_comm().comm_close();
        this.setVisible(false);
        this.dispose();
    }//GEN-LAST:event_BT_OKActionPerformed

    public void actionPerformed(ActionEvent actionEvent)
    {
        timer.stop();
        read_status();
        timer.start();
    }

    private void read_status()
    {        
        if (parent.send_cmd("GETSTATUS"))
        {
            boolean touched = false;
            //proxy_list.clear();
            
            StringTokenizer sto = new StringTokenizer( parent.get_answer(), "\n\r" );
            while (sto.hasMoreTokens())
            {
                String line = sto.nextToken();
                
                // 1. LINE GENERAL INFO
                ParseToken pt = new ParseToken( line );
                
                String date_str = pt.GetString("TIM:");
                if (date_str != null && date_str.length() > 0) 
                    TXT_TIME.setText( date_str );
                String space_str = pt.GetString("FSP:");
                String mem_str = pt.GetString("FMY:");
                
                if (space_str != null && space_str.length() > 0 && mem_str != null)
                {
                    TXT_RESOURCE.setText("Disk: " + space_str + " Mem: " + mem_str);
                }
                                                
                String name = pt.GetString("WPN:");
                if (name != null && name.length() > 0)
                {
                    String status = pt.GetString("ST:");
                    boolean ok = pt.GetBoolean("OK:");

                    JTextField txtf = null;
                    Color color = (ok) ? ok_color : nok_color;

                    if (name.compareTo("Communicator") == 0)
                        txtf = TXT_CM;
                    if (name.compareTo("MailProxyServer") == 0)
                        txtf = TXT_PS;
                    if (name.compareTo("StatusDisplay") == 0)
                        txtf = TXT_SD;                                      
                    if (name.compareTo("MailArchiver") == 0)
                        txtf = TXT_MA;

                    if (txtf != null)
                    {
                        txtf.setText( status );
                        txtf.setBackground( color );                    
                    }
                }
                
                
               
                for ( int i = 0; ; i++)
                {
                    String protokoll = pt.GetString("PXPT" + i + ":");
                    if (protokoll == null || protokoll.length() == 0)
                        break;
                    
                    String host =  pt.GetString("PXHO" + i + ":");
                    long local_port = pt.GetLongValue("PXPL" + i + ":");
                    long remote_port = pt.GetLongValue("PXPR" + i + ":");
                    long inst_cnt = pt.GetLongValue("PXIN" + i + ":");
                    
                    ProxyEntry pe = new ProxyEntry( host, local_port, remote_port, inst_cnt, protokoll );
//                    proxy_list.add(pe);
                    if (add_to_proxy_list( pe ))
                        touched = true;
                    
                }  
                
                
            }
            if (touched)
                model.fireTableDataChanged();
        }
    }
    

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BT_OK;
    private javax.swing.JPanel PN_TASKS;
    private javax.swing.JTable TB_TASKS;
    private javax.swing.JTextField TXT_CM;
    private javax.swing.JTextField TXT_MA;
    private javax.swing.JTextField TXT_PS;
    private javax.swing.JTextField TXT_RESOURCE;
    private javax.swing.JTextField TXT_SD;
    private javax.swing.JTextField TXT_TIME;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
    
}
