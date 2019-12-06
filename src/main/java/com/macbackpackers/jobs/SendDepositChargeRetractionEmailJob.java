
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends email to guest saying we done fucked up.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendDepositChargeRetractionEmailJob" )
public class SendDepositChargeRetractionEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.sendDepositChargeRetractionGmail( webClient, getReservationId() );
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
        return 2; // limit failed email attempts
    }
}
