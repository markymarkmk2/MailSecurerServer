/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.exchange;

import com.microsoft.schemas.exchange.services._2006.messages.ExchangeServicePortType;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        // Print statement so we can easily see where our statements start in the Java console.
        System.out.println("Let's get started!");

        try
        {
            // Create a URL object which points at the .wsdl we deployed in the previous step.
//            URL wsdlURL = new URL("http://localhost:8080/[Your Project Name]/exchange.wsdl");
            URL baseUrl;
            baseUrl = com.microsoft.schemas.exchange.services._2006.messages.ExchangeWebService.class.getResource(".");
            URL wsdlURL = new URL(baseUrl, "file:/J:/Develop/Java/JMailArchiv/Server%201.2.4/src_1.4.4/wsdl/ews/Services.wsdl");
            // Call to the class we just created to return an ExchangeServicePortType with authentication credentials.
            ExchangeServicePortType port = exchangeAuthenticator.getExchangeServicePort("Administrator", "helikon42\"", "dimm.home", wsdlURL);

            // Prints out the default toString() for the ExchangeServicePortType.
            System.out.println(port.toString());
        }
        catch (MalformedURLException ex)
        {
            // Catch any errors that may occur.
            Logger.getLogger(ExchangeDevelopmentTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
