
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that sends a copy of the guest checkout email to the target recipient
 * for testing.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateTestGuestCheckoutEmailJob" )
public class CreateTestGuestCheckoutEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Override
    public void resetJob() throws Exception {
        // because email address has a unique constraint, delete any existing entries first
        dao.deleteSendEmailEntry( getRecipientEmail() );
    }

    @Override
    public void finalizeJob() {
        bookingsScraper.closeAllWindows(); // cleans up JS threads
    }

    @Override
    public int getRetryCount() {
        return 1; // don't try to send more than 1 email...
    }

    @Override
    public void processJob() throws Exception {
        LOGGER.info( "Creating a test entry for sending an email" );
        SendEmailEntry emailJob = new SendEmailEntry();
        emailJob.setEmail( getRecipientEmail() );
        emailJob.setFirstName( getFirstName() );
        emailJob.setLastName( getLastName() );
        emailJob.setSendSubject( dao.getGuestCheckoutEmailSubject() );
        emailJob.setSendBody( dao.getGuestCheckoutEmailTemplate() );
        emailJob.replaceAllPlaceholders(); // if subject/body has placeholders
        dao.saveSendEmailEntry( emailJob );
    }
    
    public String getRecipientEmail() {
        return getParameter( "email_address" );
    }

    public void setRecipientEmail( String email ) {
        setParameter( "email_address", email );
    }

    public String getFirstName() {
        return getParameter( "first_name" );
    }

    public void setFirstName( String firstName ) {
        setParameter( "first_name", firstName );
    }

    public String getLastName() {
        return getParameter( "last_name" );
    }

    public void setLastName( String lastName ) {
        setParameter( "last_name", lastName );
    }
}
