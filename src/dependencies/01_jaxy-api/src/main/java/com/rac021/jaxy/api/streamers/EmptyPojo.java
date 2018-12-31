
package com.rac021.jaxy.api.streamers ;

import java.io.Serializable ;
import javax.xml.bind.annotation.XmlElement ;
import javax.xml.bind.annotation.XmlAccessType ;
import javax.xml.bind.annotation.XmlRootElement ;
import javax.xml.bind.annotation.XmlAccessorType ;

/**
 *
 * @author yahiaoui
 *
 * Enable Moxy ( JAX-B Implementation ) .
 */

/** When moving this class, you have to : 
   1- UPDATE the Path : com/rac021/jaxy/api/streamers/jaxb.properties" in the Web Sources
   2- UPDATE the Package name in the resource  : com/rac021/jaxy/api/streamers/jaxb.properties" .
*/

@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)

public class EmptyPojo implements Serializable {

    @XmlElement
    private String name ;

    public EmptyPojo()  {
    }

    public String getName() {
        return name ;
    }

    public void setName(String name) {
        this.name = name ;
    }
}
