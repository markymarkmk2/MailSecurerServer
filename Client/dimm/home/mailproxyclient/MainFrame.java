package dimm.home.mailproxyclient;

/*
 * MainFrame.java
 *
 * Created on 8. Oktober 2007, 10:30
 */



import dimm.home.mailproxyclient.Utilities.CmdExecutor;
import dimm.home.mailproxyclient.Utilities.ParseToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.event.TableModelEvent;
 
import dimm.home.mailproxyclient.Utilities.SwingWorker;
import dimm.home.mailproxyclient.Utilities.StandardTable;
import javax.swing.ImageIcon;






/**
 *
 * @author  Administrator
 */
public class MainFrame extends javax.swing.JFrame implements CommContainer
{
    
    
    public  static final int UDP_LOCAL_PORT = 11411;
    public  static final int UDP_SERVER_PORT = 11410;
    public  static final int TCP_SERVER_PORT = 11410;
    
    
    
    //String answer = null;
    boolean last_answer;
    ArrayList<StationEntry> st_list;

   // private int answer_station_id;    

    private boolean is_udp;
    
    //Socket keep_s;

    private boolean logged_in;
    int login_retries = 0;
    
   // SQLListBuilder sql_builder;
    StandardTable table;
    BoxListModel model;
    String fixed_ip = null;
    
    Communicator comm;
    
    StatusDialog status_dlg;
    
    boolean get_is_udp()
    {
        return is_udp;
    }
    
    /** Creates new form MainFrame */
    public MainFrame(boolean _is_udp, boolean allow_tcp, String _fixed_ip )
    {
        is_udp = _is_udp;
        st_list = new ArrayList<StationEntry>();            
        fixed_ip  = _fixed_ip;

        initComponents();
        
        if (!Main.enable_console)
        {
            this.MUI_SHELL.setVisible(false);
        }
        
        File icf = new File ("logo.png");
        if (icf.exists())
        {
            ImageIcon ic = new ImageIcon(icf.getAbsolutePath() );
            
            LB_ICON.setIcon( ic);
            pack();
        }
/*        else
        {
            LB_ICON.setVisible(false);
        }
  */     
        if (is_udp)
        {
            BT_NEW_IP.setVisible( false );
            TXT_IP.setVisible( false );
            MUI_UDP.setSelected( true );
            MUI_FILETRANSFER.setEnabled( false );
            
        }
        if (!allow_tcp)
        {
            MUI_TCP.setEnabled( false );
        }
        if (fixed_ip != null)
        {
            // EVERYTHING IS LIKE UDP, EXCEPT THE TRANSFERMODE ITSELF
            is_udp = false;
        }
        if (is_udp)
            comm = new UDP_Communicator(this);
        else 
            comm = new TCP_Communicator(this);
        
       // sql_builder = new SQLListBuilder();
            
        set_logged_in( false ); 
        
        table = new StandardTable();
        model = new BoxListModel( this );
        table.setModel( model );
        // STATION
        table.getColumnModel().getColumn(0).setMinWidth( 40 );
        table.getColumnModel().getColumn(0).setMaxWidth( 40 );
        // NAME
        table.getColumnModel().getColumn(1).setPreferredWidth( 120 );
        table.getColumnModel().getColumn(1).setMinWidth( 50 );
        // VERSION
        table.getColumnModel().getColumn(2).setMinWidth( 50 );
        table.getColumnModel().getColumn(2).setMaxWidth( 50 );
        // IP
        table.getColumnModel().getColumn(3).setMinWidth( 100 );
        table.getColumnModel().getColumn(3).setMaxWidth( 120 );
        // OK
        table.getColumnModel().getColumn(4).setMinWidth( 30 );
        table.getColumnModel().getColumn(4).setMaxWidth( 30 );
        
        SCP_LIST.setViewportView( table );

    }
    public Communicator get_comm()
    {
        return comm;
    }
    public String get_answer()
    {
        return comm.get_answer();
    }
    public String get_answer_err_text()
    {
        return comm.get_answer_err_text();
    }
    public synchronized boolean send_cmd( String str)
    {
        return comm.send_cmd(str, null);
    }
    public synchronized boolean send_cmd( String str, OutputStream outp)
    {
        return comm.send_cmd(str, outp);
    }
    
