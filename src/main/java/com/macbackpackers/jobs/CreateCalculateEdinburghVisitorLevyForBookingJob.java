
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
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Creates {@link CalculateEdinburghVisitorLevyForBookingJob}s for active bookings made within a
 * booking-date range where the stay exceeds five nights or the booking is from Hostelworld.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateCalculateEdinburghVisitorLevyForBookingJob" )
public class CreateCalculateEdinburghVisitorLevyForBookingJob extends AbstractJob {

    private static final String ACTIVE_STATUSES = "confirmed,not_confirmed";

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
        if ( false == dao.isCloudbeds() ) {
            return;
        }

        cbScraper.getReservationsByBookingDate( cbWebClient, getBookingDateStart(), getBookingDateEnd(), ACTIVE_STATUSES )
                .stream()
                .filter( this::requiresVisitorLevyAdjustment )
                .forEach( c -> {
                    LOGGER.info( "Creating CalculateEdinburghVisitorLevyForBookingJob for Res #{} ({}) {} {} ({} nights)",
                            c.getId(), c.getSourceName(), c.getFirstName(), c.getLastName(), c.getNights() );
                    CalculateEdinburghVisitorLevyForBookingJob job = new CalculateEdinburghVisitorLevyForBookingJob();
                    job.setStatus( JobStatus.submitted );
                    job.setReservationId( c.getId() );
                    dao.insertJob( job );
                } );
    }

    private boolean requiresVisitorLevyAdjustment( Customer customer ) {
        if ( isHostelworldBooking( customer ) ) {
            return true;
        }
        return getNumberOfNights( customer ) > 5;
    }

    private boolean isHostelworldBooking( Customer customer ) {
        return customer.getSourceName() != null && customer.getSourceName().startsWith( "Hostelworld" );
    }

    private int getNumberOfNights( Customer customer ) {
        return customer.getNights() == null ? 0 : Integer.parseInt( customer.getNights() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public LocalDate getBookingDateStart() {
        return LocalDate.parse( getParameter( "booking_date_start" ) );
    }

    public void setBookingDateStart( LocalDate bookingDateStart ) {
        setParameter( "booking_date_start", bookingDateStart.toString() );
    }

    public LocalDate getBookingDateEnd() {
        return LocalDate.parse( getParameter( "booking_date_end" ) );
    }

    public void setBookingDateEnd( LocalDate bookingDateEnd ) {
        setParameter( "booking_date_end", bookingDateEnd.toString() );
    }
}
