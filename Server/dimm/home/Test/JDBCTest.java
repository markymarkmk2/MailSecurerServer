/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * JDBCTest.java
 *
 * Created on 23.04.2009, 10:28:43
 */
package dimm.home.Test;

import dimm.home.mailarchiv.Utilities.SwingWorker;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author http://www.pornotubia.com/videos/
 */
public class JDBCTest extends javax.swing.JFrame
{

    // INDEX OF COMBO_DB!!!


    StringBuffer log_buff;
    boolean driver_loaded = false;

    /** Creates new form JDBCTest */
    public JDBCTest()
    {
        initComponents();

        COMBO_DB.removeAllItems();
        COMBO_DB.addItem("Select DB");

        log_buff = new StringBuffer();
        TXT_INSERTS.setText("10");

        load_jdbc_drivers();
    }

    String get_connect_str()
    {
        return TXT_CONNSTR.getText();
    }

    String get_db_user()
    {
        return TXT_USER.getText();
    }

    String get_db_pwd()
    {
        return TXT_PWD.getText();
    }

    Connection openSQLConnection()
    {
        try
        {

            Connection conn = DriverManager.getConnection(get_connect_str(), get_db_user(), get_db_pwd());

            return conn;
        }
        catch (Exception exc)
        {
            add_log("Cannot connect database " + get_connect_str() + " : " + exc.getMessage());
        }
        return null;
    }

    boolean  check_connect()
    {
        Connection conn = openSQLConnection();
        if (conn == null)
            return false;

        try
        {
            Statement stm = conn.createStatement();
            ResultSet rs = stm.executeQuery("select count(*) from test");
            rs.close();
            stm.close();
        }
        catch (Exception exc)
        {
            String cached = "";
            if (COMBO_DB.getSelectedItem().toString().equals("HSQL"))
                cached = "cached";

            String cr = "create " + cached + " table test  ( id INTEGER GENERATED BY DEFAULT AS IDENTITY,  name VARCHAR(255))";
            Statement stm = null;
            try
            {
                stm = conn.createStatement();
                stm.execute(cr);
                add_log("Created test table");
            }
            catch (Exception eexc)
            {
                add_log("Cannot create test table: " + eexc.getMessage());
                return false;
            }
            finally
            {
                try
                {
                    stm.close();
                }
                catch (SQLException sQLException)
                {
                }
            }
        }
        
        return true;
    }

    void run_insert() throws SQLException
    {
        SwingWorker sw = new SwingWorker("JDBCTest") {

            @Override
            public Object construct()
            {
                try
                {
                    _run_insert();
                }
                catch (SQLException sQLException)
                {
                    add_log( "Insert failed: " + sQLException.getMessage());
                }
                BT_INSERT.setEnabled(true);
                return null;
            }
        };

        BT_INSERT.setEnabled(false);
        sw.start();
    }
    
    void _run_insert() throws SQLException
    {
        if (!check_connect())
            return;

        Connection conn = openSQLConnection();
        Statement stm = conn.createStatement();

        conn.setAutoCommit( CB_AUTOCOMMIT.isSelected() );
      

/*        String p_st = "PREPARE testplan (text) AS INSERT INTO test (name) VALUES($1)";

        stm.execute(p_st);
*/
        long start_time = System.currentTimeMillis();
        long last_act_time = start_time;
        int last_cnt = 0;
        
        int count = Integer.parseInt(TXT_INSERTS.getText());

        String name1 = "abcdefgh";
        String name2 = "";
        for (int n = 0; n < 100; n++)
            name2 += name1 + n + " ";


        for (int i = 0; i < count; i++)
        {
            String name = name2 + i;
            String st = "insert into test (name) values ('" + name + "')";
//            String st = "execute testplan('" + name + "')";

            stm.execute(st);

            long diff_time = System.currentTimeMillis() - last_act_time;
            if (diff_time > 1000 )
            {
                int n = i - last_cnt;

                String str = "After " + i + " rows insert speed is " + Long.toString(( n * 1000) /diff_time ) + " rows/s";
                add_log(str);
                last_act_time = System.currentTimeMillis();
                last_cnt = i;
            }
        }
        if (!CB_AUTOCOMMIT.isSelected())
            conn.commit();

        long end_time = System.currentTimeMillis();
        long dur_ms = end_time - start_time;
        long dur_s = (end_time - start_time) / 1000;
        if (dur_ms == 0)
            dur_ms = 1;
        
        
        String str = "Insert of " + count + " rows took " + dur_ms + " ms (" + dur_s + " s)";
        add_log(str);
        str = "Speed: " + Long.toString((count * 1000) / dur_ms ) + " rows / s";
        add_log(str);
        
        ResultSet rs = stm.executeQuery("select count(*) from test");
        if (rs.next())
        {
            add_log("Actual row count: " + rs.getString(1));
        }

        rs.close();
        stm.close();
        conn.close();



    }

