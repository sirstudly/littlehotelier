
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Job that checks whether a BDC reservation has paid their deposit and if not,
 * charge the deposit with their current card details.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCDepositChargeJob" )
public class BDCDepositChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
    @Override
    public void processJob() throws Exception {
        paymentProcessor.processDepositPayment( getBookingRef(), getBookingDate() );
    }

    /**
     * Returns the booking.com reference (BDC-XXXXXXXXX).
     * 
     * @return non-null reference
     */
    public String getBookingRef() {
        return getParameter( "booking_ref" );
    }

    /**
     * Sets the booking.com reference.
     * 
     * @param bookingRef e.g. BDC-123456789
     */
    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    /**
     * Gets the date which this reservation was booked.
     * 
     * @return non-null booking date
     * @throws ParseException on parse error
     */
    public Date getBookingDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "booking_date" ) );
    }

    /**
     * Sets the date which this reservation was booked.
     * 
     * @param bookingDate date to set
     */
    public void setBookingDate( Date bookingDate ) {
        setParameter( "booking_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( bookingDate ) );
    }
    
    @Override
    public int getRetryCount() {
        return 1; // only attempt to charge card once!
    }

}
