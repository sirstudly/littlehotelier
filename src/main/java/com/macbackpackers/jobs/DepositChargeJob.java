
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Job that checks whether a reservation has paid their deposit and if not,
 * charge the deposit with their current card details.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DepositChargeJob" )
public class DepositChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;
    
    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {

        if ( dao.isCloudbeds() ) {
            try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
                paymentProcessor.processCardPaymentForRemainingBalance( webClient, String.valueOf( getReservationId() ) );
            }
        }
        else { // LittleHotelier
            try (WebClient webClient = appContext.getBean( "webClient", WebClient.class )) {
                paymentProcessor.processDepositPayment( webClient, getId(), getBookingRef(), getBookingDate() );
            }
        }
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
     * Returns the reservation id.
     * 
     * @return reservationId
     */
    public int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ) );
    }

    /**
     * Sets the reservation id.
     * 
     * @param reservationId
     */
    public void setReservationId( int reservationId ) {
        setParameter( "reservationId", String.valueOf( reservationId ) );
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
        return 2; // don't attempt too many times
    }

}
