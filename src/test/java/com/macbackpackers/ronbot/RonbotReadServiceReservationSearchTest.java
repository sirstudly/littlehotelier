package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.macbackpackers.ronbot.dto.ReservationSearchCriteria;

public class RonbotReadServiceReservationSearchTest {

    @Test
    public void buildsQueryWithStayRangeAndStatuses() {
        ReservationSearchCriteria c = new ReservationSearchCriteria();
        c.setQuery( " tour " );
        c.setStayFrom( "2026-09-01" );
        c.setStayTo( "2026-09-30" );
        c.setStatuses( "confirmed,checked_out" );

        Map<String, Object> filters = RonbotReadService.toReservationListFilter( c ).toFiltersMap();

        assertEquals( "tour", filters.get( "searchInput" ) );
        assertEquals( Arrays.asList( "2026-09-01", "2026-09-30" ), filters.get( "stayDate" ) );
        assertEquals( Arrays.asList( "confirmed", "checked_out" ), filters.get( "reservationStatus" ) );
        assertFalse( filters.containsKey( "checkinDate" ) );
    }

    @Test
    public void loneFromOrToMeansSingleDay() {
        ReservationSearchCriteria c = new ReservationSearchCriteria();
        c.setCheckinTo( "2026-10-05" );
        c.setBookedFrom( "2026-09-20" );

        Map<String, Object> filters = RonbotReadService.toReservationListFilter( c ).toFiltersMap();

        assertEquals( Arrays.asList( "2026-10-05", "2026-10-05" ), filters.get( "checkinDate" ) );
        assertEquals( Arrays.asList( "2026-09-20", "2026-09-20" ), filters.get( "bookedDate" ) );
        assertFalse( filters.containsKey( "searchInput" ) );
    }

    @Test
    public void rejectsCriteriaWithoutQueryOrDates() {
        ReservationSearchCriteria c = new ReservationSearchCriteria();
        c.setStatuses( "confirmed" );
        assertThrows( IllegalArgumentException.class, () -> RonbotReadService.toReservationListFilter( c ) );
    }

    @Test
    public void limitDefaultsToAndIsCappedAtMax() {
        assertEquals( 1000, RonbotReadService.reservationSearchLimit( null ) );
        assertEquals( 200, RonbotReadService.reservationSearchLimit( 200 ) );
        assertEquals( 1000, RonbotReadService.reservationSearchLimit( 5000 ) );
        assertThrows( IllegalArgumentException.class, () -> RonbotReadService.reservationSearchLimit( 0 ) );
    }

    @Test
    public void rejectsReversedAndInvalidDates() {
        ReservationSearchCriteria reversed = new ReservationSearchCriteria();
        reversed.setStayFrom( "2026-09-30" );
        reversed.setStayTo( "2026-09-01" );
        assertThrows( IllegalArgumentException.class, () -> RonbotReadService.toReservationListFilter( reversed ) );

        ReservationSearchCriteria invalid = new ReservationSearchCriteria();
        invalid.setCheckoutFrom( "30/09/2026" );
        assertThrows( IllegalArgumentException.class, () -> RonbotReadService.toReservationListFilter( invalid ) );
    }
}