    void run_qry() throws SQLException
    {
        if (!check_connect())
            return;

        Connection conn = openSQLConnection();
        Statement stm = conn.createStatement();

        long start_time = System.currentTimeMillis();

//        String name = "sdfhijshfjs askjfhksajhfsji asjfdkwjshfdkjs askjfhdksjfdkj sakjfdhkasjfdsj ";
        String name = "123456";
        String qry  = "select count(*) from test where name like '%" + name + "%'";
        //String qry = "select count(*) from test where to_tsvector('german', name) @@ to_tsquery('german', '" + name + "')";
        ResultSet rs = stm.executeQuery(qry);
        String cnt = "?";
        if (rs.next())
            cnt = rs.getString(1);

        long end_time = System.currentTimeMillis();
        long dur_ms = end_time - start_time;
        long dur_s = (end_time - start_time) / 1000;

        String str = "Query took " + dur_ms + " ms (" + dur_s + " s): Count is " + cnt;
        add_log(str);


        rs.close();
        stm.close();
        conn.close();

    }
    void shutdown() throws SQLException
    {
        if (!check_connect())
            return;

        Connection conn = openSQLConnection();
        Statement stm = conn.createStatement();


        long start_time = System.currentTimeMillis();

        stm.execute("SHUTDOWN");

        long end_time = System.currentTimeMillis();
        long dur_ms = end_time - start_time;
        long dur_s = (end_time - start_time) / 1000;

        String str = "Shutdown took " + dur_ms + " ms (" + dur_s + " s)";
        add_log(str);


        stm.close();
        conn.close();

    }

    void load_jdbc_drivers()
    {
        // LOAD LOCAL AND REMOTE DB
        try
        {
            if (!driver_loaded)
            {
                try
                {
                    Class.forName("com.mysql.jdbc.Driver").newInstance();
                    COMBO_DB.addItem("MySQL");
                }
                catch (Exception exception)
                {
                    add_log("Cannot load MySQL drivers: " + exception.getMessage());
                }

                try
                {
                    Class.forName("org.hsqldb.jdbcDriver").newInstance();
                    COMBO_DB.addItem("HSQL");
                }
                catch (Exception exception)
                {
                    add_log("Cannot load HSQL drivers: " + exception.getMessage());
                }

                try
                {
                    Class.forName("org.h2.Driver").newInstance();
                    COMBO_DB.addItem("H2");
                }
                catch (Exception exception)
                {
                    add_log("Cannot load H2 drivers: " + exception.getMessage());
                }
                try
                {
                    Class.forName("org.postgresql.Driver").newInstance();
                    COMBO_DB.addItem("PostgreSQL");
                }
                catch (Exception exception)
                {
                    add_log("Cannot load PostgreSQL drivers: " + exception.getMessage());
                }


                driver_loaded = true;
            }

        }
        catch (Exception exception)
        {
            add_log("Cannot load jdbc drivers: " + exception.getMessage());
        }
    }


