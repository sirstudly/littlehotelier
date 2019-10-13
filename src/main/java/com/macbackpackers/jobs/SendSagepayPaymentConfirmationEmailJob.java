
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends email to guest after a successful payment was done for a booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendSagepayPaymentConfirmationEmailJob" )
public class SendSagepayPaymentConfirmationEmailJob extends AbstractJob {

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
                cloudbedsService.sendSagepayPaymentConfirmationEmail( webClient, getReservationId(), getSagepayTxnId() );
            }
            else {
                cloudbedsService.sendSagepayPaymentConfirmationGmail( webClient, getReservationId(), getSagepayTxnId() );
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
     * Returns the sagepay txn id.
     * 
     * @return sagepayTxnId
     */
    public int getSagepayTxnId() {
        return Integer.parseInt( getParameter( "sagepay_txn_id" ) );
    }

    /**
     * Sets the sagepay txn id.
     * 
     * @param txnId the sagepay txn id
     */
    public void setSagepayTxnId( int txnId ) {
        setParameter( "sagepay_txn_id", String.valueOf( txnId ) );
    }

    @Override
    public int getRetryCount() {
        return 2; // limit failed email attempts
    }
}
