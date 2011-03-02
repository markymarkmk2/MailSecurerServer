using System;
using System.Runtime.InteropServices;
using System.ComponentModel;
using System.Windows.Forms;
using System.Diagnostics;
using Microsoft.Win32;
/*
using net.sf.jni4net;
using net.sf.jni4net.adaptors;
using tbsearchgui;*/
using System.IO;

using IOutlook = Microsoft.Office.Interop.Outlook;
using Outlook = Microsoft.Office.Interop.Outlook;

using Redemption;




namespace MSOutlookPlugin
{
    /// <summary>
    ///   Add-in Express Add-in Module
    /// </summary>
    /// 



    [GuidAttribute("17D6B425-5D0A-4B24-A9D5-CB90B0B5C060"), ProgId("MSOutlookPlugin.AddinModule")]
    public class AddinModule : AddinExpress.MSO.ADXAddinModule
    {
	
        static string prefs_folder = "";
        public static Outlook.MAPIFolder newFolder = null;
        FileSystemWatcher incoming = null;
        private ImageList imageList1;
        Process java_app = null;
        
        

        public AddinModule()
        {
            log("AddinModule started");

            try
            {
                InitializeComponent();

                if (!has_search_text())
                {
                    this.adxOlExplorerCommandBar1.Controls.Remove(this.adxCommandBarEdit1);
                }
                // Please add any initialization code to the AddinInitialize event handler
                adxCommandBarButton1.Click += new AddinExpress.MSO.ADXClick_EventHandler(DoClick);
                if (has_search_text())
                {
                    adxCommandBarEdit1.Change += new AddinExpress.MSO.ADXChange_EventHandler(DoChange);
                }

                //ItemsEvents = new OutlookItemsEventsClass1(this);

                this.AddinBeginShutdown += new AddinExpress.MSO.ADXEvents_EventHandler(this.AddinModule_AddinBeginShutdown);
                this.AddinFinalize += new AddinExpress.MSO.ADXEvents_EventHandler(this.AddinModule_AddinFinalize);
                this.AddinStartupComplete += new AddinExpress.MSO.ADXEvents_EventHandler(this.AddinModule_Startup);

                //this.OnSendMessage += new AddinExpress.MSO.ADXSendMessage_EventHandler(AddinModule_OnSendMessage);

                java_app = null;
                log("AddinModule finished");
            }
            catch (Exception exc)
            {
                log("AddinModule failed: " + exc.Message);
            }


            

        }
     
        //private ImageList imageList2;
        private AddinExpress.MSO.ADXOlExplorerCommandBar adxOlExplorerCommandBar1;
        private AddinExpress.MSO.ADXCommandBarButton adxCommandBarButton1;
        private AddinExpress.MSO.ADXCommandBarEdit adxCommandBarEdit1;
        private AddinExpress.MSO.ADXOutlookAppEvents adxOutlookEvents;
       // private ImageList images;
 
        #region Component Designer generated code
        /// <summary>
        /// Required by designer
        /// </summary>
        private System.ComponentModel.IContainer components;

 
        /// <summary>
        /// Required by designer support - do not modify
        /// the following method
        /// </summary>
        private void InitializeComponent()
        {
            this.components = new System.ComponentModel.Container();
            System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(AddinModule));
            this.adxOlExplorerCommandBar1 = new AddinExpress.MSO.ADXOlExplorerCommandBar(this.components);
            this.adxCommandBarButton1 = new AddinExpress.MSO.ADXCommandBarButton(this.components);
            this.adxCommandBarEdit1 = new AddinExpress.MSO.ADXCommandBarEdit(this.components);
            this.adxOutlookEvents = new AddinExpress.MSO.ADXOutlookAppEvents(this.components);
            this.imageList1 = new System.Windows.Forms.ImageList(this.components);
            // 
            // adxOlExplorerCommandBar1
            // 
            this.adxOlExplorerCommandBar1.CommandBarName = "MSCommandBar";
            this.adxOlExplorerCommandBar1.CommandBarTag = "03cc9f7f-0125-4035-99ee-09adc852e5cb";
            this.adxOlExplorerCommandBar1.Controls.Add(this.adxCommandBarButton1);
            this.adxOlExplorerCommandBar1.Controls.Add(this.adxCommandBarEdit1);
            this.adxOlExplorerCommandBar1.Temporary = true;
            this.adxOlExplorerCommandBar1.UpdateCounter = 3;
            // 
            // adxCommandBarButton1
            // 
            this.adxCommandBarButton1.ControlTag = "7168a4f9-31cd-4469-ac6a-53f3b2bf5561";
            this.adxCommandBarButton1.Image = 0;
            this.adxCommandBarButton1.ImageList = this.imageList1;
            this.adxCommandBarButton1.ImageTransparentColor = System.Drawing.Color.Transparent;
            this.adxCommandBarButton1.Style = AddinExpress.MSO.ADXMsoButtonStyle.adxMsoButtonIconAndCaption;
            this.adxCommandBarButton1.Temporary = true;
            this.adxCommandBarButton1.TooltipText = "Open MailSecurer Search";
            this.adxCommandBarButton1.UpdateCounter = 9;
            // 
            // adxCommandBarEdit1
            // 
            this.adxCommandBarEdit1.Caption = "adxCommandBarEdit1";
            this.adxCommandBarEdit1.ControlTag = "507481ec-0c09-4b43-a657-a40443d0fac8";
            this.adxCommandBarEdit1.Temporary = true;
            this.adxCommandBarEdit1.TooltipText = "MailSecurer Search";
            this.adxCommandBarEdit1.UpdateCounter = 1;
            // 
            // imageList1
            // 
            this.imageList1.ImageStream = ((System.Windows.Forms.ImageListStreamer)(resources.GetObject("imageList1.ImageStream")));
            this.imageList1.TransparentColor = System.Drawing.Color.Transparent;
            this.imageList1.Images.SetKeyName(0, "icon.bmp");
            // 
            // AddinModule
            // 
            this.AddinName = "MSOutlookPlugin";
            this.Description = "OutlookPlugin for MailSecurer";
            this.RegisterForAllUsers = true;
            this.SupportedApps = AddinExpress.MSO.ADXOfficeHostApp.ohaOutlook;

        }
        #endregion
 
