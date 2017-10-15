package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.services.PaymentProcessorService;


/**
 * Job that checks whether a reservation has paid their deposit and if not,
 * charge the deposit with their current card details.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AgodaChargeJob" )
public class AgodaChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
//    @Autowired
//    @Transient
//    private AgodaScraper scraper;
//
    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
//        paymentProcessor.processDepositPayment( webClient, getId(), getBookingRef(), getBookingDate() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    /**
     * Returns the booking reference (e.g. AGO-XXXXXXXXX).
     * 
     * @return non-null reference
     */
    public String getBookingRef() {
        return getParameter( "booking_ref" );
    }

    /**
     * Sets the booking reference.
     * 
     * @param bookingRef e.g. AGO-123456789
     */
    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    /**
     * Gets the checkin date to scrape the reservations.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    /**
     * Sets the checkin date to scrape reservations.
     * 
     * @param checkinDate non-null date
     */
    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }

    @Override
    public int getRetryCount() {
        return 2; // don't attempt too many times
    }
}
