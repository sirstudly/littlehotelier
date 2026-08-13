package com.macbackpackers.scrapers.cloudbedsws;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.ArchiveAllTransactionNotesJob;

/**
 * Reacts to incremental calendar WebSocket updates by enqueueing
 * {@link ArchiveAllTransactionNotesJob}s when a booking's {@code balance_due} drops (or becomes
 * paid). The job re-fetches the reservation and archives pending transaction notes only when
 * balance due excluding EVL is zero or less.
 * <p>
 * {@link #onSnapshot} seeds an in-memory balance cache only; jobs are not created on reconnect.
 */
@Component
public class ArchiveTransactionNotesBookingEventListener implements CloudbedsEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger( ArchiveTransactionNotesBookingEventListener.class );

    @Autowired
    private WordPressDAO dao;

    /** booking_id → last seen balance_due from calendar events. */
    private final Map<String, BigDecimal> balanceDueByBookingId = new ConcurrentHashMap<>();

    @Override
    public void onSnapshot( String propertyId, List<CloudbedsCalendarEvent> events ) {
        if ( events == null ) {
            return;
        }
        for ( CloudbedsCalendarEvent event : events ) {
            seedBalance( event );
        }
    }

    @Override
    public void onUpdate( String propertyId, CloudbedsCalendarUpdate update ) {
        if ( update == null ) {
            return;
        }
        processReservationEvents( update.getAllReservationEvents() );
    }

    private void processReservationEvents( List<CloudbedsCalendarEvent> events ) {
        if ( events == null || events.isEmpty() ) {
            return;
        }
        Set<String> reservationIdsSeenInBatch = new HashSet<>();
        for ( CloudbedsCalendarEvent event : events ) {
            if ( false == isGuestReservationEvent( event ) ) {
                continue;
            }
            BigDecimal newBalance = event.getBalanceDueAmount();
            if ( newBalance == null ) {
                continue;
            }
            String reservationId = event.getBookingId().trim();
            BigDecimal previousBalance = balanceDueByBookingId.get( reservationId );
            boolean balanceDropped = previousBalance != null && newBalance.compareTo( previousBalance ) < 0;
            boolean shouldEnqueue = balanceDropped || event.isPaid();

            // Always refresh cache after comparison (including within-batch duplicates).
            balanceDueByBookingId.put( reservationId, newBalance );

            if ( false == shouldEnqueue ) {
                continue;
            }
            if ( false == reservationIdsSeenInBatch.add( reservationId ) ) {
                continue;
            }
            if ( dao.hasArchiveAllTransactionNotesJobForReservation( reservationId ) ) {
                LOGGER.info( "Skipping ArchiveAllTransactionNotesJob for reservation {} (job already pending)",
                        reservationId );
                continue;
            }
            enqueueArchiveJob( event, reservationId, previousBalance, newBalance );
        }
    }

    private void enqueueArchiveJob( CloudbedsCalendarEvent event, String reservationId,
            BigDecimal previousBalance, BigDecimal newBalance ) {
        LOGGER.info( "Creating ArchiveAllTransactionNotesJob for booking {} ({}): {} {} balance {} -> {}",
                event.getThirdPartyIdentifier(), event.getStatus(),
                event.getFirstName(), event.getLastName(),
                previousBalance, newBalance );
        ArchiveAllTransactionNotesJob job = new ArchiveAllTransactionNotesJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( reservationId );
        dao.insertJob( job );
    }

    private void seedBalance( CloudbedsCalendarEvent event ) {
        if ( false == isGuestReservationEvent( event ) ) {
            return;
        }
        BigDecimal balance = event.getBalanceDueAmount();
        if ( balance == null ) {
            return;
        }
        balanceDueByBookingId.put( event.getBookingId().trim(), balance );
    }

    /**
     * Guest stay rows (not blocked dates / OOS). Includes checked-in/out because payments can post
     * after check-in.
     */
    static boolean isGuestReservationEvent( CloudbedsCalendarEvent event ) {
        if ( event == null || StringUtils.isBlank( event.getBookingId() )
                || "0".equals( event.getBookingId().trim() ) ) {
            return false;
        }
        String type = event.getType();
        return "booked".equalsIgnoreCase( type )
                || "checked_in".equalsIgnoreCase( type )
                || "checked_out".equalsIgnoreCase( type );
    }

    /** Visible for tests. */
    BigDecimal getCachedBalanceDue( String reservationId ) {
        return balanceDueByBookingId.get( reservationId );
    }
}
