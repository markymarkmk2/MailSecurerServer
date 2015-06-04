/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.vault;

import dimm.home.mailarchiv.Exceptions.IndexException;
import dimm.home.mailarchiv.Exceptions.VaultException;
import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import dimm.home.mailarchiv.Utilities.DirectoryEntry;
import dimm.home.mailarchiv.Utilities.LogManager;
import dimm.home.mailarchiv.Utilities.BackgroundWorker;
import home.shared.CS_Constants;
import home.shared.hibernate.DiskSpace;
import home.shared.mail.MailAttribute;
import home.shared.mail.RFCFileMail;
import static home.shared.mail.RFCGenericMail.MATTR_LUCENE;
import home.shared.mail.RFCMailAddress;
import home.shared.mail.RFCMimeMail;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;

class ExportDSHEntry
{

    private long total_size = 0;
    private int total_cnt = 0;
    private long act_size = 0;
    private int act_cnt = 0;
    private DiskSpaceHandler data_dsh;
    private DiskSpaceHandler index_dsh;
    private DirectoryEntry data_dir;

    public ExportDSHEntry( DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh, DirectoryEntry data_dir )
    {
        this.data_dsh = data_dsh;
        this.index_dsh = index_dsh;
        this.data_dir = data_dir;
    }

    /**
     * @return the total_size
     */
    public long getTotal_size()
    {
        return total_size;
    }

    /**
     * @param total_size the total_size to set
     */
    public void setTotal_size( long total_size )
    {
        this.total_size = total_size;
    }

    /**
     * @return the total_cnt
     */
    public int getTotal_cnt()
    {
        return total_cnt;
    }

    /**
     * @param total_cnt the total_cnt to set
     */
    public void setTotal_cnt( int total_cnt )
    {
        this.total_cnt = total_cnt;
    }

    /**
     * @return the act_size
     */
    public long getAct_size()
    {
        return act_size;
    }

    /**
     * @param act_size the act_size to set
     */
    public void setAct_size( long act_size )
    {
        this.act_size = act_size;
    }

    /**
     * @return the act_cnt
     */
    public int getAct_cnt()
    {
        return act_cnt;
    }

    /**
     * @param act_cnt the act_cnt to set
     */
    public void setAct_cnt( int act_cnt )
    {
        this.act_cnt = act_cnt;
    }

    /**
     * @return the data_dsh
     */
    public DiskSpaceHandler getData_dsh()
    {
        return data_dsh;
    }

    /**
     * @return the index_dsh
     */
    public DiskSpaceHandler getIndex_dsh()
    {
        return index_dsh;
    }

    /**
     * @return the data_dir
     */
    public DirectoryEntry getData_dir()
    {
        return data_dir;
    }

    void addAct_size( long length )
    {
        act_size += length;
    }

    void incrAct_cnt()
    {
        act_cnt++;
    }
}

/**
 *
 * @author mw
 */
public class ExportContext
{

    MandantContext context;
    int da_id;
    int ds_id;
    BackgroundWorker worker;
    private String last_msg = "";
    private boolean busy = false;
    private boolean pause = false;
    private boolean abort = false;
    ArrayList<ExportDSHEntry> export_list;
    private int act_exp_idx = -1;
    File exportPath;

    public ExportContext( MandantContext context, int da_id, int ds_id, File exportPath )
    {
        this.context = context;
        this.da_id = da_id;
        this.ds_id = ds_id;
        this.exportPath = exportPath;
        worker = null;
        export_list = new ArrayList<ExportDSHEntry>();
    }

    public ExportContext( MandantContext context, int da_id, File exportPath )
    {
        this(context, da_id, -1, exportPath);
    }
    public ExportContext( MandantContext context, File exportPath )
    {
        this(context, -1, -1, exportPath);
    }

