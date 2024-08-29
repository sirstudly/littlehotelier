
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Copies over card details for the given booking into Cloudbeds.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CopyCardDetailsToCloudbedsJob" )
public class CopyCardDetailsToCloudbedsJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.copyCardDetailsToCloudbeds( cbWebClient, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

    /**
     * Returns the unique CB reservation ID.
     * 
     * @return non-null reference
     */
    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    /**
     * Sets the unique CB reservation ID.
     * 
     * @param reservationId the reservation id (from the URL)
     */
    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

}
