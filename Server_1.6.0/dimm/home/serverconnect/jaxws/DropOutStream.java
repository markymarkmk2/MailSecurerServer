
package dimm.home.serverconnect.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "DropOutStreamFile", namespace = "http://Httpd.home.dimm/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DropOutStreamFile", namespace = "http://Httpd.home.dimm/")
public class DropOutStream {

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