        #region Add-in Express automatic code
 
        // Required by Add-in Express - do not modify
        // the methods within this region
 
        public override System.ComponentModel.IContainer GetContainer()
        {
            if (components == null)
                components = new System.ComponentModel.Container();
            return components;
        }
 
        [ComRegisterFunctionAttribute]
        public static void AddinRegister(Type t)
        {
            AddinExpress.MSO.ADXAddinModule.ADXRegister(t);
        }
 
        [ComUnregisterFunctionAttribute]
        public static void AddinUnregister(Type t)
        {
            AddinExpress.MSO.ADXAddinModule.ADXUnregister(t);
        }
 
        public override void UninstallControls()
        {
            base.UninstallControls();
        }

        #endregion

        public Outlook._Application OutlookApp
        {
            get
            {
                return (HostApplication as Outlook._Application);
            }
        }

        // True if this object is attached to an Explorer window.
        private bool showError = false;

        public bool has_search_text()
        {
            string path = GetPluginPath() + "\\no_text_search.txt";
            if (System.IO.File.Exists(path))
                return false;

            return true;
        }

        public string get_java_exe()
        {
            // TRY TO DETECT LOCAL INSTALLED JRE
            string path = GetPluginPath() + "\\jre6\\bin\\javaw.exe";
            if (System.IO.File.Exists(path))
                return path;

            return "javaw";
        }

        private void DoClick(object sender)
        {
            init_plugin();

            string path = GetPluginPath();

            string userFolder = get_prefs_folder();

            

            if (java_app == null || java_app.HasExited)
            {
                java_app = new Process();
                java_app.StartInfo.FileName = get_java_exe();
                java_app.StartInfo.Arguments = "-jar \"" + path + "\\TBSearchGui.jar\"" + " \"" + userFolder + "\"";
                //java_app.StartInfo.UseShellExecute = true;
                java_app.StartInfo.RedirectStandardOutput = false;
                //java_app.StartInfo.CreateNoWindow = true;

                java_app.Start();

            }
            else
            {
               StreamWriter wr = System.IO.File.CreateText(get_prefs_folder() + "\\cmd.txt");
               wr.WriteLine("ToFront");
               wr.Close();
            }
/*
            java.lang.String[] args = new java.lang.String[0];
           
            call_java(path, args);
            */

        }

