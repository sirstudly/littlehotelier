
package com.macbackpackers.beans.expedia.response;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Bookings" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Bookings {

    @XmlElement( name = "Booking" )
    private List<Booking> bookingList;

    public List<Booking> getBookingList() {
        return bookingList;
    }

    public void setBookingList( List<Booking> bookingList ) {
        this.bookingList = bookingList;
    }
}
