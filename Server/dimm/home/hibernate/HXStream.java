/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.hibernate;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.mapper.Mapper;

class HibernateCollectionConverter extends CollectionConverter
{

    HibernateCollectionConverter( Mapper mapper )
    {
        super(mapper);
    }

    @Override
    public boolean canConvert( Class type )
    {
        return super.canConvert(type) || org.hibernate.collection.PersistentList.class.equals(type) || org.hibernate.collection.PersistentSet.class.equals(type);
    }

}



class HibernateMapConverter extends MapConverter
{

    HibernateMapConverter( Mapper mapper )
    {
        super(mapper);
    }

    @Override
    public boolean canConvert( Class type )
    {
        return super.canConvert(type) || org.hibernate.collection.PersistentMap.class.equals(type);
    }
}

/**
 *
 * @author mw
 */
public class HXStream extends XStream
{

    public HXStream()
    {


        addDefaultImplementation(java.util.ArrayList.class, org.hibernate.collection.PersistentList.class);
        addDefaultImplementation(java.util.HashMap.class, org.hibernate.collection.PersistentMap.class);
        addDefaultImplementation(java.util.HashSet.class, org.hibernate.collection.PersistentSet.class);

        Mapper mapper = getMapper();
        registerConverter(new HibernateCollectionConverter(mapper));
        registerConverter(new HibernateMapConverter(mapper));

    }
}