        private string get_prefs_folder()
        {
            string userFolder = System.Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData) + "\\MSSearch";
            if (!System.IO.Directory.Exists(userFolder))
            {
                System.IO.Directory.CreateDirectory(userFolder);
            }
            return userFolder;
        }

        private void DoChange(object sender)
        {
            if (!has_search_text())
                return;
            init_plugin();

            string qry = adxCommandBarEdit1.Text;

            string path = GetPluginPath();

            string userFolder = get_prefs_folder();            
            
            if (java_app == null || java_app.HasExited)
            {
                java_app = new Process();
                java_app.StartInfo.FileName = get_java_exe();
                java_app.StartInfo.Arguments = "-jar \"" + path + "\\TBSearchGui.jar\"" + " \"" + userFolder + "\"";
                if (qry.Length > 0)
                {
                    java_app.StartInfo.Arguments += " -qry \"" + qry + "\"";

                    //java_app.StartInfo.UseShellExecute = true;
                    java_app.StartInfo.RedirectStandardOutput = false;
                    //java_app.StartInfo.CreateNoWindow = true;

                    java_app.Start();

                }
            }
            else
            {
                StreamWriter wr = System.IO.File.CreateText(get_prefs_folder() + "\\cmd.txt");
                wr.WriteLine("ToFront");
                wr.Close();
            }
            Outlook.Explorer activeExplorer = OutlookApp.ActiveExplorer();

            activeExplorer.CurrentFolder = newFolder;
            

            
            

        }
        /*
        private void call_java(string plugin_path, java.lang.String[] args)
        {

            // create bridge, with default setup
            // it will lookup jni4net.j.jar next to jni4net.n.dll
            string homeDll = typeof(Bridge).Assembly.Location;
            string jar = homeDll.Replace(".dll", ".jar").Replace("jni4net.n", "jni4net.j");
            if (File.Exists(jar))
            {
                jar = "";
            }
            

            BridgeSetup bridge = new BridgeSetup();
            bridge.AddBridgeClassPath();
            
            bridge.AddClassPath( plugin_path + "\\TBSearchGui.jar");
            bridge.AddClassPath( plugin_path + "\\TBSearchGui.jar");
            bridge.BindStatic = false;


            Bridge.CreateJVM(bridge);
            Bridge.RegisterAssembly(typeof(Loader).Assembly);

            
            Loader.main(args);

        }*/


        private void ShowErrorMessage(Exception e, string Title)
        {
            log(Title + ": " + e.Message);

            if (showError == true)
                MessageBox.Show(e.Message,
                            Title
                            , MessageBoxButtons.OK
                            , MessageBoxIcon.Error);
        }

        public static void log( string txt )
        {
            
            // Create a writer and open the file:
            StreamWriter log;
            string log_file = prefs_folder + "\\pluginlog.txt";

            if (!File.Exists(log_file))
            {
              log = new StreamWriter(log_file);
            }
            else
            {
              log = File.AppendText(log_file);
            }

            // Write to the file:
            log.WriteLine(DateTime.Now + ": " + txt);
            
            // Close the stream:
            log.Close();
        }

        private string GetPluginPath()
        {
            // Opening the registry key
            RegistryKey base_key = Registry.LocalMachine;

            string subKey = "SOFTWARE\\MailSecurerOutlookPlugin";
            RegistryKey rk = base_key;
            // Open a subKey as read-only

            RegistryKey sk1 = rk.OpenSubKey(subKey);
            // If the RegistrySubKey doesn't exist -> (null)

            if (sk1 == null)
            {
                return null;
            }
            else
            {
                try
                {
                    // If the RegistryKey exists I get its value

                    // or null is returned.

                    return (string)sk1.GetValue("Path");
                }
                catch (Exception e)
                {
                    // AAAAAAAAAAARGH, an error!

                    ShowErrorMessage(e, "Reading registry path");
                    return null;
                }
            }
        }


        // Outlook 2000 - 2007 (usual way) 
        private void AddinModule_AddinBeginShutdown(object sender, EventArgs e)
        {
            log("AddinModule_AddinBeginShutdown");
            HandleAddinBeginShutdown();
        }
        private void AddinModule_Startup(object sender, EventArgs e)
        {
            log("AddinModule_Startup");
            
        } 
 
        // Outlook 2000 - 2007 (usual way) 
        private void AddinModule_AddinFinalize(object sender, EventArgs e) 
        {
            log("AddinModule_AddinFinalize");
            HandleAddinFinalize(); 
        } 
 
        // Outlook 2010 - perform clean-up for Outlook 2010 Fast Shutdown mode 
        private void adxOutlookEvents_Quit(object sender, EventArgs e) 
        {
            log("adxOutlookEvents_Quit");
            if (this.OutlookShutdownBehavior == AddinExpress.MSO.OutlookShutdownBehavior.Fast) 
              { 
                    HandleAddinBeginShutdown(); 
                    HandleAddinFinalize(); 
              } 
        } 
 
        private void HandleAddinBeginShutdown() 
        { 
            //this.finish_thread = true;
            if (newFolder != null)
                Marshal.ReleaseComObject(newFolder);
            if (incoming != null)
                incoming.Dispose();
              // cleaning up any used resources, closing connections, etc. 
        } 
 
        private void HandleAddinFinalize() 
        { 
              // cleaning up any used resources, closing connections, etc. 
        }



        void init_fs_watcher()
        {
            if (incoming != null)
                return;

            incoming = new FileSystemWatcher();
            incoming.BeginInit();
            incoming.Path = get_prefs_folder();
            incoming.NotifyFilter = NotifyFilters.LastAccess |
                                    NotifyFilters.LastWrite |
                                    NotifyFilters.FileName |
                                    NotifyFilters.DirectoryName;
            //            incoming.Filter = "req.txt";
            incoming.Filter = "";

            incoming.Deleted += new FileSystemEventHandler(OnChanged);
            incoming.Changed += new FileSystemEventHandler(OnChanged);
            incoming.Created += new FileSystemEventHandler(OnChanged);
            incoming.Renamed += new RenamedEventHandler(OnRenamed);
            incoming.EndInit();

            incoming.EnableRaisingEvents = true;
        }
        public void OnRenamed(object source, RenamedEventArgs e)
        {
            if (e.Name.Equals("req.txt"))
            {
                try
                {
                    if (work_callback() != 0)
                    {
                        System.Threading.Thread.Sleep(200);
                        if (work_callback() != 0)
                        {
                            log("Cannot read req.txt");
                        }
                    }
                }
                catch (Exception exc)
                {
                    log("workcallback failed:" + exc.Message);
                }
            }
        }

        public void OnChanged(object source, FileSystemEventArgs e)
        {
            if (e.Name.Equals("req.txt"))
            {
                try
                {
                    if (work_callback() != 0)
                    {
                        System.Threading.Thread.Sleep(200);
                        if (work_callback() != 0)
                        {
                            log("Cannot read req.txt");
                        }
                    }
                }
                catch (Exception exc)
                {
                    log("workcallback failed:" + exc.Message);
                }
            }
        }
        
        

        public int work_callback()
        {
            string req_file = get_prefs_folder() + "\\req.txt";

            if (!System.IO.File.Exists(req_file))
                return 0;

            System.Collections.ArrayList display_list = new System.Collections.ArrayList();


            StreamReader rd = null;
            int ret = 0;

            try
            {
                rd = System.IO.File.OpenText(req_file);

                while (!rd.EndOfStream)
                {
                    string line = rd.ReadLine();
                    if (line.Equals("Display"))
                    {
                        line = rd.ReadLine();
                        display_list.Add(line);
                    }
                }
                rd.Close();
                rd = null;
                System.IO.File.Delete(req_file);
            }
            catch (Exception exc)
            {                
                ret = 1;
            }

            if (rd != null)
            {
                rd.Close();
            }


            clear_folder();
            
            for (int i = 0; i < display_list.Count; i++)
            {
                string value = display_list[i] as string;
                add_new_mail(value);
            }

            display_list.Clear();
            return ret;
        }

        

        public static void add_mail(string path)
        {
            
            try
            {
                Redemption.ISafeMailItem sItem = new Redemption.SafeMailItem();
                Outlook.MailItem newMail = newFolder.Items.Add(Outlook.OlItemType.olMailItem) as Outlook.MailItem;

                sItem.Item = newMail;

                sItem.Import(path, 1024);
                newMail.Move(newFolder);

                Marshal.ReleaseComObject(newMail);
                Marshal.ReleaseComObject(sItem);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Could not add result mail " + path + " to MS folder: " + ex.Message,
                    "Add Mail",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
            
        }

        public static void add_new_mail(string path)
        {
            
            if (System.IO.File.Exists(path))
            {
                add_mail(path);
                System.IO.File.Delete(path);
            }
        }
        

        public static void select_mail(int idx)
        {
            Outlook.MAPIFolder newFolder = AddinModule.newFolder;
        }



        void init_plugin()
        {
            prefs_folder = get_prefs_folder();

            init_fs_watcher();

            string req_file = get_prefs_folder() + "\\req.txt";

            try
            {
                if (System.IO.File.Exists(req_file))
                {
                    System.IO.File.Delete(req_file);
                }
            }
            catch (Exception)
            {
            }

            add_ms_folder();
        }
       

        private void add_ms_folder()
        {
            string ms_folder_name = "MailSecurer Suche";
            Outlook.Application app = HostApplication as Outlook.Application;
            Outlook.MAPIFolder folder = app.Session.GetDefaultFolder(Outlook.OlDefaultFolders.olFolderInbox) as Outlook.MAPIFolder;
            folder = folder.Parent as Outlook.MAPIFolder;
            Outlook.Folders folders = folder.Folders;
            try
            {
                if (newFolder == null)
                {
                    Outlook.MAPIFolder f;
                    f = folders.GetFirst();
                    while (f != null)
                    {
                        if (f.Name.Equals(ms_folder_name))
                            break;

                        f = folders.GetNext();

                    }

                    if (f == null)
                        newFolder = folders.Add(ms_folder_name, Outlook.OlDefaultFolders.olFolderDrafts) as Outlook.MAPIFolder;
                    else
                        newFolder = f;
                }
                clear_folder();                

            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Could not add MS result folder: " + ex.Message,
                    "Add Folder",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

                void clear_folder()
                {
                    
                    if (newFolder == null)
                        return;

                    for (int i = newFolder.Items.Count; i > 0; i--)
                    {
                        newFolder.Items.Remove(i);
                    }
                }

        

    
    }
  
}

