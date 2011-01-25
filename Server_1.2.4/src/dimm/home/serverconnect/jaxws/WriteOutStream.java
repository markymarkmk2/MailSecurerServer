
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "WriteOutStream", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WriteOutStream", namespace = "http://Httpd.home.dimm/", propOrder = {
    "streamId",
    "arg1"
})
public class WriteOutStream {

    @XmlElement(name = "stream_id", namespace = "")
    private String streamId;
    @XmlElement(name = "arg1", namespace = "", nillable = true)
    private byte[] arg1;

    /**
     * 
     * @return
     *     returns String
     */
    public String getStreamId() {
        return this.streamId;
    }

    /**
     * 
     * @param streamId
     *     the value for the streamId property
     */
    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    /**
     * 
     * @return
     *     returns byte[]
     */
    public byte[] getArg1() {
        return this.arg1;
    }

    /**
     * 
     * @param arg1
     *     the value for the arg1 property
     */
    public void setArg1(byte[] arg1) {
        this.arg1 = arg1;
    }

}