    public void start()
    {
        // DO NOT ALLOW RESTART
        if (worker != null)
        {
            return;
        }

        busy = true;
        worker = new BackgroundWorker("Export")
        {

            @Override
            public Object construct()
            {
                try
                {
                    prepare_export();
                    if (!abort)
                    {
                        do_export();
                    }
                }
                catch (Exception e)
                {
                    LogManager.msg_index( LogManager.LVL_ERR, "Unknown error during export", e);
                }
                finally
                {
                    busy = false;
                }
                return null;
            }
        };

        worker.start();
    }

    void prepare( DiskSpaceHandler data_dsh, DiskSpaceHandler index_dsh ) throws VaultException
    {
        set_status(Main.Txt("Scanning_file_system_for_data"));

        String path = data_dsh.getMailPath();
        DirectoryEntry data_dir = new DirectoryEntry(new File(path));
        ExportDSHEntry exportDSHEntry = new ExportDSHEntry(data_dsh, index_dsh, data_dir);
        export_list.add(exportDSHEntry);


        long total_size = 0;
        int total_cnt = 0;
        // START CALC STATS
        Iterator<File> it = data_dir.get_file_iterator();
        while (it.hasNext())
        {
            File mailfile = it.next();
            total_size += mailfile.length();
            total_cnt++;
            
            exportDSHEntry.setTotal_cnt(total_cnt);
            exportDSHEntry.setTotal_size(total_size);

            // CHECK ABORT
            if (abort)
            {
                set_status(Main.Txt("Aborting_export"));
                break;
            }
        }

    }

    void do_export() throws VaultException, IndexException, IOException
    {
        for (int i = 0; i < export_list.size() && !abort; i++)
        {
            ExportDSHEntry exportDSHEntry = export_list.get(i);
            synchronized (stat_sb)
            {
                act_exp_idx = i;
            }
            do_export(exportDSHEntry);

            // CHECK ABORT
            if (abort)
            {
                break;
            }
        }
    }

