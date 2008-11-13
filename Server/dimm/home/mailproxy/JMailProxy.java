package dimm.home.mailproxy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.log4j.Level;

import de.jocca.logger.MoreLogger;
import de.jocca.logger.MoreLoggerFactory;

;

/**
 * JMailProxy is a Swing GUI that manages all the configurations
 * for using spam assassin and our POP3 Server.
 * 
 * @version 1.00, 05/05/09
 * @author Jocca Jocaf
 *
 */
public class JMailProxy extends JFrame
{

    // Constants
    private static final String[] DEBUG_LEVEL = {"OFF", "ERROR", "INFO", "DEBUG"};
    private static final int DEBUG_OFF = 0;
    private static final int DEBUG_ERROR = 1;
    private static final int DEBUG_INFO = 2;
    private static final int DEBUG_DEBUG = 3;
    private static final int MODE_INSERT = 0;
    private static final int MODE_UPDATE = 1;
    // Variables
    private JLabel lblMode = new JLabel();
    private JButton btnEdit;
    private JButton btnDelete;
    private JButton btnSave;
    private JButton btnStart;
    private JButton btnStop;
    private BorderLayout borderLayout1 = new BorderLayout();
    private JPanel panelSouth = new JPanel(new BorderLayout());
    private JButton btnClose = new JButton();
    private JPanel panelSubject = new JPanel(new BorderLayout());
    private JPanel panelTimeout = new JPanel(new BorderLayout());
    private JTextField txtTimeout = new JTextField();
    private JTextField txtSubject = new JTextField();
    private JPanel panelCenter = new JPanel(new BorderLayout());
    private JPanel panelMode = new JPanel(new BorderLayout());
    private JPanel panelFields = new JPanel(new BorderLayout());
    private BorderLayout borderLayout9 = new BorderLayout();
    private JPanel panelLocalPort = new JPanel(new BorderLayout());
    private JTextField txtLocalPort = new JTextField();
    private JPanel panelGroup1 = new JPanel(new BorderLayout());
    private JPanel panelRemotePort = new JPanel(new BorderLayout());
    private JTextField txtRemotePort = new JTextField();
    private JPanel panePOPServer = new JPanel(new BorderLayout());
    private JTextField txtPOPserver = new JTextField();
    private JButton btnOK = new JButton();
    private JPanel panelGroup2 = new JPanel(new BorderLayout());
    private JPanel panelTitel1 = new JPanel();
    GridLayout myLayout = new GridLayout(1, 3);
    private JPanel panelGrid = new JPanel(myLayout);
    private JScrollPane jScrollPane1 = new JScrollPane();
    private JScrollPane jScrollPane2 = new JScrollPane();
    private JScrollPane jScrollPane3 = new JScrollPane();
    private JList lstServer1 = new JList();
    private JList lstServer2 = new JList();
    private JList lstServer3 = new JList();
    private JPanel panelEditDelete = new JPanel();
    private JComboBox cboDebugLevel = new JComboBox(DEBUG_LEVEL);
    private JTextField txtLogname = new JTextField();
    private Dimension dimLabel = new Dimension(100, 21);
    private boolean m_bSaved = true;					// is the configuration saved ?
    private Vector<String> vLocal = new Vector<String>();    		// Local Port
    private Vector<String> vServer = new Vector<String>();   		// POP3 Server
    private Vector<String> vRemote = new Vector<String>();   		// Remote Port
    private int m_iMode = MODE_INSERT;				// The program begins in the insert mode
    private MoreLogger logger; 						// class from log4j by Jocca Jocaf
    private MailProxyServer myProxyServers[];			// Thread for Proxy Servers

    /**
     *  Constructor
     */
    public JMailProxy()
    {
        Common.setMainWindow(this);
        logger = new MoreLogger(JMailProxy.class);
        try
        {
            jbInit();
            configureComponents();
        } catch (Exception e)
        {
            logger.error(e);
        }
    }

