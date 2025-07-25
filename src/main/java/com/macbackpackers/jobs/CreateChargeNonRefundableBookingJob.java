
package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateChargeNonRefundableBookingJob" )
public class CreateChargeNonRefundableBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cbScraper;

    @Override
    @Transactional
    public void processJob() throws Exception {
        cbScraper.getReservationsForBookingSources( cbWebClient, null, null,
                getBookingDate(), getBookingDate().plusDays( getDaysAhead() ),
                "Booking.com (Hotel Collect Booking)", "Hostelworld & Hostelbookers", "Hostelworld (Hotel Collect Booking)" )
                .stream()
                .filter( p -> false == p.isPaid() )
                .filter( p -> p.isHotelCollectBooking() )
                .filter( p -> p.isNonRefundable() )
                // should we charge cancelled bookings?
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .forEach( p -> {
                    LOGGER.info( "Creating a ChargeNonRefundableBookingJob for booking "
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + "): "
                            + p.getFirstName() + " " + p.getLastName() );
                    ChargeNonRefundableBookingJob chargeJob = new ChargeNonRefundableBookingJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( p.getReservationId() );
                    dao.insertJob( chargeJob );
                } );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

    /**
     * Gets the (start) booking date.
     * 
     * @return non-null date parameter
     */
    public LocalDate getBookingDate() {
        return LocalDate.parse( getParameter( "booking_date" ) );
    }

    /**
     * Sets the (start) booking date.
     * 
     * @param bookingDate non-null date
     */
    public void setBookingDate( LocalDate bookingDate ) {
        setParameter( "booking_date", bookingDate.toString() );
    }

    /**
     * Returns the number of days ahead of the booking date to include.
     * 
     * @return non-negative number of days
     */
    public int getDaysAhead() {
        String daysAhead = getParameter( "days_ahead" );
        return daysAhead == null ? 0 : Integer.parseInt( daysAhead );
    }

    /**
     * Sets the number of days ahead of the booking date to include.
     * 
     * @param daysAhead number of days
     */
    public void setDaysAhead( int daysAhead ) {
        if ( daysAhead < 0 ) {
            throw new IllegalArgumentException( "Days ahead must be non-negative" );
        }
        setParameter( "days_ahead", String.valueOf( daysAhead ) );
    }

}
