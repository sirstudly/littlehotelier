
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that cancels a booking in confirmed status leaving a note in the booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CancelBookingJob" )
public class CancelBookingJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        cloudbedsService.cancelReservation( getReservationId(), getNote() );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getNote() {
        return getParameter( "note" );
    }

    public void setNote( String note ) {
        setParameter( "note", note );
    }
}
