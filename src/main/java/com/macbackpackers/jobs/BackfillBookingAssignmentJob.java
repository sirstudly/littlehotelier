package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.BookingAssignmentEnrichService;

/**
 * One-off: backfills historical {@code wp_lh_booking_assignment} rows for reservations checking in
 * within [start_date, end_date] that have already checked out and aren't in the table yet.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BackfillBookingAssignmentJob" )
public class BackfillBookingAssignmentJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Autowired
    @Transient
    private CloudbedsScraper scraper;

    @Autowired
    @Transient
    private BookingAssignmentEnrichService enrichService;

    @Override
    public void processJob() throws Exception {
        LocalDate today = LocalDate.now();
        Set<Long> existing = dao.fetchBookingAssignmentReservationIds();
        List<Customer> listed = scraper.getReservations( webClient, null, null,
                getStartDate(), getEndDate(), null, null, null, null, null );

        List<BookingAssignment> rows = new ArrayList<>();
        int skipped = 0;
        int fetched = 0;
        for ( Customer c : listed ) {
            if ( false == shouldBackfill( c, existing, today ) ) {
                skipped++;
                continue;
            }
            Reservation r = scraper.getReservationRetry( webClient, c.getId() );
            fetched++;
            List<BookingAssignment> reservationRows = enrichService.buildBackfillAssignments( r );
            // list and REST can disagree if the stay was just extended; heal owns anything not yet departed
            if ( reservationRows.stream().anyMatch( a -> false == a.getCheckoutLocalDate().isBefore( today ) ) ) {
                skipped++;
                continue;
            }
            rows.addAll( reservationRows );
        }
        int inserted = dao.insertBookingAssignments( rows );
        LOGGER.info( "Backfill {} - {}: listed={}, skipped={}, fetched={}, rows={}, inserted={}",
                getStartDate(), getEndDate(), listed.size(), skipped, fetched, rows.size(), inserted );
    }

    /**
     * True when the reservation has checked out before {@code today} and isn't already in the table.
     */
    static boolean shouldBackfill( Customer c, Set<Long> existingReservationIds, LocalDate today ) {
        Long id;
        try {
            id = Long.parseLong( c.getId() );
        }
        catch ( NumberFormatException e ) {
            return false;
        }
        if ( existingReservationIds.contains( id ) ) {
            return false;
        }
        LocalDate checkout = parseDate( c.getCheckoutDate() );
        return checkout != null && checkout.isBefore( today );
    }

    private static LocalDate parseDate( String value ) {
        if ( StringUtils.isBlank( value ) || value.trim().length() < 10 ) {
            return null;
        }
        try {
            return LocalDate.parse( value.trim().substring( 0, 10 ) );
        }
        catch ( DateTimeParseException e ) {
            return null;
        }
    }

    @Override
    public void finalizeJob() {
        webClient.close();
    }

    public LocalDate getStartDate() {
        return LocalDate.parse( getParameter( "start_date" ) );
    }

    public void setStartDate( LocalDate startDate ) {
        setParameter( "start_date", startDate.toString() );
    }

    public LocalDate getEndDate() {
        return LocalDate.parse( getParameter( "end_date" ) );
    }

    public void setEndDate( LocalDate endDate ) {
        setParameter( "end_date", endDate.toString() );
    }
}
