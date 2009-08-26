
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "createStatement", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "createStatement", namespace = "http://Httpd.home.dimm/")
public class CreateStatement {

    @XmlElement(name = "conn_id", namespace = "")
    private String connId;

    /**
     * 
     * @return
     *     returns String
     */
    public String getConnId() {
        return this.connId;
    }

    /**
     * 
     * @param connId
     *     the value for the connId property
     */
    public void setConnId(String connId) {
        this.connId = connId;
    }

}
