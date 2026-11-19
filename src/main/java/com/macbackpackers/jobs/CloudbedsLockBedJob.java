package com.macbackpackers.jobs;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.cloudbedsws.CloudbedsCalendarEvent;
import com.macbackpackers.scrapers.cloudbedsws.CloudbedsWebSocketService;

/**
 * Locks or unlocks a Cloudbeds calendar assignment (native Classic calendar Lock / Unlock).
 * Sends {@code room_lock} / {@code room_unlock} over the processor's calendar WebSocket and waits
 * for confirmation unless {@code skip_confirm=true}.
 * <p>
 * Params: {@code reservation_id} (required); {@code lock} ({@code true}/{@code false}, required);
 * optionally {@code room_id}, {@code booking_rooms_id}, or {@code room_identifier} (skips REST
 * lookup when set); optionally {@code skip_confirm} (default {@code false}; fire-and-forget send).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CloudbedsLockBedJob" )
public class CloudbedsLockBedJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Transient
    private CloudbedsWebSocketService cloudbedsWebSocketService;

    @Override
    public void processJob() throws Exception {
        boolean lock = isLock();
        String reservationId = getReservationId();
        if ( StringUtils.isBlank( reservationId ) ) {
            throw new MissingUserDataException( "reservation_id is required" );
        }

        String roomIdentifier = StringUtils.trimToNull( getRoomIdentifier() );
        BookingRoom room = null;
        if ( roomIdentifier == null ) {
            Reservation reservation = cloudbedsScraper.getReservationRetry( cbWebClient, reservationId );
            if ( reservation == null ) {
                throw new MissingUserDataException( "No Cloudbeds reservation for id " + reservationId );
            }
            room = resolveBookingRoom( reservation );
            roomIdentifier = StringUtils.trimToNull( room.getRoomIdentifier() );
            if ( roomIdentifier == null ) {
                throw new MissingUserDataException(
                        "Booking room on reservation " + reservationId
                                + " has no room_identifier (cannot lock/unlock)" );
            }
            if ( StringUtils.isBlank( room.getRoomId() ) ) {
                throw new MissingUserDataException(
                        "Booking room on reservation " + reservationId
                                + " is unassigned (no room_id); assign a bed before locking" );
            }
        }

        boolean skipConfirm = isSkipConfirm();
        LOGGER.info( "{} bed for reservation_id={} room_identifier={} room_id={} booking_rooms_id={} skip_confirm={}",
                lock ? "Locking" : "Unlocking",
                reservationId,
                roomIdentifier,
                room != null ? room.getRoomId() : getRoomId(),
                room != null ? room.getId() : getBookingRoomsId(),
                skipConfirm );

        boolean awaitConfirm = !skipConfirm;
        CloudbedsCalendarEvent confirmed = lock
                ? cloudbedsWebSocketService.lockBed( roomIdentifier, awaitConfirm )
                : cloudbedsWebSocketService.unlockBed( roomIdentifier, awaitConfirm );

        if ( skipConfirm ) {
            LOGGER.info( "Cloudbeds {} sent (skip_confirm) for room_identifier={}",
                    lock ? "room_lock" : "room_unlock", roomIdentifier );
        }
        else {
            LOGGER.info( "Cloudbeds {} confirmed for room_identifier={} is_room_locked={} event_id={}",
                    lock ? "room_lock" : "room_unlock",
                    roomIdentifier,
                    confirmed.getIsRoomLocked(),
                    confirmed.getId() );
        }
    }

    private BookingRoom resolveBookingRoom( Reservation reservation ) {
        List<BookingRoom> rooms = reservation.getBookingRooms();
        if ( rooms == null || rooms.isEmpty() ) {
            throw new MissingUserDataException(
                    "Reservation " + reservation.getReservationId() + " has no booking_rooms" );
        }

        String bookingRoomsId = StringUtils.trimToNull( getBookingRoomsId() );
        String roomId = StringUtils.trimToNull( getRoomId() );

        if ( bookingRoomsId != null ) {
            for ( BookingRoom room : rooms ) {
                if ( bookingRoomsId.equals( room.getId() ) ) {
                    return room;
                }
            }
            throw new MissingUserDataException(
                    "No booking_rooms_id=" + bookingRoomsId + " on reservation "
                            + reservation.getReservationId() );
        }

        if ( roomId != null ) {
            List<BookingRoom> matches = rooms.stream()
                    .filter( r -> roomId.equals( r.getRoomId() ) )
                    .collect( Collectors.toList() );
            if ( matches.isEmpty() ) {
                throw new MissingUserDataException(
                        "No room_id=" + roomId + " on reservation " + reservation.getReservationId() );
            }
            if ( matches.size() > 1 ) {
                throw new MissingUserDataException(
                        "Multiple booking_rooms with room_id=" + roomId + " on reservation "
                                + reservation.getReservationId()
                                + "; pass booking_rooms_id instead" );
            }
            return matches.get( 0 );
        }

        if ( rooms.size() == 1 ) {
            return rooms.get( 0 );
        }

        throw new MissingUserDataException(
                "Reservation " + reservation.getReservationId() + " has " + rooms.size()
                        + " booking_rooms; pass room_id or booking_rooms_id" );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    @Override
    public int getRetryCount() {
        // Allow a couple of retries for transient WS disconnect; avoid hammering after success.
        return 3;
    }

    /**
     * @return {@code true} to lock, {@code false} to unlock
     */
    public boolean isLock() {
        String value = StringUtils.trimToNull( getParameter( "lock" ) );
        if ( value == null ) {
            throw new MissingUserDataException( "lock is required (true or false)" );
        }
        if ( "true".equalsIgnoreCase( value ) || "1".equals( value ) ) {
            return true;
        }
        if ( "false".equalsIgnoreCase( value ) || "0".equals( value ) ) {
            return false;
        }
        throw new MissingUserDataException( "lock must be true or false, got: " + value );
    }

    public void setLock( boolean lock ) {
        setParameter( "lock", Boolean.toString( lock ) );
    }

    /**
     * When {@code true}, send lock/unlock and complete without waiting for Cloudbeds confirmation.
     * Missing param defaults to {@code false}.
     */
    public boolean isSkipConfirm() {
        String value = StringUtils.trimToNull( getParameter( "skip_confirm" ) );
        if ( value == null ) {
            return false;
        }
        if ( "true".equalsIgnoreCase( value ) || "1".equals( value ) ) {
            return true;
        }
        if ( "false".equalsIgnoreCase( value ) || "0".equals( value ) ) {
            return false;
        }
        throw new MissingUserDataException( "skip_confirm must be true or false, got: " + value );
    }

    public void setSkipConfirm( boolean skipConfirm ) {
        setParameter( "skip_confirm", Boolean.toString( skipConfirm ) );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getRoomId() {
        return getParameter( "room_id" );
    }

    public void setRoomId( String roomId ) {
        setParameter( "room_id", roomId );
    }

    public String getBookingRoomsId() {
        return getParameter( "booking_rooms_id" );
    }

    public void setBookingRoomsId( String bookingRoomsId ) {
        setParameter( "booking_rooms_id", bookingRoomsId );
    }

    public String getRoomIdentifier() {
        return getParameter( "room_identifier" );
    }

    public void setRoomIdentifier( String roomIdentifier ) {
        setParameter( "room_identifier", roomIdentifier );
    }
}
