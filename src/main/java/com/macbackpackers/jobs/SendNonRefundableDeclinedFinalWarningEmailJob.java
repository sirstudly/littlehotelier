
package com.macbackpackers.jobs;

import java.math.BigDecimal;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends a final-warning email after a second non-refundable booking charge decline.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendNonRefundableDeclinedFinalWarningEmailJob" )
public class SendNonRefundableDeclinedFinalWarningEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.sendNonRefundableDeclinedFinalWarningGmail( webClient, getReservationId(), getAmount(), getPaymentURL() );
        }
    }

    /**
     * Returns the reservation id.
     *
     * @return reservationId
     */
    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    /**
     * Sets the reservation id.
     *
     * @param reservationId
     */
    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    /**
     * Sets the amount that was charged.
     *
     * @param amount non-zero charge amount
     */
    public void setAmount( BigDecimal amount ) {
        setParameter( "amount", amount.toString() );
    }

    /**
     * Returns the amount charged.
     *
     * @return the non-zero charge amount
     */
    public BigDecimal getAmount() {
        return new BigDecimal( getParameter( "amount" ) );
    }

    /**
     * Sets the payment URL.
     *
     * @param paymentURL
     */
    public void setPaymentURL( String paymentURL ) {
        setParameter( "payment_url", paymentURL );
    }

    /**
     * Returns the payment URL.
     *
     * @return payment URL
     */
    public String getPaymentURL() {
        return getParameter( "payment_url" );
    }

    @Override
    public int getRetryCount() {
        return 2; // limit failed email attempts
    }
}
