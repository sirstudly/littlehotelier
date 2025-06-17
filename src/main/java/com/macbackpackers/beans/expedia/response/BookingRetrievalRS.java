
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "BookingRetrievalRS" )
@XmlAccessorType( XmlAccessType.FIELD )
public class BookingRetrievalRS {

    @XmlElement( name = "Error" )
    private ErrorResponse error;

    @XmlElement( name = "Bookings" )
    private Bookings bookings;

    public ErrorResponse getError() {
        return error;
    }

    public void setError( ErrorResponse error ) {
        this.error = error;
    }

    public Bookings getBookings() {
        return bookings;
    }

    public void setBookings( Bookings bookings ) {
        this.bookings = bookings;
    }
}