    void do_export( ExportDSHEntry exportDSHEntry ) throws VaultException, IndexException, IOException
    {
        DiskSpaceHandler data_dsh = exportDSHEntry.getData_dsh();
        DiskSpaceHandler index_dsh = exportDSHEntry.getIndex_dsh();

        data_dsh.lock_for_rebuild();
        if (index_dsh != data_dsh)
        {
            index_dsh.lock_for_rebuild();
        }
        try
        {
            try
            {
                // CLOSE ANY OPEN DSH
                if (data_dsh.is_open())
                {
                    data_dsh.close();
                }
                if (index_dsh.is_open())
                {
                    index_dsh.close();
                }
            }
            catch (Exception exc )
            {
                LogManager.msg_index( LogManager.LVL_ERR,"Error closing last index", exc);
            }

            
            // AND CREATE NEW FROM SCRATCH
            //index_dsh.create_write_index();


            set_status(Main.Txt("Scanning_file_system_for_data"));

            // BUILD RECUSIVE ENTRYLIST
            //String path = data_dsh.getMailPath();
            DirectoryEntry data_dir = exportDSHEntry.getData_dir(); //new DirectoryEntry(new File(path));


            // START CALC STATS
            Iterator<File> it = data_dir.get_file_iterator();

            set_status(Main.Txt("Starting_export"));
            LogManager.msg_index( LogManager.LVL_DEBUG, getLast_msg());

            int errs_in_row = 0;
            while (it.hasNext())
            {
                // CHECK PAUSE MODE
                if (pause)
                {
                    data_dsh.unlock_for_rebuild();
                    if (index_dsh != data_dsh)
                    {
                        index_dsh.unlock_for_rebuild();
                    }
                    while (pause && !abort)
                    {
                        LogicControl.sleep(500);
                    }
                    data_dsh.lock_for_rebuild();
                    if (index_dsh != data_dsh)
                    {
                        index_dsh.lock_for_rebuild();
                    }
                }

                // CHECK ABORT
                if (abort)
                {
                    set_status(Main.Txt("Aborting_export"));
                    break;
                }

                File mailfile = it.next();

                // SKIP ATTRIBUTE ENTRIES
                if (mailfile.getName().endsWith(RFCFileMail.ATTR_SUFFIX))
                    continue;

                try
                {
                    // RETRIEVE MAILFILE
                    String norm_path = mailfile.getAbsolutePath().replace('\\', '/');
                    long time = data_dsh.build_time_from_path(norm_path, data_dsh.get_enc_mode(), data_dsh.get_fmode());
                    RFCFileMail rfc = (RFCFileMail) data_dsh.get_mail_from_time(time, data_dsh.get_enc_mode(), data_dsh.get_fmode());

                    try
                    {
                        rfc.read_attributes();
                    }
                    catch (IOException iOException)
                    {
                        LogManager.msg_index( LogManager.LVL_ERR, "Cannot read attributes for " + mailfile.getAbsolutePath() + " " + iOException.getMessage());
                    }

                    String uuid = data_dsh.get_message_uuid(rfc);

                    // AND INDEX IT
                    if (LogManager.has_lvl(LogManager.TYP_INDEX, LogManager.LVL_DEBUG))
                    {
                        LogManager.msg_index( LogManager.LVL_DEBUG,"Exporting " + mailfile.getAbsolutePath() + " " + mailfile.length());
                    }
//                    boolean ok = idx.handle_IndexJobEntry(context, uuid, da_id, data_dsh.ds.getId(), index_dsh, rfc,
//                            /*delete_after_index*/ false, /*parallel*/ /*true*/true,/*skip_account_match*/ true);

                    boolean ok = exportMessage( rfc, uuid );
                    if (!ok)
                    {
                        LogManager.msg_index( LogManager.LVL_ERR, "Exporting " + mailfile.getAbsolutePath() + " failed");
                        errs_in_row++;
                    }
                    else
                    {
                        errs_in_row = 0;
                    }

                    // STATS
                    synchronized (stat_sb)
                    {
                        exportDSHEntry.addAct_size(mailfile.length());
                        exportDSHEntry.incrAct_cnt();
                    }
                }
                catch (VaultException vaultException)
                {
                    set_status(Main.Txt("Invalid_mailfile"));
                    LogManager.msg_index( LogManager.LVL_ERR,last_msg, vaultException);
                }
                if (errs_in_row > 50)
                {
                    throw new VaultException("Cannot_export_files,_too_many_errors_in_a_row");
                }
            }
            
            // Pool wieder beschreibbar machen
            index_dsh.open_write_index_pool();

            // 100 %
            synchronized (stat_sb)
            {
                exportDSHEntry.setAct_size(exportDSHEntry.getTotal_size());
                exportDSHEntry.setAct_cnt(exportDSHEntry.getTotal_cnt());
            }

            set_status(Main.Txt("Finished_export"));
        }
        catch (VaultException vaultException)
        {
            set_status(Main.Txt("Aborting_export"));
            LogManager.msg_index( LogManager.LVL_ERR,last_msg, vaultException);
        }
        finally
        {
            data_dsh.unlock_for_rebuild();
            if (index_dsh != data_dsh)
            {
                index_dsh.unlock_for_rebuild();
            }
        }
    }

