
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "open", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "open", namespace = "http://Httpd.home.dimm/")
public class Open {

    @XmlElement(name = "db_name", namespace = "")
    private String dbName;

    /**
     * 
     * @return
     *     returns String
     */
    public String getDbName() {
        return this.dbName;
    }

    /**
     * 
     * @param dbName
     *     the value for the dbName property
     */
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

}
