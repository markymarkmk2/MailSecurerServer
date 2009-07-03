
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "execute", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "execute", namespace = "http://Httpd.home.dimm/", propOrder = {
    "statement",
    "cmd"
})
public class Execute {

    @XmlElement(name = "statement", namespace = "")
    private String statement;
    @XmlElement(name = "cmd", namespace = "")
    private String cmd;

    /**
     * 
     * @return
     *     returns String
     */
    public String getStatement() {
        return this.statement;
    }

    /**
     * 
     * @param statement
     *     the value for the statement property
     */
    public void setStatement(String statement) {
        this.statement = statement;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getCmd() {
        return this.cmd;
    }

    /**
     * 
     * @param cmd
     *     the value for the cmd property
     */
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

}
