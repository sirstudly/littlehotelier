
package com.macbackpackers.jobs;

import java.math.BigDecimal;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Job that charges an amount on a booking with their current card details.
 * At the moment, only HWL bookings are supported.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ManualChargeJob" )
public class ManualChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient lhWebClient;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient hwlWebClient;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processManualPayment( lhWebClient, hwlWebClient, getId(), getBookingRef(), getAmount(), getMessage() );
    }

    @Override
    public void finalizeJob() {
        lhWebClient.close(); // cleans up JS threads
        hwlWebClient.close(); // cleans up JS threads
    }

    /**
     * Returns the booking reference (e.g. HWL-123-XXXXXXXXX).
     * 
     * @return non-null reference
     */
    public String getBookingRef() {
        return getParameter( "booking_ref" );
    }

    /**
     * Sets the booking reference.
     * 
     * @param bookingRef e.g. BDC-123456789
     */
    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    /**
     * Sets the amount to be charged.
     * 
     * @param amount non-zero charge amount
     */
    public void setAmount( BigDecimal amount ) {
        setParameter( "amount", amount.toString() );
    }

    /**
     * Returns the amount to be charged.
     * 
     * @return the non-zero charge amount
     */
    public BigDecimal getAmount() {
        return new BigDecimal( getParameter( "amount" ) );
    }

    /**
     * Returns the message to add to the notes.
     * 
     * @return (optional) message
     */
    public String getMessage() {
        return getParameter( "message" );
    }

    /**
     * Sets the message to add to the notes.
     * 
     * @param message message to add
     */
    public void setMessage( String message ) {
        setParameter( "message", message );
    }

    @Override
    public int getRetryCount() {
        return 1; // only attempt to charge card once!
    }

}
