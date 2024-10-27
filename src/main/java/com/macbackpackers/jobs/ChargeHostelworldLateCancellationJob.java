
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import com.macbackpackers.exceptions.PaymentPendingException;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Charges the card on-file for the given Hostelworld booking for the amount of the first night if:
 * <ul>
 * <li>it hasn't been charged yet</li>
 * <li>cancellation was "late" (done outside of the cancellation window)</li>
 * <li>cancellation was done by System (so via the HWL portal)</li>
 * </ul>
 * If successful, an email will be sent to the email address on file.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ChargeHostelworldLateCancellationJob" )
public class ChargeHostelworldLateCancellationJob extends AbstractJob {

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
            paymentProcessor.processHostelworldLateCancellationCharge( cbWebClient, getReservationId() );
        }
        catch ( PaymentPendingException ex ) {
            LOGGER.info( ex.getMessage() );
            numRetriesOverride = 1;
        }
        catch ( RecordPaymentFailedException ex ) {
            LOGGER.info( "Payment not authorized. Lowering retry count on job to 1 to avoid spamming card" );
            numRetriesOverride = 1;
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
