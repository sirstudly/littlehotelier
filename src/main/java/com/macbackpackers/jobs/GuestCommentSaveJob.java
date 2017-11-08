
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
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Saves a guest comment given the booking ref, checkin date and reservation ID.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.GuestCommentSaveJob" )
public class GuestCommentSaveJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        // scrape any user comments from the reservation and save it
        String comment = bookingsScraper.getGuestCommentsForReservation( webClient, getBookingRef(), getCheckinDate() );
        dao.updateGuestCommentsForReservation( getReservationId(), comment );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    public int getReservationId() {
        return Integer.parseInt( getParameter( "reservation_id" ));
    }

    public void setReservationId( int reservationId ) {
        setParameter( "reservation_id", String.valueOf( reservationId ));
    }

    public String getBookingRef() {
        return getParameter( "booking_ref" );
    }

    public void setBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }
}
