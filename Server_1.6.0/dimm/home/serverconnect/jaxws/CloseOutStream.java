
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "CloseOutStream", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CloseOutStream", namespace = "http://Httpd.home.dimm/")
public class CloseOutStream {

    @XmlElement(name = "stream_id", namespace = "")
    private String streamId;

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

}
