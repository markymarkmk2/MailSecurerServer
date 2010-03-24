
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "Multiply", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Multiply", namespace = "http://Httpd.home.dimm/", propOrder = {
    "m1",
    "m2"
})
public class Get_malnehmen {

    @XmlElement(name = "m1", namespace = "")
    private int m1;
    @XmlElement(name = "m2", namespace = "")
    private int m2;

    /**
     * 
     * @return
     *     returns int
     */
    public int getM1() {
        return this.m1;
    }

    /**
     * 
     * @param m1
     *     the value for the m1 property
     */
    public void setM1(int m1) {
        this.m1 = m1;
    }

    /**
     * 
     * @return
     *     returns int
     */
    public int getM2() {
        return this.m2;
    }

    /**
     * 
     * @param m2
     *     the value for the m2 property
     */
    public void setM2(int m2) {
        this.m2 = m2;
    }

}
