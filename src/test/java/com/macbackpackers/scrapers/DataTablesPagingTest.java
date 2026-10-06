package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DataTablesPagingTest {

    private static JsonObject page( int rows, Integer total ) {
        JsonObject page = new JsonObject();
        JsonArray data = new JsonArray();
        for ( int i = 0 ; i < rows ; i++ ) {
            JsonArray row = new JsonArray();
            row.add( "row" + i );
            data.add( row );
        }
        page.add( "aaData", data );
        if ( total != null ) {
            page.addProperty( "iTotalDisplayRecords", String.valueOf( total ) );
        }
        return page;
    }

    @Test
    public void shortPagesAdvanceByPageSizeAndStopAtTotal() throws IOException {
        List<Integer> starts = new ArrayList<>();
        List<JsonElement> rows = DataTablesPaging.fetchAllRows( start -> {
            starts.add( start );
            // Cloudbeds filters after the limit so pages come back short
            return page( 240, 600 );
        } );
        assertThat( starts, contains( 0, 250, 500 ) );
        assertThat( rows, hasSize( 720 ) );
    }

    @Test
    public void singlePageWhenTotalFits() throws IOException {
        List<Integer> starts = new ArrayList<>();
        List<JsonElement> rows = DataTablesPaging.fetchAllRows( start -> {
            starts.add( start );
            return page( 12, 12 );
        } );
        assertThat( starts, contains( 0 ) );
        assertThat( rows, hasSize( 12 ) );
    }

    @Test
    public void stopsOnEmptyPageWithoutTotal() throws IOException {
        List<Integer> starts = new ArrayList<>();
        List<JsonElement> rows = DataTablesPaging.fetchAllRows( start -> {
            starts.add( start );
            return page( start < 500 ? 250 : 0, null );
        } );
        assertThat( starts, contains( 0, 250, 500 ) );
        assertThat( rows, hasSize( 500 ) );
    }

    @Test
    public void throwsAtPageCap() {
        assertThrows( IOException.class, () -> DataTablesPaging.fetchAllRows( start -> page( 250, null ) ) );
    }

    @Test
    public void activityLogWindowOverPageSizeIsSplitAtHalfHour() throws IOException {
        Instant from = Instant.parse( "2026-10-05T00:00:00Z" );
        Instant to = Instant.parse( "2026-10-05T02:00:00Z" );
        Instant mid = Instant.parse( "2026-10-05T01:00:00Z" );
        List<String> windows = new ArrayList<>();
        List<JsonElement> rows = DataTablesPaging.fetchActivityLogRows( ( f, t, start ) -> {
            windows.add( f + "/" + t + "@" + start );
            int total = f.equals( from ) && t.equals( to ) ? 300 : 150;
            return page( start == 0 ? total : 0, total );
        }, from, to );
        assertThat( windows, contains( from + "/" + to + "@0", from + "/" + mid + "@0", mid + "/" + to + "@0" ) );
        assertThat( rows, hasSize( 300 ) );
    }

    @Test
    public void singleSlotOverPageSizeIsPagedThrough() throws IOException {
        Instant from = Instant.parse( "2026-10-05T00:00:00Z" );
        Instant to = Instant.parse( "2026-10-05T00:30:00Z" );
        List<Integer> starts = new ArrayList<>();
        List<JsonElement> rows = DataTablesPaging.fetchActivityLogRows( ( f, t, start ) -> {
            starts.add( start );
            return page( start == 0 ? 250 : 50, 300 );
        }, from, to );
        assertThat( starts, contains( 0, 250 ) );
        assertThat( rows, hasSize( 300 ) );
    }
}
