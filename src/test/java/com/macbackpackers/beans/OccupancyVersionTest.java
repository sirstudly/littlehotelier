package com.macbackpackers.beans;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

public class OccupancyVersionTest {

    @Test
    public void preserveCalendarEventIdFromKeepsWsIdWhenIncomingBlank() {
        OccupancyVersion current = base( "ev-ws" );
        OccupancyVersion incoming = base( null );

        incoming.preserveCalendarEventIdFrom( current );

        assertThat( incoming.getCalendarEventId(), is( "ev-ws" ) );
        assertThat( current.differsForVersioning( incoming ), is( false ) );
    }

    @Test
    public void preserveCalendarEventIdFromDoesNotOverwriteNonBlank() {
        OccupancyVersion current = base( "ev-old" );
        OccupancyVersion incoming = base( "ev-new" );

        incoming.preserveCalendarEventIdFrom( current );

        assertThat( incoming.getCalendarEventId(), is( "ev-new" ) );
        assertThat( current.differsForVersioning( incoming ), is( true ) );
    }

    @Test
    public void preserveCalendarEventIdFromNoopWhenPreviousBlank() {
        OccupancyVersion current = base( null );
        OccupancyVersion incoming = base( null );

        incoming.preserveCalendarEventIdFrom( current );

        assertThat( incoming.getCalendarEventId(), nullValue() );
        assertThat( current.differsForVersioning( incoming ), is( false ) );
    }

    @Test
    public void differsForVersioningWhenEventIdsDiffer() {
        OccupancyVersion a = base( "ev-a" );
        OccupancyVersion b = base( "ev-b" );
        assertThat( a.differsForVersioning( b ), is( true ) );
    }

    private static OccupancyVersion base( String calendarEventId ) {
        OccupancyVersion o = new OccupancyVersion();
        o.setAssignmentKey( "226796445" );
        o.setRoomId( "112602-1" );
        o.setCheckinDate( LocalDate.of( 2026, 9, 17 ) );
        o.setCheckoutDate( LocalDate.of( 2026, 9, 20 ) );
        o.setBedStatus( "checked_in" );
        o.setInHouse( true );
        o.setGuestName( "Xin Su" );
        o.setSource( OccupancyVersion.SOURCE_GUEST );
        o.setCalendarEventId( calendarEventId );
        return o;
    }
}
