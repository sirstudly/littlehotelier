
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.ReservationPageScraper;

/**
 * Job that goes to the individual bookings page and confirms any outstanding deposit present and
 * saves the booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ConfirmDepositAmountsJob" )
public class ConfirmDepositAmountsJob extends AbstractJob {

    @Autowired
    @Transient
    private ReservationPageScraper reservationScraper;

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        String bookingRef = getBookingRef();
        HtmlPage bookingsPage = bookingsScraper.goToBookingPageArrivedOn( webClient, getCheckinDate(), bookingRef );
        reservationScraper.tickDeposit( bookingsPage, getReservationId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    public String getBookingRef() {
        return getParameter( "booking_reference" );
    }

    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_reference", bookingRef );
    }

    /**
     * Gets the checkin date to scrape the reservation.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    /**
     * Sets the checkin date to scrape reservation.
     * 
     * @param checkinDate non-null date
     */
    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }

    public int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ) );
    }

    public void setReservationId( int reservationId ) {
        setParameter( "reservation_id", String.valueOf( reservationId ) );
    }

}
