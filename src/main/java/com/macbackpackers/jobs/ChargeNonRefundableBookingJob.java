
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.exceptions.PaymentNotAuthorizedException;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Clicks AUTHORIZE/CAPTURE on credit cards tab in Cloudbeds for the given booking if the Rate Plan
 * is "Non-refundable".
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ChargeNonRefundableBookingJob" )
public class ChargeNonRefundableBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Transient
    private int numRetriesOverride = 2; // forced lower default to avoid spamming card txns

    @Override
    public void processJob() throws Exception {
        try {
            paymentProcessor.chargeNonRefundableBooking( cbWebClient, getReservationId() );
        }
        catch ( PaymentNotAuthorizedException ex ) {
            LOGGER.info( "Payment not authorized. Lowering retry count on job to 1 to avoid spamming card" );
            numRetriesOverride = 1;
            throw ex;
        }
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

    @Override
    public int getRetryCount() {
        return numRetriesOverride;
    }

}
