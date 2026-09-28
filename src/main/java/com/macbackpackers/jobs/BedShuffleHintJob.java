
package com.macbackpackers.jobs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.shuffle.BedShuffleService;
import com.macbackpackers.services.shuffle.FallbackLevel;
import com.macbackpackers.services.shuffle.ShuffleSuggestion;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Runs the bed shuffle solver for one reservation on the split room report and stores the result as a hint on its
 * report rows. Already-assigned beds split across rooms are consolidated where possible.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BedShuffleHintJob" )
public class BedShuffleHintJob extends AbstractJob {

    private static final DateTimeFormatter SUGGESTED_AT = DateTimeFormatter.ofPattern( "dd-MMM HH:mm", Locale.ENGLISH );

    @Autowired
    @Transient
    private BedShuffleService bedShuffleService;

    @Override
    public void processJob() throws Exception {
        int reportJobId = getAllocationScraperJobId();
        long reservationId = getReservationId();
        if ( false == dao.isLatestSplitRoomReport( reportJobId ) ) {
            LOGGER.info( "Split room report {} has been superseded; skipping hint for {}", reportJobId, reservationId );
            return;
        }
        ShuffleSuggestion suggestion = bedShuffleService.suggestForReservation( reservationId,
                new BedShuffleService.Options().consolidate( true ) );
        int rows = dao.updateSplitRoomShuffleHint( reportJobId, reservationId, suggestion.getStatus().name(),
                hintText( suggestion, LocalDateTime.now() ) );
        LOGGER.info( "Reservation {}: {} (updated {} report rows)", reservationId, suggestion.getStatus(), rows );
    }

    /** The suggestion without its "Reservation X: STATUS" header; null when there's nothing to do. */
    static String hintText( ShuffleSuggestion suggestion, LocalDateTime now ) {
        if ( suggestion.getStatus() == ShuffleSuggestion.Status.ALREADY_ASSIGNED ) {
            return null;
        }
        String text = suggestion.describe();
        int newline = text.indexOf( '\n' );
        String body = newline < 0 ? "" : text.substring( newline + 1 ).strip();
        FallbackLevel level = suggestion.getLevel();
        if ( suggestion.getStatus() == ShuffleSuggestion.Status.FOUND && level != null && level != FallbackLevel.SAME_TYPE ) {
            body = "With " + level.description() + ":\n" + body;
        }
        return ( body.isEmpty() ? "" : body + "\n" )
                + "(Suggested " + SUGGESTED_AT.format( now ) + "; independent of other rows)";
    }

    @Override
    public int getRetryCount() {
        return 1; // a solver failure won't fix itself on retry
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

    public long getReservationId() {
        return Long.parseLong( getParameter( "reservation_id" ) );
    }

    public void setReservationId( long reservationId ) {
        setParameter( "reservation_id", String.valueOf( reservationId ) );
    }
}
