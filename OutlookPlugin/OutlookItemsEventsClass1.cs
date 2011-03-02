using System;
 
namespace MSOutlookPlugin
{
    /// <summary>
    /// Add-in Express Outlook Items Events Class
    /// </summary>
    public class OutlookItemsEventsClass1 : AddinExpress.MSO.ADXOutlookItemsEvents
    {
        public OutlookItemsEventsClass1(AddinExpress.MSO.ADXAddinModule module): base(module)
        {
        }
 
        public override void ProcessItemAdd(object item)
        {
            // TODO: Add some code
        }
 
        public override void ProcessItemChange(object item)
        {
            // TODO: Add some code
        }
 
        public override void ProcessItemRemove()
        {
            // TODO: Add some code
        }
 
        public override void ProcessBeforeFolderMove(object moveTo, AddinExpress.MSO.ADXCancelEventArgs e)
        {
            // TODO: Add some code
        }
 
        public override void ProcessBeforeItemMove(object item, object moveTo, AddinExpress.MSO.ADXCancelEventArgs e)
        {
            // TODO: Add some code
        }
    }
}

