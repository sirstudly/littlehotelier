package com.macbackpackers.scrapers.cloudbedsws;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Maps Cloudbeds calendar WebSocket events to {@link BookingAssignment} rows.
 * No HK date-window filter; includes unassigned reservations.
 */
@Component
public class BookingAssignmentMapper {

    private static final DateTimeFormatter BOOKING_DATE_TIME =
            DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

    @Autowired
    private CloudbedsScraper scraper;

    /**
     * Converts a calendar event into a booking assignment, or null if not applicable
     * (unknown type / canceled guest / incomplete dates).
     */
    public BookingAssignment toAssignment( CloudbedsCalendarEvent event, Map<String, RoomBed> roomsById ) {
        if ( event == null ) {
            return null;
        }
        String type = StringUtils.defaultString( event.getType() ).toLowerCase();
        boolean closure = "out_of_service".equals( type ) || "blocked_dates".equals( type );
        boolean guest = "booked".equals( type ) || "checked_in".equals( type ) || "checked_out".equals( type );
        if ( false == closure && false == guest ) {
            return null;
        }
        if ( false == closure && ( StringUtils.isBlank( event.getBookingId() ) || "0".equals( event.getBookingId().trim() ) ) ) {
            return null;
        }
        // Canceled tiles should not remain as currents (WS usually deletes them; status may still arrive)
        if ( false == closure && event.isCanceled() ) {
            return null;
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse( event.getStartDate().trim() );
            end = LocalDate.parse( event.getEndDate().trim() );
        }
        catch ( Exception e ) {
            return null;
        }

        BookingAssignment a = new BookingAssignment();
        if ( closure ) {
            a.setAssignmentKey( "closure:" + event.getId() );
            a.setSource( BookingAssignment.SOURCE_CLOSURE );
            a.setReservationId( 0L );
            a.setGuestName( StringUtils.defaultIfBlank( event.getRaw().get( "reason" ), "Room closure" ) );
            a.setInHouse( false );
            a.setBedStatus( type );
        }
        else {
            String bookingRoomsId = event.getBookingRoomsId();
            String roomPart = StringUtils.defaultIfBlank( event.getRoomId(), "unassigned" );
            a.setAssignmentKey( StringUtils.defaultIfBlank( bookingRoomsId,
                    "res:" + event.getBookingId() + ":" + roomPart ) );
            a.setBookingRoomsId( bookingRoomsId );
            a.setSource( BookingAssignment.SOURCE_GUEST );
            try {
                a.setReservationId( Long.parseLong( event.getBookingId().trim() ) );
            }
            catch ( NumberFormatException e ) {
                a.setReservationId( null );
            }
            String name = StringUtils.trimToEmpty( event.getFirstName() ) + " "
                    + StringUtils.trimToEmpty( event.getLastName() );
            a.setGuestName( name.trim() );
            a.setEmail( StringUtils.trimToNull( event.getEmail() ) );
            String status = StringUtils.defaultIfBlank( event.getStatus(), type );
            // no_show kept (same as historic AllocationScraper statuses)
            a.setBedStatus( status );
            boolean inHouse = "checked_in".equalsIgnoreCase( type ) || "checked_in".equalsIgnoreCase( status );
            a.setInHouse( inHouse );
            a.setPaymentTotal( parseMoney( event.getTotal() ) );
            a.setPaymentOutstanding( event.getBalanceDueAmount() );
            a.setBookingSource( StringUtils.trimToNull( event.getBookingSource() ) );
            a.setBookingReference( StringUtils.defaultIfBlank(
                    event.getThirdPartyIdentifier(), null ) );
            a.setHotelCollect( event.isHotelCollectBooking() );
            a.setNotes( StringUtils.trimToNull( event.getNotes() ) );
            a.setRatePlanName( StringUtils.trimToNull( event.getDetailedRates() ) );
            a.setNumberGuests( parseGuestCount( event.getAdults(), event.getKids() ) );
            a.setBookedDate( parseBookingDate( event.getBookingDate() ) );
            if ( a.getReservationId() != null && a.getReservationId() > 0 ) {
                a.setDataHref( "/connect/" + scraper.getPropertyId() + "#/reservations/" + a.getReservationId() );
            }
        }

        a.setCalendarEventId( event.getId() );
        a.setRoomId( StringUtils.trimToNull( event.getRoomId() ) );
        a.setCheckinDate( start );
        a.setCheckoutDate( end );

        if ( StringUtils.isNotBlank( event.getRoomId() ) ) {
            RoomBed rb = roomsById == null ? null : roomsById.get( event.getRoomId() );
            if ( rb != null ) {
                a.setRoom( rb.getRoom() );
                a.setBedName( rb.getBedName() );
                a.setRoomTypeId( rb.getRoomTypeId() );
            }
        }
        else if ( false == closure ) {
            a.setRoom( "Unallocated" );
        }
        if ( a.getRoomTypeId() == null && StringUtils.isNotBlank( event.getResRtId() ) ) {
            try {
                a.setRoomTypeId( Integer.parseInt( event.getResRtId().trim() ) );
            }
            catch ( NumberFormatException ignored ) {
                // leave null
            }
        }

        a.setValidFrom( new Timestamp( System.currentTimeMillis() ) );
        return a;
    }

    private static BigDecimal parseMoney( String value ) {
        if ( StringUtils.isBlank( value ) ) {
            return null;
        }
        try {
            return new BigDecimal( value.trim() );
        }
        catch ( NumberFormatException e ) {
            return null;
        }
    }

    private static Integer parseGuestCount( String adults, String kids ) {
        int total = 0;
        boolean any = false;
        try {
            if ( StringUtils.isNotBlank( adults ) ) {
                total += Integer.parseInt( adults.trim() );
                any = true;
            }
            if ( StringUtils.isNotBlank( kids ) ) {
                total += Integer.parseInt( kids.trim() );
                any = true;
            }
        }
        catch ( NumberFormatException e ) {
            return any ? total : null;
        }
        return any ? total : null;
    }

    private static java.util.Date parseBookingDate( String bookingDate ) {
        if ( StringUtils.isBlank( bookingDate ) ) {
            return null;
        }
        String trimmed = bookingDate.trim();
        try {
            if ( trimmed.length() >= 19 ) {
                return java.sql.Timestamp.valueOf( LocalDateTime.parse( trimmed.substring( 0, 19 ), BOOKING_DATE_TIME ) );
            }
            return java.sql.Date.valueOf( LocalDate.parse( trimmed.substring( 0, 10 ) ) );
        }
        catch ( Exception e ) {
            try {
                return java.sql.Date.valueOf( LocalDate.parse( trimmed.substring( 0, 10 ) ) );
            }
            catch ( Exception e2 ) {
                return null;
            }
        }
    }
}
