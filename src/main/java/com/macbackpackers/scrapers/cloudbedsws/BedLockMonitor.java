package com.macbackpackers.scrapers.cloudbedsws;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.BedLock;
import com.macbackpackers.beans.BedLockViolation;
import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.GmailService;

/**
 * Flags locked reservations ({@code wp_lh_bed_lock}, set from the calendar userscript) that the
 * calendar WebSocket moves off their bed. Called by {@link BookingAssignmentCloudbedsEventListener}
 * with the assignment version before and after each placement change.
 * <p>
 * Only placement changes on the same {@code assignment_key} are seen, so this relies on the event
 * carrying {@code booking_rooms_id}. Moves made while the WebSocket was disconnected are picked up by
 * {@link #reconcileSnapshot(List)} on the next {@code on_migrate}.
 */
@Component
public class BedLockMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger( BedLockMonitor.class );

    /** DB option to stop emailing the property inbox on violations (any value other than "false" leaves it on). */
    static final String OPTION_EMAIL_ALERTS = "hbo_bed_lock_email_alerts";

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private GmailService gmail;

    /**
     * Reservations with an active lock; empty if none or the bed lock tables don't exist on this
     * property yet.
     */
    public Set<Long> fetchLockedReservationIds() {
        try {
            return dao.fetchActiveBedLockReservationIds();
        }
        catch ( RuntimeException e ) {
            LOGGER.debug( "Unable to fetch bed locks: {}", e.getMessage() );
            return Collections.emptySet();
        }
    }

    /**
     * Records (or updates / resolves) a violation for each active lock on the reservation that this
     * placement change affects.
     *
     * @param previous current version before the change
     * @param next version that replaced it
     */
    public void onPlacementChange( BookingAssignment previous, BookingAssignment next ) {
        if ( previous == null || next == null || next.getReservationId() == null
                || Objects.equals( previous.getRoomId(), next.getRoomId() ) ) {
            return;
        }
        try {
            for ( BedLock lock : dao.fetchActiveBedLocksForReservation( next.getReservationId() ) ) {
                checkLock( lock, previous, next );
            }
        }
        catch ( RuntimeException e ) {
            LOGGER.error( "Bed lock check failed for reservation {}", next.getReservationId(), e );
        }
    }

    /**
     * Brings violations in line with a full calendar snapshot: resolves open violations whose
     * reservation is back on the locked bed, and flags locked reservations found elsewhere (moves
     * made while the WebSocket was disconnected). Reservations missing from the snapshot (outside its
     * window, canceled) are left alone.
     *
     * @param snapshot assignments decoded from {@code on_migrate}
     */
    public void reconcileSnapshot( List<BookingAssignment> snapshot ) {
        Set<Long> lockedReservationIds = fetchLockedReservationIds();
        if ( lockedReservationIds.isEmpty() || snapshot == null ) {
            return;
        }
        Map<Long, List<BookingAssignment>> byReservation = new HashMap<>();
        for ( BookingAssignment a : snapshot ) {
            if ( a.getReservationId() != null && lockedReservationIds.contains( a.getReservationId() ) ) {
                byReservation.computeIfAbsent( a.getReservationId(), k -> new ArrayList<>() ).add( a );
            }
        }
        for ( Map.Entry<Long, List<BookingAssignment>> e : byReservation.entrySet() ) {
            try {
                List<BedLock> locks = dao.fetchActiveBedLocksForReservation( e.getKey() );
                for ( BedLock lock : locks ) {
                    reconcileLock( lock, locks, e.getValue() );
                }
            }
            catch ( RuntimeException ex ) {
                LOGGER.error( "Bed lock snapshot reconcile failed for reservation {}", e.getKey(), ex );
            }
        }
    }

    private void reconcileLock( BedLock lock, List<BedLock> reservationLocks, List<BookingAssignment> assignments ) {
        BedLockViolation open = dao.fetchOpenBedLockViolation( lock.getId() );
        Timestamp now = new Timestamp( System.currentTimeMillis() );

        if ( assignments.stream().anyMatch( a -> lock.getRoomId().equals( a.getRoomId() ) ) ) {
            if ( open != null ) {
                open.setResolvedDate( now );
                open.setResolution( BedLockViolation.RESOLUTION_MOVED_BACK );
                dao.saveBedLockViolation( open );
                LOGGER.info( "Locked bed restored (snapshot): reservation {} back in {}", lock.getReservationId(),
                        describe( lock.getRoom(), lock.getBedName(), lock.getRoomId() ) );
            }
            return;
        }

        // with several rooms on the reservation, skip the ones sitting on its other locked beds
        Set<String> otherLockedRooms = reservationLocks.stream()
                .map( BedLock::getRoomId )
                .collect( Collectors.toSet() );
        BookingAssignment moved = assignments.stream()
                .filter( a -> false == otherLockedRooms.contains( a.getRoomId() ) )
                .findFirst().orElse( null );
        if ( moved == null ) {
            return;
        }

        if ( open == null ) {
            BedLockViolation v = new BedLockViolation();
            v.setBedLockId( lock.getId() );
            v.setReservationId( lock.getReservationId() );
            v.setGuestName( StringUtils.defaultIfBlank( moved.getGuestName(), lock.getGuestName() ) );
            v.setFromRoomId( lock.getRoomId() );
            v.setFromRoom( lock.getRoom() );
            v.setFromBedName( lock.getBedName() );
            setDestination( v, moved );
            v.setDetectedDate( now );
            dao.saveBedLockViolation( v );
            LOGGER.warn( "Locked bed moved (snapshot): reservation {} ({}) {} -> {}", v.getReservationId(),
                    v.getGuestName(), describe( v.getFromRoom(), v.getFromBedName(), v.getFromRoomId() ),
                    describe( v.getToRoom(), v.getToBedName(), v.getToRoomId() ) );
            sendAlert( lock, v, moved );
        }
        else if ( false == Objects.equals( open.getToRoomId(), moved.getRoomId() ) ) {
            setDestination( open, moved );
            dao.saveBedLockViolation( open );
        }
    }

    private void checkLock( BedLock lock, BookingAssignment previous, BookingAssignment next ) {
        BedLockViolation open = dao.fetchOpenBedLockViolation( lock.getId() );
        Timestamp now = new Timestamp( System.currentTimeMillis() );

        if ( open == null ) {
            if ( lock.getRoomId().equals( previous.getRoomId() ) ) {
                BedLockViolation v = new BedLockViolation();
                v.setBedLockId( lock.getId() );
                v.setReservationId( lock.getReservationId() );
                v.setGuestName( StringUtils.defaultIfBlank( next.getGuestName(), lock.getGuestName() ) );
                v.setFromRoomId( previous.getRoomId() );
                v.setFromRoom( previous.getRoom() );
                v.setFromBedName( previous.getBedName() );
                setDestination( v, next );
                v.setDetectedDate( now );
                dao.saveBedLockViolation( v );
                LOGGER.warn( "Locked bed moved: reservation {} ({}) {} -> {}", v.getReservationId(),
                        v.getGuestName(), describe( v.getFromRoom(), v.getFromBedName(), v.getFromRoomId() ),
                        describe( v.getToRoom(), v.getToBedName(), v.getToRoomId() ) );
                sendAlert( lock, v, next );
            }
            return;
        }

        // only follow the assignment that was moved off the locked bed
        if ( false == Objects.equals( open.getToRoomId(), previous.getRoomId() ) ) {
            return;
        }
        if ( lock.getRoomId().equals( next.getRoomId() ) ) {
            open.setResolvedDate( now );
            open.setResolution( BedLockViolation.RESOLUTION_MOVED_BACK );
            LOGGER.info( "Locked bed restored: reservation {} back in {}", lock.getReservationId(),
                    describe( lock.getRoom(), lock.getBedName(), lock.getRoomId() ) );
        }
        else {
            setDestination( open, next );
        }
        dao.saveBedLockViolation( open );
    }

    private static void setDestination( BedLockViolation v, BookingAssignment next ) {
        v.setToRoomId( next.getRoomId() );
        v.setToRoom( next.getRoom() );
        v.setToBedName( next.getBedName() );
    }

    private void sendAlert( BedLock lock, BedLockViolation v, BookingAssignment next ) {
        if ( "false".equalsIgnoreCase( dao.getOptionNoCache( OPTION_EMAIL_ALERTS ) ) ) {
            return;
        }
        String from = describe( v.getFromRoom(), v.getFromBedName(), v.getFromRoomId() );
        String to = describe( v.getToRoom(), v.getToBedName(), v.getToRoomId() );
        String link = StringUtils.isBlank( next.getDataHref() ) ? String.valueOf( v.getReservationId() )
                : "<a href=\"https://hotels.cloudbeds.com" + next.getDataHref() + "\">" + v.getReservationId() + "</a>";
        String body = "<p>A booking locked to a bed has been moved on the Cloudbeds calendar.</p>"
                + "<p>Guest: " + escape( v.getGuestName() ) + "<br/>"
                + "Reservation: " + link + "<br/>"
                + "Locked to: " + escape( from ) + "<br/>"
                + "Now in: " + escape( to ) + "<br/>"
                + "Locked by: " + escape( StringUtils.defaultIfBlank( lock.getLockedBy(), "unknown" ) ) + "</p>"
                + "<p>Move it back or unlock it on the calendar to clear this alert.</p>";
        try {
            gmail.sendEmailToSelf( "Locked bed moved: " + v.getGuestName() + " (" + from + " -> " + to + ")", body );
        }
        catch ( Exception e ) {
            LOGGER.error( "Failed to send bed lock alert for reservation {}", v.getReservationId(), e );
        }
    }

    static String describe( String room, String bedName, String roomId ) {
        if ( roomId == null ) {
            return "Unassigned";
        }
        if ( StringUtils.isBlank( room ) ) {
            return roomId;
        }
        return StringUtils.isBlank( bedName ) ? room : room + " - " + bedName;
    }

    private static String escape( String s ) {
        return StringEscapeUtils.escapeHtml4( StringUtils.defaultString( s ) );
    }
}