    public synchronized String send( String str, OutputStream outp)
    {
        return comm.send(str, outp);
    }
   
    public String send( String str)
    {
      return send( str, null );
    }    
    
    

    public void errm_ok( String str )
    {
        Object[] ok_options = {"OK"};
        JOptionPane.showOptionDialog(this, str, "Info", 
        JOptionPane.YES_NO_OPTION,        
        JOptionPane.INFORMATION_MESSAGE,
        null,
        ok_options,
        ok_options[0]);        
    }

    public boolean errm_ok_cancel( String str )
    {
        Object[] ok_options = {"Abbruch", "OK"};
        int sel = JOptionPane.showOptionDialog(this, str, "Info", 
        JOptionPane.YES_OPTION,        
        JOptionPane.INFORMATION_MESSAGE,
        null,
        ok_options,
        ok_options[0]);        
        
        return (sel == 1); 
    }

    public StationEntry get_selected_box()
    {
        int idx = table.getSelectedRow();
        if (idx >= 0)
            return st_list.get(idx );
        
        return null;
    }
    int get_station_entries()
    {
        return st_list.size();
    }
    StationEntry get_station(int i)
    {
        return st_list.get(i);
    }
    
    void run_scan()
    {
        run_scan( false );
    }
    void run_scan(final boolean b)
    {
                        
       SwingWorker worker = new SwingWorker()
        {
            public Object construct()
            {
                int ret = -1;
                try
                {
                    do_scan( b );              
                }
                catch (Exception err)
                {
                    err.printStackTrace();
                    ret = -2;
                }
                Integer iret = new Integer(ret);
                return iret;
            }
        };
        
        worker.start();
        
    }
    void table_changed()
    {
        table.tableChanged( new TableModelEvent( model ) );
    }
    
    void do_scan( boolean fast_mode)
    {
        st_list.clear();
        String answer = null;
        
        comm.do_scan( st_list, fixed_ip, fast_mode );
        
        table_changed();
        
        if (st_list.size() == 0)
        {
            this.TXT_STATUS.setText("Kein Geraet gefunden");
            if (status_dlg != null)
                status_dlg.reset_status();
        }
        else if (st_list.size() == 1)
            this.TXT_STATUS.setText("Ein Geraet gefunden");
        else
            this.TXT_STATUS.setText("Insg. " + st_list.size() + " Geraete gefunden");
        
    }
    boolean check_selected()
    {
        int idx = table.getSelectedRow();
        if (idx < 0)
        {
            errm_ok( "Bitte selektieren Sie einen " + Main.SERVERAPP);
            return false;
        }
        return true;
    }
        
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel2 = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();
        BTG_IP = new javax.swing.ButtonGroup();
        jPanel4 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        BT_SCAN = new javax.swing.JButton();
        SCP_LIST = new javax.swing.JScrollPane();
        LST_LIST = new javax.swing.JList();
        jLabel1 = new javax.swing.JLabel();
        BT_NEW_IP = new javax.swing.JButton();
        TXT_IP = new javax.swing.JTextField();
        BT_MAILARCHIVA = new javax.swing.JButton();
        LB_ICON = new javax.swing.JLabel();
        PN_STATUS = new javax.swing.JPanel();
        TXT_STATUS = new javax.swing.JTextField();
        TXT_USER = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        MU_FILE = new javax.swing.JMenu();
        MUI_LOGIN = new javax.swing.JMenuItem();
        MUI_UDP = new javax.swing.JRadioButtonMenuItem();
        MUI_TCP = new javax.swing.JRadioButtonMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        MUI_OWNPARAM = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        MUI_EXIT = new javax.swing.JMenuItem();
        MU_PARAMS = new javax.swing.JMenu();
        MUI_NETWORK = new javax.swing.JMenuItem();
        MUI_PROXY_LIST = new javax.swing.JMenuItem();
        MUI_STATIONID = new javax.swing.JMenuItem();
        MUI_PARAM = new javax.swing.JMenuItem();
        MU_EXTRA = new javax.swing.JMenu();
        PUI_STATUS = new javax.swing.JMenuItem();
        MUI_BOOT = new javax.swing.JMenuItem();
        MUI_LOG = new javax.swing.JMenuItem();
        MUI_VPN = new javax.swing.JCheckBoxMenuItem();
        MUI_SHELL = new javax.swing.JMenuItem();
        MUI_FILETRANSFER = new javax.swing.JMenuItem();

