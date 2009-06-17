
package dimm.home.Httpd.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "StringConcat", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StringConcat", namespace = "http://Httpd.home.dimm/", propOrder = {
    "m1",
    "m2"
})
public class Stringconcat {

    @XmlElement(name = "m1", namespace = "")
    private String m1;
    @XmlElement(name = "m2", namespace = "")
    private dimm.home.Test.TestString m2;

    /**
     * 
     * @return
     *     returns String
     */
    public String getM1() {
        return this.m1;
    }

    /**
     * 
     * @param m1
     *     the value for the m1 property
     */
    public void setM1(String m1) {
        this.m1 = m1;
    }

    /**
     * 
     * @return
     *     returns TestString
     */
    public dimm.home.Test.TestString getM2() {
        return this.m2;
    }

    /**
     * 
     * @param m2
     *     the value for the m2 property
     */
    public void setM2(dimm.home.Test.TestString m2) {
        this.m2 = m2;
    }

}
