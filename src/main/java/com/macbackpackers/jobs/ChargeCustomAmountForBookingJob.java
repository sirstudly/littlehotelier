
package com.macbackpackers.jobs;

import com.macbackpackers.services.PaymentProcessorService;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;

/**
 * Charges a custom amount for the given booking.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ChargeCustomAmountForBookingJob" )
public class ChargeCustomAmountForBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processManualChargeForBooking( cbWebClient, getReservationId(), getAmount() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

    /**
     * Returns the reservation id for this booking.
     *
     * @return non-null reference
     */
    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public BigDecimal getAmount() {
        return new BigDecimal( getParameter( "amount" ) );
    }

    /**
     * Sets the reservation id for this booking.
     *
     * @param reservationId the reservation id
     */
    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public void setAmount( BigDecimal amount ) {
        setParameter( "amount", amount.toString() );
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

}