    void clear_table() throws SQLException
    {
        if (!check_connect())
            return;

        Connection conn = openSQLConnection();
        Statement stm = conn.createStatement();

        stm.execute("delete from test");
        stm.close();
        conn.close();

    }

    private void add_log( String string )
    {
        log_buff.append(string);
        log_buff.append("\n");
        TXT_STATUS.setText(log_buff.toString());
        TXT_STATUS.setCaretPosition(log_buff.length());
        
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        TXT_CONNSTR = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        TXT_USER = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        TXT_PWD = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        TXT_INSERTS = new javax.swing.JTextField();
        BT_INSERT = new javax.swing.JButton();
        BT_QRY = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        TXT_STATUS = new javax.swing.JTextArea();
        CB_AUTOCOMMIT = new javax.swing.JCheckBox();
        BT_CLEAR_LOG = new javax.swing.JButton();
        BT_CLEAR_TABLE = new javax.swing.JButton();
        COMBO_DB = new javax.swing.JComboBox();
        BT_SHUTDOWN = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("ConnectString");

        jLabel2.setText("User");

        jLabel3.setText("PWD");

        jLabel4.setText("Inserts");

        TXT_INSERTS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TXT_INSERTSActionPerformed(evt);
            }
        });

        BT_INSERT.setText("Start Insert");
        BT_INSERT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BT_INSERTActionPerformed(evt);
            }
        });

        BT_QRY.setText("Start Query all");
        BT_QRY.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BT_QRYActionPerformed(evt);
            }
        });

        TXT_STATUS.setColumns(20);
        TXT_STATUS.setRows(5);
        jScrollPane1.setViewportView(TXT_STATUS);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 473, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 197, Short.MAX_VALUE)
        );

        CB_AUTOCOMMIT.setText("AutoCommit");

        BT_CLEAR_LOG.setText("Clear Log");
        BT_CLEAR_LOG.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BT_CLEAR_LOGActionPerformed(evt);
            }
        });

        BT_CLEAR_TABLE.setText("Clear Table");
        BT_CLEAR_TABLE.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BT_CLEAR_TABLEActionPerformed(evt);
            }
        });

        COMBO_DB.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        COMBO_DB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                COMBO_DBActionPerformed(evt);
            }
        });

        BT_SHUTDOWN.setText("Shutdown");
        BT_SHUTDOWN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BT_SHUTDOWNActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(TXT_PWD, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(TXT_USER, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(TXT_CONNSTR, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(COMBO_DB, 0, 130, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(TXT_INSERTS, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(CB_AUTOCOMMIT)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 67, Short.MAX_VALUE)
                                .addComponent(BT_CLEAR_TABLE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(BT_CLEAR_LOG))))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(BT_INSERT)
                        .addGap(18, 18, 18)
                        .addComponent(BT_QRY)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(BT_SHUTDOWN)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(TXT_CONNSTR, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(COMBO_DB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(TXT_USER, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(TXT_PWD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(TXT_INSERTS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(CB_AUTOCOMMIT)
                    .addComponent(BT_CLEAR_LOG)
                    .addComponent(BT_CLEAR_TABLE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(BT_INSERT)
                    .addComponent(BT_QRY)
                    .addComponent(BT_SHUTDOWN))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void BT_INSERTActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_INSERTActionPerformed
    {//GEN-HEADEREND:event_BT_INSERTActionPerformed
        // TODO add your handling code here:

        try
        {
            run_insert();
        }
        catch (SQLException sQLException)
        {
            add_log( "Insert failed: " + sQLException.getMessage());
        }
}//GEN-LAST:event_BT_INSERTActionPerformed

    private void BT_CLEAR_TABLEActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_CLEAR_TABLEActionPerformed
    {//GEN-HEADEREND:event_BT_CLEAR_TABLEActionPerformed
        // TODO add your handling code here:
        try
        {
            clear_table();
        }
        catch (SQLException sQLException)
        {
            add_log( "Clear failed: " + sQLException.getMessage());
        }

}//GEN-LAST:event_BT_CLEAR_TABLEActionPerformed

    private void BT_CLEAR_LOGActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_CLEAR_LOGActionPerformed
    {//GEN-HEADEREND:event_BT_CLEAR_LOGActionPerformed
        // TODO add your handling code here:
        log_buff.setLength(0);
        TXT_STATUS.setText("");
}//GEN-LAST:event_BT_CLEAR_LOGActionPerformed

    private void COMBO_DBActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_COMBO_DBActionPerformed
    {//GEN-HEADEREND:event_COMBO_DBActionPerformed
        // TODO add your handling code here:
        String conn_str = "";
        if (COMBO_DB.getItemCount() < 2)
            return;
        
        if (COMBO_DB.getSelectedItem().toString().equals("MySQL"))
        {
            conn_str = "jdbc:mysql://localhost/test_mysql";
            TXT_USER.setText("root");
            TXT_PWD.setText("");
        }
        if (COMBO_DB.getSelectedItem().toString().equals("HSQL"))
        {
            conn_str = "jdbc:hsqldb:file:test_hsql";
            TXT_USER.setText("sa");
            TXT_PWD.setText("");
        }
        if (COMBO_DB.getSelectedItem().toString().equals("H2"))
        {
            conn_str = "jdbc:h2:test_h2";
            TXT_USER.setText("sa");
            TXT_PWD.setText("");
        }
        if (COMBO_DB.getSelectedItem().toString().equals("PostgreSQL"))
        {
            conn_str = "jdbc:postgresql://localhost/test_postgresql";
            TXT_USER.setText("postgres");
            TXT_PWD.setText("12345");
        }
        
        TXT_CONNSTR.setText(conn_str);
    }//GEN-LAST:event_COMBO_DBActionPerformed

    private void BT_QRYActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_QRYActionPerformed
    {//GEN-HEADEREND:event_BT_QRYActionPerformed
        // TODO add your handling code here:
        try
        {
            run_qry();
        }
        catch (SQLException sQLException)
        {
            add_log( "Query failed: " + sQLException.getMessage());
        }

    }//GEN-LAST:event_BT_QRYActionPerformed

    private void TXT_INSERTSActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_TXT_INSERTSActionPerformed
    {//GEN-HEADEREND:event_TXT_INSERTSActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_TXT_INSERTSActionPerformed

    private void BT_SHUTDOWNActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_BT_SHUTDOWNActionPerformed
    {//GEN-HEADEREND:event_BT_SHUTDOWNActionPerformed
        // TODO add your handling code here:
       if (COMBO_DB.getSelectedItem().toString().equals("HSQL"))
       {
        try
        {
           shutdown();
        }
        catch (SQLException sQLException)
        {
            add_log( "Shutdownfailed: " + sQLException.getMessage());
        }
       }

    }//GEN-LAST:event_BT_SHUTDOWNActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main( String args[] )
    {
        java.awt.EventQueue.invokeLater(new Runnable()
        {

            public void run()
            {
                new JDBCTest().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BT_CLEAR_LOG;
    private javax.swing.JButton BT_CLEAR_TABLE;
    private javax.swing.JButton BT_INSERT;
    private javax.swing.JButton BT_QRY;
    private javax.swing.JButton BT_SHUTDOWN;
    private javax.swing.JCheckBox CB_AUTOCOMMIT;
    private javax.swing.JComboBox COMBO_DB;
    private javax.swing.JTextField TXT_CONNSTR;
    private javax.swing.JTextField TXT_INSERTS;
    private javax.swing.JTextField TXT_PWD;
    private javax.swing.JTextArea TXT_STATUS;
    private javax.swing.JTextField TXT_USER;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
