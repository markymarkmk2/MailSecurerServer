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


        addDefaultImplementation(org.hibernate.collection.PersistentList.class, java.util.ArrayList.class);
        addDefaultImplementation(org.hibernate.collection.PersistentMap.class, java.util.HashMap.class);
        addDefaultImplementation(org.hibernate.collection.PersistentSet.class, java.util.HashSet.class);

        Mapper mapper = getMapper();
        registerConverter(new HibernateCollectionConverter(mapper));
        registerConverter(new HibernateMapConverter(mapper));

    }
}

