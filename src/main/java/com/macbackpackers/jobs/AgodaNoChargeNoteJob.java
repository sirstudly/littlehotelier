
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
import com.macbackpackers.scrapers.ReservationPageScraper;

/**
 * Job that puts a note in the comments/note section of the agoda booking to NOT CHARGE GUEST!
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AgodaNoChargeNoteJob" )
public class AgodaNoChargeNoteJob extends AbstractJob {

    @Autowired
    @Transient
    private ReservationPageScraper reservationScraper;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        reservationScraper.updateGuestCommentsAndNotesForAgoda(
                webClient, getBookingRef(), getBookedDate() );
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

    public Date getBookedDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "booked_date" ) );
    }

    public void setBookedDate( Date bookedDate ) {
        setParameter( "booked_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( bookedDate ) );
    }
}
