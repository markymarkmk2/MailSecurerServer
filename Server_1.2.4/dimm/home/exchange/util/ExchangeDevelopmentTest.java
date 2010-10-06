/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.exchange.util;

import com.microsoft.schemas.exchange.services._2006.messages.ExchangeServicePortType;
import dimm.home.exchange.ExchangeAuthenticator;
import dimm.home.exchange.dao.ItemTypeDAO;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

class RetrieveWSDLAuthenticator extends Authenticator
{
        private String username, password;

        public RetrieveWSDLAuthenticator(String user, String pass)
        {
            username = user;
            password = pass;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication()
        {
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }
/**
 * Throw-away class for doing quick tests of features and classes during
 * development.
 *
 * @author Reid Miller
 */
public class ExchangeDevelopmentTest
{

    /**
     * Main method so we can quickly test things.
     * @param args
     */
    public static void main( String[] args )
    {
        ExchangeAuthenticator exchangeAuthenticator = new ExchangeAuthenticator();
        ItemTypeDAO itemTypeDAO;

        // Print statement so we can easily see where our statements start in the Java console.
        System.out.println("Let's get started!");

        try
        {
            Authenticator.setDefault(new RetrieveWSDLAuthenticator("w2k8becom.dimm.home\\mw", "helikon42$"));
            // Create a URL object which points at the .wsdl we deployed in the previous step.
//            URL wsdlURL = new URL("http://localhost:8080/ExchangeWebServices/exchange.wsdl");
            URL baseUrl;
            baseUrl = com.microsoft.schemas.exchange.services._2006.messages.ExchangeWebService.class.getResource(".");
            URL wsdlURL = new URL(baseUrl, "file:/J:/Develop/Java/JMailArchiv/Server%201.2.4/src_1.4.4/wsdl/ews/Services.wsdl");
            // Call to the class we just created to return an ExchangeServicePortType with authentication credentials.
             ExchangeServicePortType port = exchangeAuthenticator.getExchangeServicePort("mw", "helikon42$", "w2k8becom.dimm.home", wsdlURL);

//            ExchangeServicePortType port = exchangeAuthenticator.getExchangeServicePort("username", "password", "DOMAIN", wsdlURL);

            // Prints out the default toString() for the ExchangeServicePortType.
            System.out.println(port.toString());

            // Test out the new ItemTypeDAO functionality.
            itemTypeDAO = new ItemTypeDAO();
            itemTypeDAO.getFolderItems(port);
        }
        catch (MalformedURLException ex)
        {
            // Catch any errors that may occur.
            Logger.getLogger(ExchangeDevelopmentTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        // Print statement so we can easily see where our statements start in the Java console.
        System.out.println("Thats it!");

    }
}
