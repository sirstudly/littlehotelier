
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "GuestCount" )
@XmlAccessorType( XmlAccessType.FIELD )
public class GuestCount {

    @XmlAttribute( name = "adult" )
    private Integer adult;

    public Integer getAdult() {
        return adult;
    }

    public void setAdult( Integer adult ) {
        this.adult = adult;
    }
}
