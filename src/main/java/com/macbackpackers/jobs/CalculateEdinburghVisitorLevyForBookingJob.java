
package com.macbackpackers.jobs;

import com.macbackpackers.services.EdinburghVisitorLevyService;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Calculates and corrects the Edinburgh Visitor Levy for a single Cloudbeds reservation.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob" )
public class CalculateEdinburghVisitorLevyForBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    @Override
    public void processJob() throws Exception {
        edinburghVisitorLevyService.processVisitorLevyForBooking( cbWebClient, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }
}