    public void prepare_export()
    {
        for (Vault vault : context.getVaultArray() ) {
            if (!(vault instanceof DiskVault))
            {
                continue;
            }
            DiskVault dv = (DiskVault) vault;
            if (da_id != -1) {
                if (dv.get_da().getId() != da_id) {
                    continue;
                }
            }
            Iterator<DiskSpaceHandler> it = dv.get_dsh_list().iterator();

            while (it.hasNext())
            {
                DiskSpaceHandler dsh = it.next();

                if (dsh.is_disabled())
                {
                    continue;
                }

                DiskSpace ds = dsh.getDs();

                // WE WANT SPECIFIV DS?
                if (ds_id != -1 && ds.getId() != ds_id)
                {
                    continue;
                }

                // SKIP OFFLINE, SO USER HAS A CHANCE TO PRESERVE INDEX IF HE WANTS
                if (ds.getStatus().compareTo(CS_Constants.DS_OFFLINE) == 0)
                {
                    continue;
                }

                File dsf = new File(ds.getPath());
                if (!dsf.exists())
                {
                    set_status("DiskSpace <" + dsf.getAbsolutePath() + "> was not found, skipping");
                    LogManager.msg_index( LogManager.LVL_ERR,getLast_msg());
                    continue;
                }

                // CHECK FOR DS WITH DATA AND INDEX
                if (dsh.test_flag(ds, CS_Constants.DS_MODE_DATA) && dsh.test_flag(ds, CS_Constants.DS_MODE_INDEX))
                {
                    try
                    {
                        prepare(dsh, dsh);
                    }
                    catch (Exception vaultException)
                    {
                        set_status(Main.Txt("Reindex_aborted"));
                        LogManager.msg_index( LogManager.LVL_ERR,getLast_msg(), vaultException);
                    }
                    continue;
                }
                // SKIP INDEX ONLY
                if (!dsh.test_flag(ds, CS_Constants.DS_MODE_DATA))
                {
                    continue;
                }

                // FOUND DATA, LOOK FOR FIRST INDEX
                DiskSpaceHandler use_dsh_idx = null;
                Iterator<DiskSpaceHandler> it_idx = dv.get_dsh_list().iterator();

                while (it_idx.hasNext())
                {
                    DiskSpaceHandler dsh_idx = it_idx.next();

                    if (dsh_idx.is_disabled())
                    {
                        continue;
                    }


                    // SKIP OFFLINE, SO USER HAS A CHANCE TO PRESERVE INDEX IF HE WANTS
                    if (dsh_idx.getDs().getStatus().compareTo(CS_Constants.DS_OFFLINE) == 0)
                    {
                        continue;
                    }

                    if (!dsh_idx.test_flag(dsh_idx.getDs(), CS_Constants.DS_MODE_INDEX))
                    {
                        continue;
                    }

                    use_dsh_idx = dsh_idx;
                    break;
                }
                if (use_dsh_idx == null)
                {
                    set_status(Main.Txt("No index disk space found for disk space <" + dsf.getAbsolutePath() + ">"));
                    LogManager.msg_index( LogManager.LVL_ERR,getLast_msg());
                }
                try
                {
                    prepare(dsh, use_dsh_idx);
                }
                catch (VaultException vaultException)
                {
                    set_status(Main.Txt("Export_aborted:") + " " + vaultException.getMessage());
                    LogManager.msg_index( LogManager.LVL_ERR,getLast_msg(), vaultException);
                }
            }
        }
    }

    private void set_status( String Txt )
    {
        LogManager.msg_index( LogManager.LVL_DEBUG,Txt);
        last_msg = Txt;
    }

    /**
     * @return the busy
     */
    public boolean isBusy()
    {
        return busy;
    }

    /**
     * @return the last_msg
     */
    public String getLast_msg()
    {
        return last_msg;
    }

    /**
     * @return the percent_done
     */
    public double getPercent_done()
    {
        if (getTotal_size() == 0)
        {
            return 0;
        }

        double percent = 100.0 / getTotal_size();
        percent *= getAct_size();
        return percent;
    }

    /**
     * @return the total_size
     */
    public long getTotal_size()
    {
        long total_size = 0;
        for (int i = 0; i < export_list.size(); i++)
        {
            ExportDSHEntry exportDSHEntry = export_list.get(i);
            total_size += exportDSHEntry.getTotal_size();
        }
        return total_size;
    }

