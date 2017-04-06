
package com.macbackpackers.beans.expedia.request;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

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
