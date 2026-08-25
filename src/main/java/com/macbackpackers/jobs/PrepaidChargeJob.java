package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import java.math.BigDecimal;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Job that charges any remaining balance with the current card details.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.PrepaidChargeJob" )
public class PrepaidChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            paymentProcessor.processPrepaidBooking( webClient, getReservationId(), getAmount() );
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
     * Optional charge amount. When set, overrides the VCC balance / balance due in
     * {@link PaymentProcessorService#processPrepaidBooking} and skips the minimum charge check.
     *
     * @return amount to charge, or null to use the default amount
     */
    public BigDecimal getAmount() {
        String amount = getParameter( "amount" );
        return amount == null ? null : new BigDecimal( amount );
    }

    /**
     * Sets an optional charge amount override.
     *
     * @param amount amount to charge
     */
    public void setAmount( BigDecimal amount ) {
        setParameter( "amount", amount.toString() );
    }

    @Override
    public int getRetryCount() {
        return 1; // limit failed email attempts
    }
}
