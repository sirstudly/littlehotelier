
package com.macbackpackers.beans.expedia.request;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "ParamSet" )
@XmlAccessorType( XmlAccessType.FIELD )
public class ParamSet {

    @XmlElement( name = "Booking" )
    private Booking booking;

    public Booking getBooking() {
        return booking;
    }

    public void setBooking( Booking booking ) {
        this.booking = booking;
    }

}
