package dimm.home.hibernate;
// Generated 25.06.2009 14:19:53 by Hibernate Tools 3.2.1.GA



/**
 * HotfolderExclude generated by hbm2java
 */
public class HotfolderExclude  implements java.io.Serializable {


     private int id;
     private Hotfolder hotfolder;
     private String name;
     private Integer flags;

    public HotfolderExclude() {
    }

	
    public HotfolderExclude(int id) {
        this.id = id;
    }
    public HotfolderExclude(int id, Hotfolder hotfolder, String name, Integer flags) {
       this.id = id;
       this.hotfolder = hotfolder;
       this.name = name;
       this.flags = flags;
    }
   
    public int getId() {
        return this.id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    public Hotfolder getHotfolder() {
        return this.hotfolder;
    }
    
    public void setHotfolder(Hotfolder hotfolder) {
        this.hotfolder = hotfolder;
    }
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    public Integer getFlags() {
        return this.flags;
    }
    
    public void setFlags(Integer flags) {
        this.flags = flags;
    }




}


