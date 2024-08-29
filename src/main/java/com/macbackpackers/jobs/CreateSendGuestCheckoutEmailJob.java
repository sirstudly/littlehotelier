
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that creates individual jobs for sending out emails to guests who have checked-out.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendGuestCheckoutEmailJob" )
public class CreateSendGuestCheckoutEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        
        String emailSubject = dao.getGuestCheckoutEmailSubject();
        String emailTemplate = dao.getGuestCheckoutEmailTemplate();
        
        // retrieve all checkouts for the day
        for( CSVRecord record : bookingsScraper.getAllCheckouts( webClient, "HWL", getCheckoutDate() ) ) {
            LOGGER.info( "Booking ref: " + record.get( "Booking reference" ) );
            
            String email = record.get( "Guest email" );
            if( StringUtils.isNotBlank( email ) && false == dao.doesSendEmailEntryExist( email )) {
                LOGGER.info( "Creating an entry for sending an email for booking " + record.get( "Booking reference" ) );
                SendEmailEntry emailJob = new SendEmailEntry();
                emailJob.setEmail( email );
                emailJob.setFirstName( record.get( "Guest first name" ) );
                emailJob.setLastName( record.get( "Guest last name" ) );
                emailJob.setSendSubject( emailSubject );
                emailJob.setSendBody( emailTemplate );
                emailJob.replaceAllPlaceholders(); // if subject/body has placeholders
                dao.saveSendEmailEntry( emailJob );
            }
        }
    }
    
    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    public Date getCheckoutDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkout_date" ) );
    }

    public void setCheckoutDate( Date checkoutDate ) {
        setParameter( "checkout_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkoutDate ) );
    }
}
