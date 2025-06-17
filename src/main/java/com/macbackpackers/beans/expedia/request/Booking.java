
package com.macbackpackers.beans.expedia.request;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Booking" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Booking {

    @XmlAttribute( name = "id" )
    private String id;

    public Booking() {
        // default constructor
    }

    public Booking( String id ) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

}