    /**
     * @return the total_cnt
     */
    public long getTotal_cnt()
    {
        long total_cnt = 0;
        for (int i = 0; i < export_list.size(); i++)
        {
            ExportDSHEntry exportDSHEntry = export_list.get(i);
            total_cnt += exportDSHEntry.getTotal_cnt();
        }
        return total_cnt;
    }

    /**
     * @return the act_size
     */
    public long getAct_size()
    {
        long act_size = 0;
        for (int i = 0; i < export_list.size(); i++)
        {
            ExportDSHEntry exportDSHEntry = export_list.get(i);
            act_size += exportDSHEntry.getAct_size();
        }
        return act_size;
    }

    /**
     * @return the act_cnt
     */
    public long getAct_cnt()
    {
        long act_cnt = 0;
        for (int i = 0; i < export_list.size(); i++)
        {
            ExportDSHEntry exportDSHEntry = export_list.get(i);
            act_cnt += exportDSHEntry.getAct_cnt();
        }
        return act_cnt;
    }

    public int getTotal_percent()
    {
        long ts = getTotal_size();
        if (ts == 0)
        {
            return 0;
        }

        return (int) ((getAct_size() * 100) / ts);

    }

    public void set_pause( boolean b )
    {
        this.pause = b;
    }

    public void set_abort( boolean b )
    {
        this.abort = b;
    }
    final StringBuffer stat_sb = new StringBuffer();

    public String get_statistics_string()
    {

        synchronized (stat_sb)
        {
            stat_sb.setLength(0);
            
            stat_sb.append("BS:");
            stat_sb.append(isBusy() ? "1" : "0");
            stat_sb.append(" PA:");
            stat_sb.append(pause ? "1" : "0");
            stat_sb.append(" AB:");
            stat_sb.append(abort ? "1" : "0");
            stat_sb.append(" PC:");
            stat_sb.append(getTotal_percent());
            stat_sb.append(" TCNT:");
            stat_sb.append(getTotal_cnt());
            stat_sb.append(" TSIZ:");
            stat_sb.append(getTotal_size());
            stat_sb.append(" ACNT:");
            stat_sb.append(getAct_cnt());
            stat_sb.append(" ASIZ:");
            stat_sb.append(getAct_size());
            if (act_exp_idx >= 0)
            {
                ExportDSHEntry exportDSHEntry = export_list.get(act_exp_idx);
                stat_sb.append(" RPA:\"");
                stat_sb.append(exportDSHEntry.getData_dsh().getDs().getPath());
                stat_sb.append("\"");
                stat_sb.append(" RDS:");
                stat_sb.append(exportDSHEntry.getData_dsh().getDs().getId());
                stat_sb.append(" RDA:");
                stat_sb.append(exportDSHEntry.getData_dsh().getDs().getDiskArchive().getId());
                stat_sb.append(" TRCNT:");
                stat_sb.append(getTotal_cnt());
                stat_sb.append(" TRSIZ:");
                stat_sb.append(getTotal_size());
                stat_sb.append(" ARCNT:");
                stat_sb.append(getAct_cnt());
                stat_sb.append(" ARSIZ:");
                stat_sb.append(getAct_size());
            }
            stat_sb.append(" PTH:\"");
            stat_sb.append(exportPath.getAbsolutePath());
            stat_sb.append("\"");
            stat_sb.append(" MSG:\"");
            stat_sb.append(getLast_msg());
            stat_sb.append("\"");
        }
        return stat_sb.toString();
    }

    public static void main( String[] args)
    {

    }

