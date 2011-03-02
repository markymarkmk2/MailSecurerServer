using System;
using System.Collections;
using System.ComponentModel;
using System.Drawing;
using System.Windows.Forms;
using System.Runtime.InteropServices;
 
namespace MSOutlookPlugin
{
    /// <summary>
    /// Add-in Express Outlook Option Page
    /// </summary>
    [GuidAttribute("6AEB4C01-F49D-4A13-8F85-D886CDF86F9A"), ProgId("MSOutlookPlugin.PropertyPage1")]
    public class PropertyPage1 : AddinExpress.MSO.ADXOlPropertyPage
    {
        public PropertyPage1()
        {
            // This call is required by the Component Designer
            InitializeComponent();
 
        }
 
        #region Component Designer generated code
        /// <summary>
        /// Required by designer
        /// </summary>
        private System.ComponentModel.Container components = null;
 
        /// <summary>
        /// Required by designer - do not modify
        /// the following method
        /// </summary>
        private void InitializeComponent()
        {
            components = new System.ComponentModel.Container();
            //
            // PropertyPage1
            //
            this.Name = "PropertyPage1";
            this.Size = new System.Drawing.Size(413, 358);
        }
        #endregion
 
        /// <summary>
        /// Clean up any resources being used
        /// </summary>
        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                if(components != null)
                {
                    components.Dispose();
                }
            }
            base.Dispose(disposing);
        }
 
    }
}