    private void jbInit() throws Exception
    {
        this.setSize(640, 580);
        this.setTitle(Common.APP_TITLE + " - " + Common.APP_VERSION);
        JPanel panel = new JPanel();
        this.getContentPane().setLayout(borderLayout9);
        panelSouth.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 3, 5, 3));
        panel.setLayout(borderLayout1);
        JLabel lblLabel1 = new JLabel();
        JLabel lblSubject1 = new JLabel();
        JLabel lblLabel = new JLabel();
        JLabel lblSubject = new JLabel("Add to subject:  ", JLabel.RIGHT);
        JLabel lblLabel3 = new JLabel();
        txtTimeout.setEnabled(false);  // Next Version

        lblSubject.setPreferredSize(dimLabel);
        lblSubject.setMaximumSize(dimLabel);

        JPanel panelDebug = new JPanel(new BorderLayout());
        JPanel panelDebugLevel = new JPanel(new BorderLayout());
        panelDebugLevel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel panelDebugLogfile = new JPanel(new BorderLayout());
        panelDebugLogfile.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel lblDebugLevel = new JLabel("Debug Level:  ", JLabel.RIGHT);
        lblDebugLevel.setPreferredSize(dimLabel);
        lblDebugLevel.setMaximumSize(dimLabel);
        panelDebugLevel.add(lblDebugLevel, BorderLayout.WEST);
        panelDebugLevel.add(cboDebugLevel, BorderLayout.CENTER);

        JLabel lblLogfile = new JLabel("Log file:  ", JLabel.RIGHT);
        lblLogfile.setPreferredSize(dimLabel);
        lblLogfile.setMaximumSize(dimLabel);
        panelDebugLogfile.add(lblLogfile, BorderLayout.WEST);
        panelDebugLogfile.add(txtLogname, BorderLayout.CENTER);
        panelDebug.add(panelDebugLevel, BorderLayout.NORTH);
        panelDebug.add(panelDebugLogfile, BorderLayout.SOUTH);

        JPanel panel2000 = new JPanel(new BorderLayout());
        panel2000.add(panelTimeout, BorderLayout.SOUTH);
        panel2000.add(panelSubject, BorderLayout.NORTH);

        JPanel panelNorth = new JPanel(new BorderLayout());
        panelNorth.setBorder(javax.swing.BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panePOPServer.add(lblLabel3, BorderLayout.WEST);
        panePOPServer.add(txtPOPserver, BorderLayout.CENTER);
        panelLocalPort.add(lblLabel1, BorderLayout.WEST);
        panelLocalPort.add(txtLocalPort, BorderLayout.CENTER);
        panelSubject.add(lblSubject, BorderLayout.WEST);
        panelSubject.add(txtSubject, BorderLayout.CENTER);

        panelTimeout.add(lblLabel, BorderLayout.WEST);
        panelTimeout.add(txtTimeout, BorderLayout.CENTER);
        panelNorth.add(panel2000, BorderLayout.NORTH);
        panelNorth.add(panelDebug, BorderLayout.SOUTH);
        panel.add(panelNorth, BorderLayout.NORTH);


        JPanel panelSouth1 = new JPanel(new BorderLayout());
        JPanel panelSouth2 = new JPanel(new BorderLayout());

        btnClose.setText("Close");
        btnClose.setMnemonic('c');
        btnSave = new JButton("Save");
        btnSave.setMnemonic('a');
        JLabel lblBlank = new JLabel("            ");
        panelSouth2.add(btnSave, BorderLayout.WEST);
        panelSouth2.add(lblBlank, BorderLayout.CENTER);
        panelSouth2.add(btnClose, BorderLayout.EAST);

        btnStop = new JButton();
        JLabel lblLabel2 = new JLabel();
        btnStart = new JButton("Start");
        JPanel panelOK = new JPanel(new BorderLayout());
        panelGroup2.setBorder(javax.swing.BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panelOK.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 3, 5, 3));
        btnOK.setMnemonic('o');
        btnOK.setText("OK");
        panelOK.add(btnOK, BorderLayout.EAST);

        lblLabel3.setText("POP3 server:  ");
        lblLabel3.setHorizontalAlignment(JLabel.RIGHT);
        lblLabel3.setPreferredSize(dimLabel);
        lblLabel3.setMaximumSize(dimLabel);
        panePOPServer.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelRemotePort.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        lblLabel2.setText("Remote Port:  ");
        lblLabel2.setHorizontalAlignment(JLabel.RIGHT);
        lblLabel2.setPreferredSize(dimLabel);
        lblLabel2.setMaximumSize(dimLabel);
        panelGroup1.setBorder(javax.swing.BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        lblLabel1.setText("Local Port:  ");
        lblLabel1.setHorizontalAlignment(JLabel.RIGHT);
        lblLabel1.setPreferredSize(dimLabel);
        lblLabel1.setMaximumSize(dimLabel);
        panelLocalPort.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelMode.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelSubject.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelTimeout.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        lblLabel.setText("Timeout (ms):  ");
        lblLabel.setHorizontalAlignment(JLabel.RIGHT);
        lblLabel.setPreferredSize(dimLabel);
        lblLabel.setMaximumSize(dimLabel);

        btnStop.setText("Stop");
        btnStop.setEnabled(false);
        lblSubject1.setText("            ");
        btnStart.setMnemonic('s');

        JLabel l1 = new JLabel("Local Port", JLabel.CENTER);
        JLabel l2 = new JLabel("POP3 Server", JLabel.CENTER);
        JLabel l3 = new JLabel("Remote Port", JLabel.CENTER);
        JPanel panelHeader2 = new JPanel(new GridLayout(1, 3));
        panelHeader2.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelHeader2.add(l1);
        panelHeader2.add(l2);
        panelHeader2.add(l3);

        JLabel lblHeader = new JLabel("POP3 Server List");
        lblHeader.setHorizontalAlignment(SwingConstants.CENTER);
        lblHeader.setFont(new Font("Tahoma", 1, 13));
        lblHeader.setForeground(Color.white);
        lblHeader.setOpaque(true);
        lblHeader.setPreferredSize(new Dimension(120, 18));
        lblHeader.setBackground(new Color(186, 207, 245));
        panelTitel1.add(lblHeader);
        JPanel panelHeaderMain = new JPanel(new BorderLayout());
        panelHeaderMain.add(panelTitel1, BorderLayout.NORTH);
        panelHeaderMain.add(panelHeader2, BorderLayout.SOUTH);

        jScrollPane1.getViewport().add(lstServer1, null);
        jScrollPane1.setPreferredSize(new Dimension(10, 10));
        jScrollPane2.getViewport().add(lstServer2, null);
        lstServer2.setPreferredSize(new Dimension(20, 10));
        jScrollPane2.setPreferredSize(new Dimension(20, 10));
        jScrollPane2.setMaximumSize(new Dimension(20, 10));
        jScrollPane3.getViewport().add(lstServer3, null);
        jScrollPane3.setPreferredSize(new Dimension(50, 10));

        // Buttons Edit and Delete
        btnEdit = new JButton("Edit");
        btnEdit.setMnemonic('e');

        JLabel lblBlank1 = new JLabel("            ");

        btnDelete = new JButton("Delete");
        lblMode.setText("Insert mode");
        lblMode.setBackground(new Color(186, 207, 245));
        lblMode.setPreferredSize(new Dimension(120, 18));
        lblMode.setOpaque(true);
        lblMode.setForeground(Color.white);
        lblMode.setFont(new Font("Tahoma", 1, 13));
        lblMode.setHorizontalAlignment(SwingConstants.CENTER);
        btnDelete.setMnemonic('d');
        panelEditDelete.setLayout(new FlowLayout(SwingConstants.RIGHT));

        panelGrid.add(jScrollPane1);
        panelGrid.add(jScrollPane2);
        panelGrid.add(jScrollPane3);


        panelEditDelete.add(btnEdit);
        panelEditDelete.add(lblBlank1);
        panelEditDelete.add(btnDelete);

        panelSouth1.add(btnStart, BorderLayout.WEST);
        panelSouth1.add(btnStop, BorderLayout.EAST);
        panelSouth1.add(lblSubject1, BorderLayout.CENTER);

        panelSouth.add(panelSouth1, BorderLayout.WEST);
        panelSouth.add(panelSouth2, BorderLayout.EAST);

        panel.add(panelSouth, BorderLayout.SOUTH);
        panelMode.add(lblMode, BorderLayout.EAST);
        panelGroup1.add(panelMode, BorderLayout.NORTH);
        panelFields.add(panelLocalPort, BorderLayout.NORTH);
        panelRemotePort.add(lblLabel2, BorderLayout.WEST);
        panelRemotePort.add(txtRemotePort, BorderLayout.CENTER);
        panelRemotePort.add(panelOK, BorderLayout.SOUTH);
        panelFields.add(panelRemotePort, BorderLayout.SOUTH);
        panelFields.add(panePOPServer, BorderLayout.CENTER);
        panelGroup1.add(panelFields, BorderLayout.CENTER);
        panelCenter.add(panelGroup1, BorderLayout.NORTH);
        panelGroup2.add(panelHeaderMain, BorderLayout.NORTH);

        panelGroup2.add(panelGrid, BorderLayout.CENTER);
        panelGroup2.add(panelEditDelete, BorderLayout.SOUTH);
        panelCenter.add(panelGroup2, BorderLayout.CENTER);
        panel.add(panelCenter, BorderLayout.CENTER);

        this.getContentPane().add(panel, BorderLayout.CENTER);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    } // jbInit

    private void configureComponents()
    {
        // add List Selection listeners
        JListListener myListner = new JListListener();

        lstServer1.addListSelectionListener(myListner);
        lstServer2.addListSelectionListener(myListner);
        lstServer3.addListSelectionListener(myListner);

        lstServer1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstServer2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstServer3.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // add key listeners
        MyKeyListener keyListener = new MyKeyListener();
        txtSubject.addKeyListener(keyListener);
        txtTimeout.addKeyListener(keyListener);
        txtLogname.addKeyListener(keyListener);

        // add action listeners
        btnClose.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                btnClose_actionPerformed(e);
            }
        });

        btnOK.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                addPOPServer();
            }
        });

        btnStart.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                startServer();
            }
        });

        btnStop.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                stopServer();
            }
        });

        btnSave.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                if (!m_bSaved)
                {
                    save();
                }
            }
        });

        btnEdit.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                editPOPServer();
            }
        });

        btnDelete.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                deletePOPServer();
            }
        });

        cboDebugLevel.addActionListener(new ActionListener()
        {

            public void actionPerformed(ActionEvent e)
            {
                // saved state changed
                m_bSaved = false;
            }
        });

        this.addWindowListener(new WindowAdapter()
        {

            @Override
            public void windowClosing(WindowEvent e)
            {
//	        if (Common.showQuestion(this,"Would you like to quit the application?")==JOptionPane.YES_OPTION)
//	        {
                ExitApplication();
//	        }

            }
        });

    } // configureComponents

    private void ExitApplication()
    {
        stopServer();
        this.dispose();
    }

    private void addPOPServer()
    {
        if (!validateServerFields())
        {
            return;
        }


        if (m_iMode == MODE_INSERT)
        {
            // add the new POP3 server
            vLocal.add(txtLocalPort.getText().trim());
            vServer.add(txtPOPserver.getText().trim());
            vRemote.add(txtRemotePort.getText().trim());
        } else if (m_iMode == MODE_UPDATE)
        {
            // replace the POP3 server
            vLocal.setElementAt(txtLocalPort.getText().trim(), lstServer1.getSelectedIndex());
            vServer.setElementAt(txtPOPserver.getText().trim(), lstServer1.getSelectedIndex());
            vRemote.setElementAt(txtRemotePort.getText().trim(), lstServer1.getSelectedIndex());
        }

        lstServer1.setListData(vLocal);
        lstServer2.setListData(vServer);
        lstServer3.setListData(vRemote);

        // set the new mode
        setMode(MODE_INSERT);

        // saved state changed
        m_bSaved = false;
    }

    private void deletePOPServer()
    {
        if (!validateEditDelete())
        {
            return;
        }

        int selectedRow = lstServer1.getSelectedIndex();
        vLocal.remove(selectedRow);
        vServer.remove(selectedRow);
        vRemote.remove(selectedRow);

        lstServer1.setListData(vLocal);
        lstServer2.setListData(vServer);
        lstServer3.setListData(vRemote);

        m_bSaved = false;

        // set the new mode
        setMode(MODE_INSERT);
    }

    private void editPOPServer()
    {
        if (!validateEditDelete())
        {
            return;
        }

        int selectedRow = lstServer1.getSelectedIndex();
        txtLocalPort.setText(lstServer1.getModel().getElementAt(selectedRow).toString());
        txtPOPserver.setText(lstServer2.getModel().getElementAt(selectedRow).toString());
        txtRemotePort.setText(lstServer3.getModel().getElementAt(selectedRow).toString());

        // set the new mode
        setMode(MODE_UPDATE);
    }

    private boolean validateEditDelete()
    {
        if (lstServer2.getModel().getSize() == 0)
        {
            Common.showMSG("There is no POP3 server.");
            return false;
        } else if (lstServer1.getSelectedIndex() == -1)
        {
            Common.showMSG("Select first a POP3 server from the list.");
            return false;
        }

        return true;
    }

    private void setMode(int newMode)
    {
        m_iMode = newMode;
        boolean lockServerList = false;

        switch (m_iMode)
        {
            case MODE_INSERT:
                lblMode.setText("Insert mode");
                // release the server list
                lockServerList = true;
                break;
            case MODE_UPDATE:
                lblMode.setText("Update mode");
                // avoid the index change
                lockServerList = false;
                break;
        }

        lstServer1.setEnabled(lockServerList);
        lstServer2.setEnabled(lockServerList);
        lstServer3.setEnabled(lockServerList);

    }  // setMode

    class MyKeyListener extends KeyAdapter
    {

        @Override
        public void keyReleased(KeyEvent e)
        {
            m_bSaved = false;
        }
    }  // MyKeyEvent

    class JListListener implements ListSelectionListener
    {

        public void valueChanged(ListSelectionEvent e)
        {
            if (e.getValueIsAdjusting() == false)
            {

                JList myList = (JList) e.getSource();

                int selectedRow = myList.getSelectedIndex();

                if (selectedRow > -1)
                {
                    lstServer1.setSelectedIndex(selectedRow);
                    lstServer2.setSelectedIndex(selectedRow);
                    lstServer3.setSelectedIndex(selectedRow);
                }
            }
        }
    }  // JListListener

    public void read()
    {

        // file not found
        if (!Common.DirFileExists("config.ini"))
        {
            // set default values
            txtSubject.setText("*JSpamAssassin* ");
            txtTimeout.setText("2000");

            cboDebugLevel.setSelectedIndex(DEBUG_ERROR);
            txtLogname.setText("JSpamAssassin.log");

            txtLocalPort.setText("110");
            txtPOPserver.setText("example.pop.com");
            txtRemotePort.setText("110");

            // set the flag
            m_bSaved = true;
            return;
        }

        try
        {
            // read the file
            FileReader reader = new FileReader("config.ini");
            BufferedReader in = new BufferedReader(reader);
            // subject
            String line = in.readLine();
            txtSubject.setText(line);
            // timeout
            line = in.readLine();
            txtTimeout.setText(line);
            // timeout
            line = in.readLine();
            int index = Integer.parseInt(line);
            cboDebugLevel.setSelectedIndex(index);
            // Log file
            line = in.readLine();
            txtLogname.setText(line);

            // POP Servers

            while ((line = in.readLine()) != null)
            {
                vLocal.add(line);
                line = in.readLine();
                vServer.add(line);
                line = in.readLine();
                vRemote.add(line);
            }

            lstServer1.setListData(vLocal);
            lstServer2.setListData(vServer);
            lstServer3.setListData(vRemote);

        } catch (Exception e)
        {
            logger.error(e);
        }

        // set the flag
        m_bSaved = true;

    }  // read

    public boolean save()
    {
        // validate fields
        if (!validateTopFields())
        {
            return false;
        }

        try
        {
            // open file
            FileWriter writer = new FileWriter("config.ini");
            BufferedWriter out = new BufferedWriter(writer);
            // write fields
            out.write(txtSubject.getText() + Common.LINE_FEED);
            out.write(txtTimeout.getText() + Common.LINE_FEED);
            out.write(cboDebugLevel.getSelectedIndex() + Common.LINE_FEED);
            out.write(txtLogname.getText() + Common.LINE_FEED);

            for (int i = 0; i < lstServer1.getModel().getSize(); i++)
            {
                out.write(lstServer1.getModel().getElementAt(i) + Common.LINE_FEED);
                out.write(lstServer2.getModel().getElementAt(i) + Common.LINE_FEED);
                out.write(lstServer3.getModel().getElementAt(i) + Common.LINE_FEED);
            }
            // close config file
            out.close();
            writer.close();

            Common.showMSG("The configuration file was saved.");
            // saved state changed
            m_bSaved = true;

            return true;

        } catch (Exception e)
        {
            logger.error(e);
        }
        return false;
    }  // write

    private boolean validateTopFields()
    {
        String msg = null;
        Component component = null;
        boolean result = false;

        try
        {
            int i = Integer.parseInt(txtTimeout.getText());
        } catch (NumberFormatException e)
        {
            msg = "The timeout (in miliseconds) must be an integer.";
            component = txtTimeout;
        }

        if (msg == null)
        {
            result = true;
        } else
        {
            Common.showError(msg);
            component.requestFocus();
        }

        return result;
    }

    private boolean validateServerFields()
    {
        String msg = null;
        Component component = null;
        boolean result = false;
        int port;

        try
        {
            port = Integer.parseInt(txtLocalPort.getText());
            if (port >= 10000)
            {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e)
        {
            msg = "The Local Port must be an integer less than 10000.";
            component = txtLocalPort;
        }

        if (msg == null)
        {
            if (txtPOPserver.getText().trim().length() == 0)
            {
                msg = "Enter the POP3 server name or the POP3 IP address.";
                component = txtPOPserver;
            }
        }

        if (msg == null)
        {
            try
            {
                port = Integer.parseInt(txtRemotePort.getText());
                if (port >= 10000)
                {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e)
            {
                msg = "The Remote Port must be an integer less than 10000.";
                component = txtRemotePort;
            }
        }

        if (msg == null)
        {
            port = Integer.parseInt(txtLocalPort.getText());
            // verify if the port is occupied
            for (int i = 0; i < lstServer1.getModel().getSize(); i++)
            {
                int portOccupied = Integer.parseInt((String) lstServer1.getModel().getElementAt(i));
                // we jump the verification if the current index = selected index
                // in the Update mode.
                if (!(m_iMode == MODE_UPDATE && lstServer1.getSelectedIndex() == i))
                {
                    if (portOccupied == port)
                    {
                        String server = (String) lstServer2.getModel().getElementAt(i);
                        msg = "The Local Port " + port + " is occupied by the POP3 server \"" + server +
                                "\" on the line " + (i + 1) + " of the list.";
                        component = txtLocalPort;
                        break;
                    }
                }
            }
        }

        if (msg == null)
        {
            result = true;
        } else
        {
            Common.showError(msg);
            component.requestFocus();
        }

        return result;
    }

    private void btnClose_actionPerformed(ActionEvent e)
    {
        if (!m_bSaved)
        {
            int answer = Common.showQuestion(this, "Would you like to save the changes ?");
            if (answer == JOptionPane.YES_OPTION)
            {
                // try to save the configuration
                if (!save())
                {
                    Common.showError("The configuration file could not be saved. See the error messages or log file for more details.");
                    // abort
                    return;
                }
            }
        }
        // close the window
        this.setVisible(false);
        this.dispose();
    }

    public static void main(String[] args)
    {
        JMailProxy jsa = new JMailProxy();
        jsa.setVisible(true);
        jsa.read();
    }

    private void stopServer()
    {
        Common.setAllComponentsEnabled(this.getContentPane(), true, true, true);
        btnStop.setEnabled(false);
        txtTimeout.setEnabled(false);  // Next Version

        if (myProxyServers == null)
        {
            return;
        }

        for (int i = 0; i < myProxyServers.length; i++)
        {
            MailProxyServer.StopServer();
            myProxyServers[i] = null;
        }

        myProxyServers = null;
    }

    private void startServer()
    {
        // verify if there is a POP server
        if (lstServer1.getModel().getSize() == 0)
        {
            Common.showMSG("Add a POP server first.");
            return;
        }

        Common.setAllComponentsEnabled(this.getContentPane(), true, true, false);
        btnStop.setEnabled(true);

        ////////////////////////////////
        // initialize the logger engine
        ////////////////////////////////

        // output to file ?
        if (txtLogname.getText().trim().length() > 0)
        {
            MoreLoggerFactory.setLogname(txtLogname.getText());
            MoreLoggerFactory.setOutputMode(MoreLoggerFactory.OUTPUT_MODE_FILE_DETAILED);
        }

        // what is the log level ?
        if (cboDebugLevel.getSelectedIndex() == DEBUG_OFF)
        {
            MoreLoggerFactory.setLevel(Level.OFF);
        } else if (cboDebugLevel.getSelectedIndex() == DEBUG_ERROR)
        {
            MoreLoggerFactory.setLevel(Level.ERROR);
        } else if (cboDebugLevel.getSelectedIndex() == DEBUG_INFO)
        {
            MoreLoggerFactory.setLevel(Level.INFO);
        } else if (cboDebugLevel.getSelectedIndex() == DEBUG_DEBUG)
        {
            MoreLoggerFactory.setLevel(Level.DEBUG);
        }

        ////////////////////////////////
        // start the threads
        ////////////////////////////////

        myProxyServers = new MailProxyServer[lstServer1.getModel().getSize()];

        for (int i = 0; i < lstServer1.getModel().getSize(); i++)
        {
            int localP = Integer.parseInt(lstServer1.getModel().getElementAt(i).toString());
            int remoteP = Integer.parseInt(lstServer3.getModel().getElementAt(i).toString());
            String sHost = lstServer2.getModel().getElementAt(i).toString();
            if (localP == 110)
            {
                startPOP3ProxyServer(i, localP, remoteP, sHost);
            } else
            {
                startSMTPProxyServer(i, localP, remoteP, sHost);
            }
        }
    }  // startServer

    private void startPOP3ProxyServer(final int counter, final int localP, final int remoteP, final String sHost)
    {
        Thread proxyThread;

        proxyThread = new Thread()
        {

            @Override
            public void run()
            {
                // runs the POP3 server
                myProxyServers[counter] = new MailProxyServer();
                myProxyServers[counter].go_pop(sHost, localP, remoteP);
            }
        };

        proxyThread.start();

    }

    private void startSMTPProxyServer(final int counter, final int localP, final int remoteP, final String sHost)
    {
        Thread proxyThread;

        proxyThread = new Thread()
        {

            @Override
            public void run()
            {
                // runs the POP3 server
                myProxyServers[counter] = new MailProxyServer();
                myProxyServers[counter].go_smtp(sHost, localP, remoteP);
            }
        };

        proxyThread.start();

    }
}  // JMailProxy