    private boolean exportMessage( RFCFileMail rfc, String uuid ) {
        
        List<RFCMailAddress> targetAddresses = retrieveValidtargetAddresses(rfc, uuid);
        
        // Gleichzeitig in N Streams schreiben
        List<OutputStream> osStreams = new ArrayList<OutputStream>(targetAddresses.size());        
        
        InputStream is = null;
        try {
            for (RFCMailAddress address : targetAddresses) {
                File expFile = new File(exportPath + "/" + getPathFromAdress(address), uuid + ".eml"  );
                if (!expFile.getParentFile().exists()) {
                    expFile.getParentFile().mkdir();
                }
                osStreams.add(new FileOutputStream(expFile));
            }

            is = rfc.open_inputstream();
            byte[] buffer = new byte[4096];
            while (true) {
                int rlen = is.read(buffer);
                if (rlen == -1) {
                    break;                    
                }
                for (OutputStream os: osStreams) {
                    os.write(buffer, 0, rlen);
                }
            }
        }
        catch (Exception exc) {
             LogManager.msg_index( LogManager.LVL_ERR,"Error writing Mail id " + uuid , exc);
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch (Exception ex) {
                     LogManager.msg_index( LogManager.LVL_ERR,"Error closing Mail id " + uuid , ex);
                }
            }
            
            for (OutputStream os: osStreams) {
                if (os != null) {
                    try {
                        os.close();
                    }
                    catch (Exception ex) {
                         LogManager.msg_index( LogManager.LVL_ERR,"Error closing Export Mail id " + uuid, ex);
                    }
                }
            }
        }
        return true;        
    }

    private String getPathFromAdress( RFCMailAddress adr ) {
       return adr.get_mail();
    }

    private List<RFCMailAddress> retrieveValidtargetAddresses( RFCFileMail rfc, String uuid ) {
        List<RFCMailAddress> targetAddresses = new ArrayList<RFCMailAddress>();
        for (MailAttribute attribute : rfc.getAttributes()) {
           // MailAttribute(MATTR_LUCENE, CS_Constants.FLD_BCC, address.toString());
            if (attribute.isType(MATTR_LUCENE) && attribute.isName(CS_Constants.FLD_BCC) ) {
                targetAddresses.add(new RFCMailAddress(attribute.getValue(), RFCMailAddress.ADR_TYPE.BCC));                
            }
            else if (attribute.isType(MATTR_LUCENE) && attribute.isName(CS_Constants.FLD_BCC) ) {
                targetAddresses.add(new RFCMailAddress(attribute.getValue(), RFCMailAddress.ADR_TYPE.BCC));                
            }
        }
        RFCMimeMail mimeMail = new RFCMimeMail();
        try {
            mimeMail.parse(rfc);
            List<RFCMailAddress> mails = mimeMail.getEmail_list();
            for (RFCMailAddress mail : mails) {
                targetAddresses.add(mail);                                                    
            }
        }
        catch (Exception exc) {
            LogManager.msg_index( LogManager.LVL_ERR,"Error reading Mail id " + uuid, exc);
        }
        // Doubletten raus
        RFCMimeMail.sort_email_list(targetAddresses);
        boolean toExists = false;
        // Alle fremden Adressen raus
        ArrayList<String> domain_list = context.get_index_manager().getAllowed_domains();
        for (int i = 0; i < targetAddresses.size(); i++) {
            RFCMailAddress address = targetAddresses.get(i);
            if (!domain_list.contains(address.get_domain())) {
                targetAddresses.remove(i);
                i--;
            }
            else {
                if (address.getAdr_type() == RFCMailAddress.ADR_TYPE.TO) {
                    toExists = true;
                }
            }            
        }
        // Wenn ein To existert, dann alle Froms raus -> wären sonst Duplikate
        if (toExists) {
            for (int i = 0; i < targetAddresses.size(); i++) {
                RFCMailAddress address = targetAddresses.get(i);
                if (address.getAdr_type().equals(RFCMailAddress.ADR_TYPE.FROM)) {
                    targetAddresses.remove(i);
                    i--;
                }
            }    
        }
        if (targetAddresses.isEmpty()) {
            LogManager.msg_index( LogManager.LVL_ERR,"Detected mail with no recipient, exporting to unknown");
            targetAddresses.add(new RFCMailAddress("unknown@unknown.exp", RFCMailAddress.ADR_TYPE.TO));
        }        
        return targetAddresses;
    }
}
