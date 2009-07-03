
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "getSQLFirstRowField", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getSQLFirstRowField", namespace = "http://Httpd.home.dimm/", propOrder = {
    "connText",
    "qry",
    "field"
})
public class GetSQLFirstRowField {

    @XmlElement(name = "conn_text", namespace = "")
    private String connText;
    @XmlElement(name = "qry", namespace = "")
    private String qry;
    @XmlElement(name = "field", namespace = "")
    private int field;

    /**
     * 
     * @return
     *     returns String
     */
    public String getConnText() {
        return this.connText;
    }

    /**
     * 
     * @param connText
     *     the value for the connText property
     */
    public void setConnText(String connText) {
        this.connText = connText;
    }

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

    /**
     * 
     * @return
     *     returns int
     */
    public int getField() {
        return this.field;
    }

    /**
     * 
     * @param field
     *     the value for the field property
     */
    public void setField(int field) {
        this.field = field;
    }

}
