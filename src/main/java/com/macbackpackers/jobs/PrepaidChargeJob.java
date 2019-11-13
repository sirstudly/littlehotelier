
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
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
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            paymentProcessor.processPrepaidBooking( webClient, String.valueOf( getReservationId() ) );
        }
    }

    /**
     * Returns the reservation id.
     * 
     * @return reservationId
     */
    public int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ) );
    }

    /**
     * Sets the reservation id.
     * 
     * @param reservationId
     */
    public void setReservationId( int reservationId ) {
        setParameter( "reservation_id", String.valueOf( reservationId ) );
    }

    @Override
    public int getRetryCount() {
        return 2; // don't attempt too many times
    }

}