        jPanel2.setLayout(new java.awt.GridLayout(1, 0));

        jTextField1.setText("jTextField1");

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 100, Short.MAX_VALUE)
        );

        jButton1.setText("jButton1");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("SonicRemote");
        getContentPane().setLayout(new java.awt.GridBagLayout());

        BT_SCAN.setText("Suchen...");
        BT_SCAN.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_SCANActionPerformed(evt);
            }
        });

        LST_LIST.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        SCP_LIST.setViewportView(LST_LIST);

        jLabel1.setText("BettyMAIL");

        BT_NEW_IP.setText("Neue IP");
        BT_NEW_IP.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_NEW_IPActionPerformed(evt);
            }
        });

        TXT_IP.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                TXT_IPActionPerformed(evt);
            }
        });

        BT_MAILARCHIVA.setText("Mailarchiv öffnen");
        BT_MAILARCHIVA.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                BT_MAILARCHIVAActionPerformed(evt);
            }
        });

        LB_ICON.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        LB_ICON.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dimm/home/mailproxyclient/logo.png"))); // NOI18N
        LB_ICON.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        LB_ICON.setIconTextGap(0);

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, LB_ICON, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 507, Short.MAX_VALUE)
                    .add(SCP_LIST, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 507, Short.MAX_VALUE)
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(BT_MAILARCHIVA)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(BT_NEW_IP)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(TXT_IP, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 88, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 132, Short.MAX_VALUE)
                        .add(BT_SCAN))
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jLabel1))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .add(LB_ICON, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(SCP_LIST, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 150, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(18, 18, 18)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(BT_SCAN)
                    .add(BT_MAILARCHIVA)
                    .add(BT_NEW_IP)
                    .add(TXT_IP, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(jPanel1, gridBagConstraints);

        PN_STATUS.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        PN_STATUS.setLayout(new java.awt.GridBagLayout());

        TXT_STATUS.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        PN_STATUS.add(TXT_STATUS, gridBagConstraints);

        TXT_USER.setEditable(false);
        TXT_USER.setText("jTextField2");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        PN_STATUS.add(TXT_USER, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(PN_STATUS, gridBagConstraints);

        MU_FILE.setText("Datei");

        MUI_LOGIN.setText("Login...");
        MUI_LOGIN.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_LOGINActionPerformed(evt);
            }
        });
        MU_FILE.add(MUI_LOGIN);

        BTG_IP.add(MUI_UDP);
        MUI_UDP.setText("Lokales Netzwerk (UDP)");
        MUI_UDP.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_UDPActionPerformed(evt);
            }
        });
        MU_FILE.add(MUI_UDP);

        BTG_IP.add(MUI_TCP);
        MUI_TCP.setText("TCP/IP");
        MUI_TCP.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_TCPActionPerformed(evt);
            }
        });
        MU_FILE.add(MUI_TCP);
        MU_FILE.add(jSeparator1);

        MUI_OWNPARAM.setText("Eigene Parameter");
        MUI_OWNPARAM.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_OWNPARAMActionPerformed(evt);
            }
        });
        MU_FILE.add(MUI_OWNPARAM);
        MU_FILE.add(jSeparator2);

        MUI_EXIT.setText("Ende");
        MUI_EXIT.setToolTipText("Quit");
        MUI_EXIT.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_EXITActionPerformed(evt);
            }
        });
        MU_FILE.add(MUI_EXIT);

        jMenuBar1.add(MU_FILE);

        MU_PARAMS.setText("Parameter");

        MUI_NETWORK.setText("Netzwerkparameter");
        MUI_NETWORK.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_NETWORKActionPerformed(evt);
            }
        });
        MU_PARAMS.add(MUI_NETWORK);

        MUI_PROXY_LIST.setText("Mailproxyliste");
        MUI_PROXY_LIST.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_PROXY_LISTActionPerformed(evt);
            }
        });
        MU_PARAMS.add(MUI_PROXY_LIST);

        MUI_STATIONID.setText("Stations-ID festlegen");
        MUI_STATIONID.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_STATIONIDActionPerformed(evt);
            }
        });
        MU_PARAMS.add(MUI_STATIONID);

        MUI_PARAM.setText("LowLevel Parameter");
        MUI_PARAM.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_PARAMActionPerformed(evt);
            }
        });
        MU_PARAMS.add(MUI_PARAM);

        jMenuBar1.add(MU_PARAMS);

        MU_EXTRA.setText("Extras");
        MU_EXTRA.addMenuListener(new javax.swing.event.MenuListener()
        {
            public void menuCanceled(javax.swing.event.MenuEvent evt)
            {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt)
            {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt)
            {
                MU_EXTRAMenuSelected(evt);
            }
        });
        MU_EXTRA.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MU_EXTRAActionPerformed(evt);
            }
        });

        PUI_STATUS.setText("Statusdisplay");
        PUI_STATUS.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                PUI_STATUSActionPerformed(evt);
            }
        });
        MU_EXTRA.add(PUI_STATUS);

        MUI_BOOT.setText("Reboot Box");
        MUI_BOOT.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_BOOTActionPerformed(evt);
            }
        });
        MU_EXTRA.add(MUI_BOOT);

        MUI_LOG.setText("Log Dateien");
        MUI_LOG.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_LOGActionPerformed(evt);
            }
        });
        MU_EXTRA.add(MUI_LOG);

        MUI_VPN.setSelected(true);
        MUI_VPN.setText("VPN-Verbindung aktiv");
        MUI_VPN.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_VPNActionPerformed(evt);
            }
        });
        MU_EXTRA.add(MUI_VPN);

        MUI_SHELL.setText("Kommandozeile");
        MUI_SHELL.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_SHELLActionPerformed(evt);
            }
        });
        MU_EXTRA.add(MUI_SHELL);

        MUI_FILETRANSFER.setText("Dateitransfer (TCP/IP)");
        MUI_FILETRANSFER.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                MUI_FILETRANSFERActionPerformed(evt);
            }
        });
        MU_EXTRA.add(MUI_FILETRANSFER);

        jMenuBar1.add(MU_EXTRA);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void MUI_FILETRANSFERActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_FILETRANSFERActionPerformed
    {//GEN-HEADEREND:event_MUI_FILETRANSFERActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            FileTransferHandler fw = new FileTransferHandler( this );
            fw.do_action();
        }
        
    }//GEN-LAST:event_MUI_FILETRANSFERActionPerformed

    private void MUI_OWNPARAMActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_OWNPARAMActionPerformed
    {//GEN-HEADEREND:event_MUI_OWNPARAMActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        ParameterLocalDlg dlg = new ParameterLocalDlg( this );
        dlg.setLocation( this.getLocationOnScreen().x + 20, this.getLocationOnScreen().y + 50 );
        dlg.setVisible( true );

    }//GEN-LAST:event_MUI_OWNPARAMActionPerformed

    private void MUI_SHELLActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_SHELLActionPerformed
    {//GEN-HEADEREND:event_MUI_SHELLActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            ShellDlg dlg = new ShellDlg( this );
            dlg.setLocation( this.getLocationOnScreen().x + 80, this.getLocationOnScreen().y + 60 );
            dlg.setVisible( true );
        }
        
    }//GEN-LAST:event_MUI_SHELLActionPerformed

    private void MUI_LOGINActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_LOGINActionPerformed
    {//GEN-HEADEREND:event_MUI_LOGINActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        LoginDialog dlg = new LoginDialog( this );
        dlg.setLocation( this.getLocationOnScreen().x + 20, this.getLocationOnScreen().y + 30 );
        set_logged_in( false );

        
        File f = new File( "pprefs.dat" );
        if (!f.exists())
        {
            this.errm_ok("Es ist noch kein Passwort vergeben worden, bitte geben Sie das Systempasswort ein!");
            dlg.setVisible( true );
            if (dlg.get_pwd() == null)
                return;
            
            if ((dlg.get_pwd().compareTo("helikon") == 0) || 
                 (dlg.get_pwd().compareTo("bettymail") == 0))   
            {
                int retry = 0;
                while (true)
                {
                    retry++;
                    if (retry > 5)
                    {
                        this.errm_ok("Sicher, dass das noch klappen wird?");
                        if (retry > 10)
                        {
                            this.errm_ok("Das glaube ich nicht!!! Ciao...");
                            return;
                        }
                    }
                        
                    this.errm_ok("Okay, geben Sie nun das neue Benutzerpasswort ein (mind. 4 Zeichen)");
                    dlg.setVisible( true );
                    if (dlg.get_pwd() == null)
                        return;

                    String pwd1 = dlg.get_pwd();
                    if (pwd1.length() < 4)
                    {
                        this.errm_ok("Bitte mindestens 4 Zeichen!");
                        continue;
                    }
                    this.errm_ok("Gut, wiederholen Sie bitte das neue Benutzerpasswort");
                    dlg.setVisible( true );
                    if (dlg.get_pwd() == null)
                        return;
                    
                    String pwd2 = dlg.get_pwd();
                    if (pwd1.compareTo( pwd2 ) == 0)
                    {
                        try
                        {
                            String opwd = obfuscate_pwd(pwd1);
                            FileWriter fw = new FileWriter( f );
                            fw.write( opwd );
                            fw.close();
                        }
                        catch (Exception exc )
                        {
                            this.errm_ok("Verdammt, die Passwortdatei konnte nicht erzeugt werden");
                            return;
                        }
                        this.errm_ok("Das neue Benutzerpasswort wurde gespeichert, Sie können sich nun anmelden");
                        return;
                    }                    
                    this.errm_ok("Die Passworte sind unterschiedlich, noch mal von vorn...");
                }
            }
        }     
        else
        {
         
            dlg.setVisible( true );
            if (dlg.get_pwd() == null)
                return;
            
            BufferedReader in;
            String fpwd = null;
            try
            {
                in = new BufferedReader(new FileReader(f));
                fpwd = in.readLine();
                in.close();
            } 
            catch (Exception ex)
            {
                this.errm_ok("Oha, die Passwortdatei konnte nicht gelesen werden");
                return;
            }
            
            if (fpwd.length() < 8)
            {
                this.errm_ok("Hmm, die Passwortdatei ist fehlerhaft, sie wird nun geloescht");
                f.delete();
                return;
            }
            String pwd = de_obfuscate_pwd( fpwd );
            if (pwd.compareTo( dlg.get_pwd() ) == 0)
//            if (dlg.get_pwd().compareTo("123fckw456" ) == 0 || dlg.get_pwd().compareTo("helikon" ) == 0)
            {
                set_logged_in( true );
            }
            else
            {
                this.errm_ok("Das Passwort stimmt nicht");
                login_retries++;
                if (login_retries == 5)
                {
                    this.errm_ok("Bitte etwas mehr Konzentration!");
                }
                if (login_retries >= 8)
                {
                    this.errm_ok("Ich würde gern einen Tip geben, aber ich darf nicht!");
                }
                if (login_retries >= 16)
                {
                    this.errm_ok("Wow, das nenn ich Ausdauer, Respekt!");
                }
            }                    
        }
    }//GEN-LAST:event_MUI_LOGINActionPerformed

    private void TXT_IPActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TXT_IPActionPerformed
    {//GEN-HEADEREND:event_TXT_IPActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (NetworkParamsDlg.check_ip( TXT_IP.getText() ))
        {
            st_list.add( new StationEntry( 0, "0", TXT_IP.getText(), false, true ) );
            table_changed();
            TXT_IP.setVisible( false );
            
            this.validate();
            this.repaint();
        }        
    }//GEN-LAST:event_TXT_IPActionPerformed

    private void BT_NEW_IPActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_NEW_IPActionPerformed
    {//GEN-HEADEREND:event_BT_NEW_IPActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (TXT_IP.isVisible() == false)
        {
            TXT_IP.setVisible( true );
            if (TXT_IP.getText().length() == 0)                    
                TXT_IP.setText( "127.0.0.1");
            
            TXT_IP.setSelectionStart(0);
            TXT_IP.repaint();
            this.validate();
        }
        else
        {
            TXT_IPActionPerformed( null );
        }
            
    }//GEN-LAST:event_BT_NEW_IPActionPerformed

    private void MUI_UDPActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_UDPActionPerformed
    {//GEN-HEADEREND:event_MUI_UDPActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        this.is_udp = true;
        comm = new UDP_Communicator(this);

        BT_NEW_IP.setVisible( false );
        TXT_IP.setVisible( false );
        MUI_FILETRANSFER.setEnabled( false );
        
        
        scan_entry_list();
    }//GEN-LAST:event_MUI_UDPActionPerformed

    private void MUI_TCPActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_TCPActionPerformed
    {//GEN-HEADEREND:event_MUI_TCPActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        this.is_udp = false;
        comm = new TCP_Communicator(this);
         
         
        // for now we enter IP manually
        BT_NEW_IP.setVisible( true );
        MUI_FILETRANSFER.setEnabled( true );
        
         
        scan_entry_list();

    }//GEN-LAST:event_MUI_TCPActionPerformed

    private void PUI_STATUSActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_PUI_STATUSActionPerformed
    {//GEN-HEADEREND:event_PUI_STATUSActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            status_dlg = new StatusDialog( this );
            status_dlg.setLocation( this.getLocationOnScreen().x + 20, this.getLocationOnScreen().y + 30 );
           
            status_dlg.setVisible( true );                        
        }              
    }//GEN-LAST:event_PUI_STATUSActionPerformed

    private void MUI_NETWORKActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_NETWORKActionPerformed
    {//GEN-HEADEREND:event_MUI_NETWORKActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            comm.comm_open();                        
            NetworkParamsDlg dlg = new NetworkParamsDlg( this );
            dlg.setLocation( this.getLocationOnScreen().x + 70, this.getLocationOnScreen().y + 40 );
            dlg.setVisible( true );
            comm.comm_close();    
            
            scan_entry_list();
            
        }        
        
    }//GEN-LAST:event_MUI_NETWORKActionPerformed

    private void MUI_BOOTActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_BOOTActionPerformed
    {//GEN-HEADEREND:event_MUI_BOOTActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            if (this.errm_ok_cancel("Wollen Sie wirklich die Box neu booten?" ))
                comm.send_cmd("REBOOT" );
        }        
    }//GEN-LAST:event_MUI_BOOTActionPerformed

    private void MUI_LOGActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_LOGActionPerformed
    {//GEN-HEADEREND:event_MUI_LOGActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            comm.comm_open();            
            
            LogDlg dlg = new LogDlg( this );
            dlg.setLocation( this.getLocationOnScreen().x + 80, this.getLocationOnScreen().y + 50 );
           
            dlg.setVisible( true );
            
            comm.comm_close();            
        }        
    }//GEN-LAST:event_MUI_LOGActionPerformed

    private void MUI_PARAMActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_PARAMActionPerformed
    {//GEN-HEADEREND:event_MUI_PARAMActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            comm.comm_open();            
            
            ParameterRawDlg dlg = new ParameterRawDlg( this );
            dlg.setLocation( this.getLocationOnScreen().x + 30, this.getLocationOnScreen().y + 50 );
            dlg.setVisible( true );
            
            comm.comm_close();                        
        }
    }//GEN-LAST:event_MUI_PARAMActionPerformed

   

    
    
    private void MUI_STATIONIDActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_STATIONIDActionPerformed
    {//GEN-HEADEREND:event_MUI_STATIONIDActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        if (check_selected())
        {
            comm.comm_open();            
            
            StationIDDlg dlg = new StationIDDlg(this);
            dlg.setLocation( this.getLocationOnScreen().x + 50, this.getLocationOnScreen().y + 30 );
            dlg.setVisible(true);
            
            comm.comm_close();            
            
        }
    }//GEN-LAST:event_MUI_STATIONIDActionPerformed

    private void BT_SCANActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_SCANActionPerformed
    {//GEN-HEADEREND:event_BT_SCANActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        scan_entry_list();
        
    }//GEN-LAST:event_BT_SCANActionPerformed

    private void MUI_EXITActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_EXITActionPerformed
    {//GEN-HEADEREND:event_MUI_EXITActionPerformed
// TODO Ihre Ereignisbehandlung hier einf�gen:
        this.setVisible( false );
        this.dispose();
        System.exit(0);
    }//GEN-LAST:event_MUI_EXITActionPerformed

    private void BT_MAILARCHIVAActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_MAILARCHIVAActionPerformed
    {//GEN-HEADEREND:event_BT_MAILARCHIVAActionPerformed
        // TODO add your handling code here:
        if (!check_selected())
            return;
        
        StationEntry se = st_list.get( table.getSelectedRow() );
        
        String mailarchiva_url = "http://" + se.get_ip() +  Main.get_prop(Preferences.MAILARCHIVAURL, ":8090/mailarchiva");
            
        
        
        if (System.getProperty("os.name").startsWith("Windows"))
        {            
            String[] cmd = {"rundll32", "url.dll,FileProtocolHandler", mailarchiva_url};
            CmdExecutor exe = new CmdExecutor( cmd );
            exe.start();
        }
        if (System.getProperty("os.name").startsWith("Mac"))
        {            
            String[] cmd = {"open", mailarchiva_url};
            CmdExecutor exe = new CmdExecutor( cmd );
            exe.start();
        }
        
        
    }//GEN-LAST:event_BT_MAILARCHIVAActionPerformed

    private void MUI_PROXY_LISTActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_PROXY_LISTActionPerformed
    {//GEN-HEADEREND:event_MUI_PROXY_LISTActionPerformed
        // TODO add your handling code here:
        
        ProxyListDlg dlf = new ProxyListDlg( this );
        dlf.setLocation(this.getLocationOnScreen().x + 30, this.getLocationOnScreen().y + 30);
        dlf.setVisible(true);
    }//GEN-LAST:event_MUI_PROXY_LISTActionPerformed

    private void MUI_VPNActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MUI_VPNActionPerformed
    {//GEN-HEADEREND:event_MUI_VPNActionPerformed
        // TODO add your handling code here:
        if (check_selected())
        {
            if (MUI_VPN.isSelected())
            {
                if (this.errm_ok_cancel("Wollen Sie die VPN-Verbindung öffnen?" ))
                    comm.send_cmd("STARTVPN CMD:START" );
            }
            else
            {
                if (this.errm_ok_cancel("Wollen Sie die VPN-Verbindung schließen?" ))
                    comm.send_cmd("STARTVPN CMD:STOP" );
            }
            
            ParseToken pt = new ParseToken( comm.get_answer() );
            long retcode = pt.GetLong("VPN:");
            
            if (retcode != 0)
                this.errm_ok("Der Vorgang wurde nicht fehlerfrei abgeschlossen");
            
            // CHECK SETTINGS
            comm.send_cmd("STARTVPN CMD:GET" );
            pt = new ParseToken( comm.get_answer() );
            retcode = pt.GetLong("VPN:");
            MUI_VPN.setSelected((retcode == 1));
        }            
    }//GEN-LAST:event_MUI_VPNActionPerformed

    private void MU_EXTRAActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_MU_EXTRAActionPerformed
    {//GEN-HEADEREND:event_MU_EXTRAActionPerformed
        // TODO add your handling code here:
        if (check_selected())
        {
            // CHECK SETTINGS
            comm.send_cmd("STARTVPN CMD:GET" );
            ParseToken pt = new ParseToken( comm.get_answer() );
            long retcode = pt.GetLong("VPN:");
            MUI_VPN.setSelected((retcode == 1));
        }
        else
            MUI_VPN.setSelected( false );
    }//GEN-LAST:event_MU_EXTRAActionPerformed

    private void MU_EXTRAMenuSelected(javax.swing.event.MenuEvent evt)//GEN-FIRST:event_MU_EXTRAMenuSelected
    {//GEN-HEADEREND:event_MU_EXTRAMenuSelected
        // TODO add your handling code here:
        MU_EXTRAActionPerformed( null );
    }//GEN-LAST:event_MU_EXTRAMenuSelected

    
    void scan_entry_list()
    {
        int idx = table.getSelectedRow();
                
        if (this.is_udp)
            this.do_scan( true );    
        else if (fixed_ip == null)
        {
            this.scan_ips();
        }
        
        if (st_list.size() == 1)
            table.setRowSelectionInterval( 0, 0 );
        
        if (idx >= 0 && st_list.size() > idx )
            table.setRowSelectionInterval( idx, idx );            
    }
    /**
     * @param args the command line arguments
     */


    public void set_status(String string)
    {
        System.out.println( string );
        TXT_STATUS.setText( string );
    }
    
    /*

*/
    

    void fast_scan()
    {
        // AUTOMATIC LOCAL / REMOTE DETECTION
        do_scan(true);
        if (st_list.size() == 1)
            table.setRowSelectionInterval( 0, 0 );
    }

    private void scan_ips()
    {/*
        st_list = sql_builder.build_box_list( false );
        table_changed();
*/
    }

    public static String obfuscate_pwd(String pwd)
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < pwd.length(); i++)
        {
            char ch = pwd.charAt(i);
            sb.append((char)('A' + (ch & 0xf)) );
            sb.append((char)('Z' - (ch>>4)) );
        }
        return sb.toString();
    }
    public static String de_obfuscate_pwd(String pwd)
    {
        
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < pwd.length() -1; i++)
        {
            char ch = (char)(pwd.charAt(i) - 'A');
            char ch2 = (char)(-pwd.charAt(i+1) + 'Z');
            
            sb.append((char)(ch + (ch2 <<4)) );
            i++;
        }
        return sb.toString();
    }

    private void set_logged_in(boolean b)
    {
        if (b)
        {
            TXT_USER.setText(" Admin ");
        }
        else
        {
            TXT_USER.setText(" Gast ");
        }
        
        TXT_USER.validate();
        this.PN_STATUS.validate();
        logged_in = b;        
        
        MUI_STATIONID.setEnabled( b );
        MUI_PARAM.setEnabled( b );
        
        MUI_SHELL.setEnabled( b );
        MUI_OWNPARAM.setEnabled( b );
        MUI_TCP.setEnabled( b );
        this.MUI_NETWORK.setEnabled(b);
        this.MUI_PROXY_LIST.setEnabled(b);
        

    }
    
    public boolean is_logged_in()
    {
        return logged_in;
    }
    public boolean check_logged_in()
    {
        if (!is_logged_in())
        {
            errm_ok("Sie müssen sich zuerst anmelden");
            return false;
        }
        return true;
    }
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup BTG_IP;
    private javax.swing.JButton BT_MAILARCHIVA;
    private javax.swing.JButton BT_NEW_IP;
    private javax.swing.JButton BT_SCAN;
    private javax.swing.JLabel LB_ICON;
    private javax.swing.JList LST_LIST;
    private javax.swing.JMenuItem MUI_BOOT;
    private javax.swing.JMenuItem MUI_EXIT;
    private javax.swing.JMenuItem MUI_FILETRANSFER;
    private javax.swing.JMenuItem MUI_LOG;
    private javax.swing.JMenuItem MUI_LOGIN;
    private javax.swing.JMenuItem MUI_NETWORK;
    private javax.swing.JMenuItem MUI_OWNPARAM;
    private javax.swing.JMenuItem MUI_PARAM;
    private javax.swing.JMenuItem MUI_PROXY_LIST;
    private javax.swing.JMenuItem MUI_SHELL;
    private javax.swing.JMenuItem MUI_STATIONID;
    private javax.swing.JRadioButtonMenuItem MUI_TCP;
    private javax.swing.JRadioButtonMenuItem MUI_UDP;
    private javax.swing.JCheckBoxMenuItem MUI_VPN;
    private javax.swing.JMenu MU_EXTRA;
    private javax.swing.JMenu MU_FILE;
    private javax.swing.JMenu MU_PARAMS;
    private javax.swing.JPanel PN_STATUS;
    private javax.swing.JMenuItem PUI_STATUS;
    private javax.swing.JScrollPane SCP_LIST;
    private javax.swing.JTextField TXT_IP;
    private javax.swing.JTextField TXT_STATUS;
    private javax.swing.JTextField TXT_USER;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration//GEN-END:variables
    
}
