
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends the group booking approval required email.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendGroupBookingApprovalRequiredEmailJob" )
public class SendGroupBookingApprovalRequiredEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            if ( isPrepaid() ) {
                cloudbedsService.sendGroupBookingApprovalRequiredPrepaidGmail( webClient, getReservationId() );
            }
            else {
                cloudbedsService.sendGroupBookingApprovalRequiredGmail( webClient, getReservationId() );
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

    public boolean isPrepaid() {
        return String.valueOf( true ).equals( getParameter( "is_prepaid" ) );
    }

    public void setPrepaid( boolean isPrepaid ) {
        setParameter( "is_prepaid", String.valueOf( isPrepaid ) );
    }

    @Override
    public int getRetryCount() {
        return 2; // limit failed email attempts
    }
}
