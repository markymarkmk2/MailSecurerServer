
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "getQuery", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getQuery", namespace = "http://Httpd.home.dimm/")
public class GetQuery {

    @XmlElement(name = "qry", namespace = "")
    private String qry;

    /**
     * 
     * @return
     *     returns String
     */
    public String getQry() {
        return this.qry;
    }

    /**
     * 
     * @param qry
     *     the value for the qry property
     */
    public void setQry(String qry) {
        this.qry = qry;
    }

}
