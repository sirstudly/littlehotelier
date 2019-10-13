
package com.macbackpackers.jobs;

import java.math.BigDecimal;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends email to guest after non-refundable booking charge was declined.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendNonRefundableDeclinedEmailJob" )
public class SendNonRefundableDeclinedEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            if ( dao.isCloudbedsEmailEnabled() ) {
                cloudbedsService.sendNonRefundableDeclinedEmail( webClient, getReservationId(), getAmount(), getPaymentURL() );
            }
            else {
                cloudbedsService.sendNonRefundableDeclinedGmail( webClient, getReservationId(), getAmount(), getPaymentURL() );
            }
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
