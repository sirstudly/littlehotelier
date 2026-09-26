
package com.macbackpackers.beans.cloudbeds.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

public class ReservationListFilterTest {

    @Test
    public void emptyFilterProducesNoFilters() {
        assertTrue( new ReservationListFilter().toFiltersMap().isEmpty() );
    }

    @Test
    public void dateRangesAndStatusesMatchHarFormat() {
        Map<String, Object> filters = new ReservationListFilter()
                .bookedDate( LocalDate.parse( "2026-09-03" ), LocalDate.parse( "2026-09-14" ) )
                .checkoutDate( LocalDate.parse( "2026-09-08" ), LocalDate.parse( "2026-09-23" ) )
                .statuses( "no_show, not_confirmed,checked_in" )
                .toFiltersMap();
        assertEquals( "{\"checkoutDate\":[\"2026-09-08\",\"2026-09-23\"],"
                + "\"bookedDate\":[\"2026-09-03\",\"2026-09-14\"],"
                + "\"reservationStatus\":[\"no_show\",\"not_confirmed\",\"checked_in\"]}",
                new Gson().toJson( filters ) );
    }

    @Test
    public void nullEndDateDefaultsToStartDate() {
        Map<String, Object> filters = new ReservationListFilter()
                .checkinDate( LocalDate.parse( "2026-09-01" ), null )
                .toFiltersMap();
        assertEquals( Arrays.asList( "2026-09-01", "2026-09-01" ), filters.get( "checkinDate" ) );
    }

    @Test
    public void nullStartDateOmitsFilter() {
        Map<String, Object> filters = new ReservationListFilter()
                .stayDate( null, LocalDate.parse( "2026-09-01" ) )
                .toFiltersMap();
        assertFalse( filters.containsKey( "stayDate" ) );
    }

    @Test
    public void allOrBlankStatusesOmitFilter() {
        assertFalse( new ReservationListFilter().statuses( "all" ).toFiltersMap().containsKey( "reservationStatus" ) );
        assertFalse( new ReservationListFilter().statuses( " " ).toFiltersMap().containsKey( "reservationStatus" ) );
        assertFalse( new ReservationListFilter().statuses( null ).toFiltersMap().containsKey( "reservationStatus" ) );
    }

    @Test
    public void sourceIdsAndSearchInput() {
        Map<String, Object> filters = new ReservationListFilter()
                .sourceIds( "dir-1,dir-2,7-398394-1" )
                .searchInput( " 6202925288 " )
                .toFiltersMap();
        assertEquals( Arrays.asList( "dir-1", "dir-2", "7-398394-1" ), filters.get( "reservationSource" ) );
        assertEquals( "6202925288", filters.get( "searchInput" ) );
    }
}
