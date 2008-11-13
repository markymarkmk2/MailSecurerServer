package dimm.home.mailproxyclient;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



/**
 *
 * @author Administrator
 */
public interface CommContainer 
{
    
    public StationEntry get_selected_box();
    public void set_status( String st );
    public Communicator get_comm();
   

}
