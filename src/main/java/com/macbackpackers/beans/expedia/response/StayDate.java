
package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "StayDate" )
@XmlAccessorType( XmlAccessType.FIELD )
public class StayDate {

    @XmlAttribute( name = "arrival" )
    private String arrival;

    @XmlAttribute( name = "departure" )
    private String departure;

    public String getArrival() {
        return arrival;
    }

    public void setArrival( String arrival ) {
        this.arrival = arrival;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture( String departure ) {
        this.departure = departure;
    }
}
