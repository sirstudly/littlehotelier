
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that marks the given card invalid on Booking.com.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob" )
public class BDCMarkCreditCardInvalidJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService service;

    @Override
    public void processJob() throws Exception {
        service.markCreditCardInvalidOnBDC( getReservationId() );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String id ) {
        setParameter( "reservation_id", id );
    }
}
