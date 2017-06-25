
package com.macbackpackers.jobs;

import java.math.BigDecimal;
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
 * Job that charges a no-show amount on a booking with their current card details.
 * At the moment, this only HWL bookings are supported.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.NoShowChargeJob" )
public class NoShowChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient lhWebClient;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient hwlWebClient;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processNoShowPayment( lhWebClient, hwlWebClient, getBookingRef(), getCheckinDate(), getAmount() );
    }

    @Override
    public void finalizeJob() {
        lhWebClient.close(); // cleans up JS threads
        hwlWebClient.close(); // cleans up JS threads
    }

    /**
     * Returns the booking reference (e.g. BDC-XXXXXXXXX).
     * 
     * @return non-null reference
     */
    public String getBookingRef() {
        return getParameter( "booking_ref" );
    }

    /**
     * Sets the booking reference.
     * 
     * @param bookingRef e.g. BDC-123456789
     */
    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    /**
     * Gets the date which this reservation was supposed to checkin.
     * 
     * @return non-null checkin date
     * @throws ParseException on parse error
     */
    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    /**
     * Sets the date which this reservation was supposed to checkin.
     * 
     * @param checkinDate date to set
     */
    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }
    
    /**
     * Sets the amount to be charged.
     * 
     * @param amount non-zero charge amount
     */
    public void setAmount( BigDecimal amount ) {
        setParameter( "amount", amount.toString() );
    }

    /**
     * Returns the amount to be charged.
     * 
     * @return the non-zero charge amount
     */
    public BigDecimal getAmount() {
        return new BigDecimal( getParameter( "amount" ) );
    }

    @Override
    public int getRetryCount() {
        return 1; // only attempt to charge card once!
    }

}
