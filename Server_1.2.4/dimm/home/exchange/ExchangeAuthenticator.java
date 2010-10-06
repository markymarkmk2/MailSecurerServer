/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.exchange;

import com.microsoft.schemas.exchange.services._2006.messages.ExchangeServicePortType;
import com.microsoft.schemas.exchange.services._2006.messages.ExchangeWebService;
import com.sun.xml.ws.developer.JAXWSProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;

class DefaultTrueHostnameVerifier implements  HostnameVerifier
{

    @Override
    public boolean verify( String hostname, SSLSession session )
    {
       return true;
    }
}
/**
 * This class manages web services authentication requests to the Exchange
 * server.
 *
 * @author Reid Miller
 */
public class ExchangeAuthenticator
{

    /**
     * Obtains an authenticated ExchangeServicePortType with given credentials.
     *
     * See https://jax-ws.dev.java.net/faq/index.html#auth
     *
     * @param username
     * @param password
     * @param domain
     * @param wsdlURL
     * @return ExchangeServicePortType
     * @throws MalformedURLException
     */
    public ExchangeServicePortType getExchangeServicePort( String username, String password, String domain, URL wsdlURL ) throws MalformedURLException
    {
        // Concatinate our domain and username for the UID needed in authentication.
        String uid = domain + "\\" + username;

        // Create an ExchangeWebService object that uses the supplied WSDL file, wsdlURL.
        ExchangeWebService exchangeWebService = new ExchangeWebService(wsdlURL, new QName("http://schemas.microsoft.com/exchange/services/2006/messages", "ExchangeWebService"));
        ExchangeServicePortType port = exchangeWebService.getExchangeWebPort();
        BindingProvider bp = (BindingProvider)port;

        // Supply your username and password when the ExchangeServicePortType is used for binding in the SOAP request.
        Map m = bp.getRequestContext();
        bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, uid);
        bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);

        Map<String,List<String>> map = new HashMap<String,List<String>>();
        map.put("Content-Type", Collections.singletonList("text/xml; charset=\"utf-8\"") );

        bp.getRequestContext().put(MessageContext.HTTP_REQUEST_HEADERS, map);

        bp.getRequestContext().put(JAXWSProperties.HOSTNAME_VERIFIER, new DefaultTrueHostnameVerifier());


        return port;
    }
}
