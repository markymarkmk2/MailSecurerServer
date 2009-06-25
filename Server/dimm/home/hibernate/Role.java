package dimm.home.hibernate;
// Generated 25.06.2009 14:19:53 by Hibernate Tools 3.2.1.GA



/**
 * Role generated by hbm2java
 */
public class Role  implements java.io.Serializable {


     private int id;
     private Mandant mandant;
     private String name;
     private Integer license;

    public Role() {
    }

	
    public Role(int id) {
        this.id = id;
    }
    public Role(int id, Mandant mandant, String name, Integer license) {
       this.id = id;
       this.mandant = mandant;
       this.name = name;
       this.license = license;
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
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    public Integer getLicense() {
        return this.license;
    }
    
    public void setLicense(Integer license) {
        this.license = license;
    }




}


