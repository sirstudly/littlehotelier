
package com.macbackpackers.jobs;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.GmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * Job that sends an email.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendGmailJob" )
public class SendGmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsScraper scraper;

    @Autowired
    @Transient
    private GmailService gmailService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            Reservation res = scraper.getReservationRetry( webClient, getReservationId() );
            final String note = getSubject() + " email sent.";
            if( res.containsNote( note ) ) {
                LOGGER.info( "Email already sent. Nothing to do." );
            }
            else {
                gmailService.sendEmail( getToAddress(), null, getSubject(), getEmailBody() );
                scraper.addNote( webClient, getReservationId(), note );
            }
        }
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getToAddress() {
        return getParameter( "to_address" );
    }

    public void setToAddress( String toAddress ) {
        setParameter( "to_address", toAddress );
    }

    public String getSubject() {
        return getParameter( "subject" );
    }

    public void setSubject( String subject ) {
        setParameter( "subject", subject );
    }

    public String getEmailBody() {
        return getParameter( "email_body" );
    }

    public void setEmailBody( String emailBody ) {
        setParameter( "email_body", emailBody );
    }
}
