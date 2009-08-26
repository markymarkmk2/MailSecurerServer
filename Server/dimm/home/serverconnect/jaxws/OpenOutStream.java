
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "OpenOutStream", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OpenOutStream", namespace = "http://Httpd.home.dimm/", propOrder = {
    "streamName",
    "args"
})
public class OpenOutStream {

    @XmlElement(name = "stream_name", namespace = "")
    private String streamName;
    @XmlElement(name = "args", namespace = "")
    private String args;

    /**
     * 
     * @return
     *     returns String
     */
    public String getStreamName() {
        return this.streamName;
    }

    /**
     * 
     * @param streamName
     *     the value for the streamName property
     */
    public void setStreamName(String streamName) {
        this.streamName = streamName;
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
