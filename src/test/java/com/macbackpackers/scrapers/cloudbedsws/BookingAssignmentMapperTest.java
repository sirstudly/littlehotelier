package com.macbackpackers.scrapers.cloudbedsws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.scrapers.CloudbedsScraper;

@ExtendWith( MockitoExtension.class )
public class BookingAssignmentMapperTest {

    @Mock
    private CloudbedsScraper scraper;

    @InjectMocks
    private BookingAssignmentMapper mapper;

    private Map<String, RoomBed> roomsById;

    @BeforeEach
    public void setUp() {
        roomsById = new HashMap<>();
        RoomBed bed = new RoomBed();
        bed.setId( "10-3" );
        bed.setRoom( "10" );
        bed.setBedName( "3" );
        bed.setRoomTypeId( 10 );
        roomsById.put( "10-3", bed );
        lenient().when( scraper.getPropertyId() ).thenReturn( "prop1" );
    }

    @Test
    public void mapsAssignedGuestWithReportFields() {
        Map<String, String> raw = baseGuest();
        raw.put( "room_id", "10-3" );
        raw.put( "booking_rooms_id", "br-99" );
        raw.put( "total", "120.50" );
        raw.put( "balance_due", "40.00" );
        raw.put( "email", "a@b.com" );
        raw.put( "adults", "2" );
        raw.put( "kids", "1" );
        raw.put( "is_hotel_collect_payment", "1" );
        raw.put( "booking_source", "Booking.com" );
        raw.put( "third_party_identifier", "BDC-1" );
        raw.put( "notes", "late arrival" );
        raw.put( "detailed_rates", "Non-refundable" );

        BookingAssignment a = mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById );
        assertThat( a, notNullValue() );
        assertThat( a.getAssignmentKey(), is( "br-99" ) );
        assertThat( a.getRoom(), is( "10" ) );
        assertThat( a.getBedName(), is( "3" ) );
        assertThat( a.getPaymentTotal(), comparesEqualTo( new BigDecimal( "120.50" ) ) );
        assertThat( a.getPaymentOutstanding(), comparesEqualTo( new BigDecimal( "40.00" ) ) );
        assertThat( a.getEmail(), is( "a@b.com" ) );
        assertThat( a.getNumberGuests(), is( 3 ) );
        assertThat( a.isHotelCollect(), is( true ) );
        assertThat( a.getBookingSource(), is( "Booking.com" ) );
        assertThat( a.getBookingReference(), is( "BDC-1" ) );
        assertThat( a.getNotes(), is( "late arrival" ) );
        assertThat( a.getRatePlanName(), is( "Non-refundable" ) );
        assertThat( a.getDataHref(), containsString( "reservations/555" ) );
    }

    @Test
    public void resolvesPropertyIdOnceAcrossEvents() {
        for ( int i = 0 ; i < 5 ; i++ ) {
            Map<String, String> raw = baseGuest();
            raw.put( "booking_rooms_id", "br-" + i );
            assertThat( mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById ).getDataHref(),
                    is( "/connect/prop1#/reservations/555" ) );
        }
        verify( scraper, times( 1 ) ).getPropertyId();
    }

    @Test
    public void truncatesLongDetailedRatesToColumnLength() {
        Map<String, String> raw = baseGuest();
        raw.put( "booking_rooms_id", "br-long" );
        raw.put( "detailed_rates", StringUtils.repeat( "x", 2000 ) );
        BookingAssignment a = mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById );
        assertThat( a.getRatePlanName().length(), is( BookingAssignment.RATE_PLAN_NAME_MAX_LENGTH ) );
    }

    @Test
    public void mapsUnassignedGuest() {
        Map<String, String> raw = baseGuest();
        raw.put( "booking_rooms_id", "na-1" );
        // no room_id
        BookingAssignment a = mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById );
        assertThat( a, notNullValue() );
        assertThat( a.getAssignmentKey(), is( "na-1" ) );
        assertThat( a.getRoomId(), nullValue() );
        assertThat( a.getRoom(), is( "Unallocated" ) );
    }

    @Test
    public void skipsCanceled() {
        Map<String, String> raw = baseGuest();
        raw.put( "status", "canceled" );
        raw.put( "room_id", "10-3" );
        assertThat( mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById ), nullValue() );
    }

    @Test
    public void mapsClosure() {
        Map<String, String> raw = new HashMap<>();
        raw.put( "id", "ev-c" );
        raw.put( "type", "out_of_service" );
        raw.put( "start_date", "2026-09-22" );
        raw.put( "end_date", "2026-09-24" );
        raw.put( "room_id", "10-3" );
        raw.put( "reason", "paint" );
        BookingAssignment a = mapper.toAssignment( new CloudbedsCalendarEvent( raw ), roomsById );
        assertThat( a, notNullValue() );
        assertThat( a.getAssignmentKey(), is( "closure:ev-c" ) );
        assertThat( a.isClosure(), is( true ) );
        assertThat( a.getReservationId(), is( 0L ) );
        assertThat( a.getGuestName(), is( "paint" ) );
    }

    @Test
    public void differsForVersioningIgnoresRestEnrichColumns() {
        BookingAssignment a = new BookingAssignment();
        a.setAssignmentKey( "k" );
        a.setRoomId( "1" );
        a.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        a.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        a.setSource( BookingAssignment.SOURCE_GUEST );
        a.setVisitorLevyTotal( new BigDecimal( "5" ) );
        a.setComments( "special" );

        BookingAssignment b = new BookingAssignment();
        b.setAssignmentKey( "k" );
        b.setRoomId( "1" );
        b.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        b.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        b.setSource( BookingAssignment.SOURCE_GUEST );
        b.setVisitorLevyTotal( new BigDecimal( "99" ) );
        b.setComments( "other" );

        assertThat( a.differsForVersioning( b ), is( false ) );
        b.setPaymentOutstanding( BigDecimal.ONE );
        assertThat( a.differsForVersioning( b ), is( true ) );
    }

    @Test
    public void preserveCalendarEventIdFromStopsRestNullChurn() {
        BookingAssignment current = new BookingAssignment();
        current.setAssignmentKey( "k" );
        current.setRoomId( "1" );
        current.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        current.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        current.setSource( BookingAssignment.SOURCE_GUEST );
        current.setCalendarEventId( "ev-ws" );

        BookingAssignment fromRest = new BookingAssignment();
        fromRest.setAssignmentKey( "k" );
        fromRest.setRoomId( "1" );
        fromRest.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        fromRest.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        fromRest.setSource( BookingAssignment.SOURCE_GUEST );
        // REST omit calendarEventId

        assertThat( current.differsForVersioning( fromRest ), is( true ) );
        fromRest.preserveCalendarEventIdFrom( current );
        assertThat( fromRest.getCalendarEventId(), is( "ev-ws" ) );
        assertThat( current.differsForVersioning( fromRest ), is( false ) );
    }

    @Test
    public void preserveCalendarEventIdFromAllowsRealEventIdChange() {
        BookingAssignment current = new BookingAssignment();
        current.setAssignmentKey( "k" );
        current.setRoomId( "1" );
        current.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        current.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        current.setSource( BookingAssignment.SOURCE_GUEST );
        current.setCalendarEventId( "ev-old" );

        BookingAssignment next = new BookingAssignment();
        next.setAssignmentKey( "k" );
        next.setRoomId( "1" );
        next.setCheckinDate( java.time.LocalDate.of( 2026, 9, 22 ) );
        next.setCheckoutDate( java.time.LocalDate.of( 2026, 9, 23 ) );
        next.setSource( BookingAssignment.SOURCE_GUEST );
        next.setCalendarEventId( "ev-new" );

        next.preserveCalendarEventIdFrom( current );
        assertThat( next.getCalendarEventId(), is( "ev-new" ) );
        assertThat( current.differsForVersioning( next ), is( true ) );
    }

    private static Map<String, String> baseGuest() {
        Map<String, String> raw = new HashMap<>();
        raw.put( "id", "ev-1" );
        raw.put( "type", "booked" );
        raw.put( "status", "confirmed" );
        raw.put( "booking_id", "555" );
        raw.put( "first_name", "Ada" );
        raw.put( "last_name", "Lovelace" );
        raw.put( "start_date", "2026-10-01" );
        raw.put( "end_date", "2026-10-03" );
        return raw;
    }
}
