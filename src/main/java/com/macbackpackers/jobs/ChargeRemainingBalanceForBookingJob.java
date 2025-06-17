
package com.macbackpackers.jobs;

import com.macbackpackers.services.PaymentProcessorService;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Charges the amount remaining using the card on-file for the given booking.
 * If successful, an email will be sent to the email address on file.
 * If unsuccessful, an email will be sent with a payment link for the amount due.
 */
@Entity
@DiscriminatorValue(value = "com.macbackpackers.jobs.ChargeRemainingBalanceForBookingJob")
public class ChargeRemainingBalanceForBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier("webClientForCloudbeds")
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.chargeRemainingBalanceForBooking( cbWebClient, getReservationId() );
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
        return 1;
    }

}
