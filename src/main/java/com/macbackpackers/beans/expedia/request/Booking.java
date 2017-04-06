
package com.macbackpackers.beans.expedia.request;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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
