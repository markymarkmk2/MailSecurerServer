
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "getSQLArrayResult", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getSQLArrayResult", namespace = "http://Httpd.home.dimm/")
public class GetSQLArrayResult {

    @XmlElement(name = "resultset", namespace = "")
    private String resultset;

    /**
     * 
     * @return
     *     returns String
     */
    public String getResultset() {
        return this.resultset;
    }

    /**
     * 
     * @param resultset
     *     the value for the resultset property
     */
    public void setResultset(String resultset) {
        this.resultset = resultset;
    }

}
