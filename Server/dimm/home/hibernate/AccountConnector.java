package dimm.home.hibernate;
// Generated 29.05.2009 11:20:51 by Hibernate Tools 3.2.1.GA



/**
 * AccountConnector generated by hbm2java
 */
public class AccountConnector  implements java.io.Serializable {


     private int id;
     private Mandant mandant;
     private String type;
     private String ip;
     private Integer port;

    public AccountConnector() {
    }

	
    public AccountConnector(int id) {
        this.id = id;
    }
    public AccountConnector(int id, Mandant mandant, String type, String ip, Integer port) {
       this.id = id;
       this.mandant = mandant;
       this.type = type;
       this.ip = ip;
       this.port = port;
    }
   
    public int getId() {
        return this.id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    public Mandant getMandant() {
        return this.mandant;
    }
    
    public void setMandant(Mandant mandant) {
        this.mandant = mandant;
    }
    public String getType() {
        return this.type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    public String getIp() {
        return this.ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    public Integer getPort() {
        return this.port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }




}


