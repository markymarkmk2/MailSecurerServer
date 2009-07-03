
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "close", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "close", namespace = "http://Httpd.home.dimm/")
public class Close {

    @XmlElement(name = "conn_txt", namespace = "")
    private String connTxt;

    /**
     * 
     * @return
     *     returns String
     */
    public String getConnTxt() {
        return this.connTxt;
    }

    /**
     * 
     * @param connTxt
     *     the value for the connTxt property
     */
    public void setConnTxt(String connTxt) {
        this.connTxt = connTxt;
    }

}
