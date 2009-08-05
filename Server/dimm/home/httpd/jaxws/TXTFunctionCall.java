
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "TXTFunctionCall", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TXTFunctionCall", namespace = "http://Httpd.home.dimm/", propOrder = {
    "funcName",
    "args"
})
public class TXTFunctionCall {

    @XmlElement(name = "func_name", namespace = "")
    private String funcName;
    @XmlElement(name = "args", namespace = "")
    private String args;

    /**
     * 
     * @return
     *     returns String
     */
    public String getFuncName() {
        return this.funcName;
    }

    /**
     * 
     * @param funcName
     *     the value for the funcName property
     */
    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getArgs() {
        return this.args;
    }

    /**
     * 
     * @param args
     *     the value for the args property
     */
    public void setArgs(String args) {
        this.args = args;
    }

}
