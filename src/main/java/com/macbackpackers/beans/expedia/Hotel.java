
package com.macbackpackers.beans.expedia;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Hotel" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Hotel {

    @XmlAttribute( name = "id" )
    private String id;

    public Hotel() {
        // default constructor
    }

    public Hotel( String id ) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

}
