
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

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
            paymentProcessor.processPrepaidBooking( webClient, getReservationId() );
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

    @Override
    public int getRetryCount() {
        return 1; // limit failed email attempts
    }
}
