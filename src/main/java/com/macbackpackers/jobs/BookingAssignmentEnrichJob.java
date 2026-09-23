package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.BookingAssignmentEnrichService;

/**
 * REST-enriches one reservation into {@code wp_lh_booking_assignment} (EVL total, special requests, …).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BookingAssignmentEnrichJob" )
public class BookingAssignmentEnrichJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Autowired
    @Transient
    private BookingAssignmentEnrichService enrichService;

    @Override
    public void processJob() throws Exception {
        enrichService.enrichReservation( webClient, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close();
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }
}
