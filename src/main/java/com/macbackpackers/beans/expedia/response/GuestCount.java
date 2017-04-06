
package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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
