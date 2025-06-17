
package com.macbackpackers.beans.expedia.response;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

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
