
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends email from a template.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendTemplatedEmailJob" )
public class SendTemplatedEmailJob extends AbstractJob {

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
                cloudbedsService.sendTemplatedEmail( webClient, getReservationId(), getEmailTemplate() );
            }
            else {
                cloudbedsService.sendTemplatedGmail( webClient, getReservationId(), getEmailTemplate() );
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

    public String getEmailTemplate() {
        return getParameter( "email_template" );
    }

    public void setEmailTemplate( String template ) {
        setParameter( "email_template", template );
    }

    @Override
    public int getRetryCount() {
        return 1; // limit failed email attempts
    }
}
