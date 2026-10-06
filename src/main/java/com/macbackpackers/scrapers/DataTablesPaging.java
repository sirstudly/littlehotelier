package com.macbackpackers.scrapers;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.exceptions.MissingUserDataException;

/**
 * Pages through Cloudbeds DataTables-style responses ({@code aaData} / {@code iTotalDisplayRecords})
 * using the front-end page size of {@link CloudbedsJsonRequestFactory#DATATABLES_PAGE_SIZE}.
 * <p>
 * Cloudbeds can filter rows out of a page after applying the limit, so pages may come back short;
 * the offset always advances by the page size, never by the number of rows returned.
 */
public final class DataTablesPaging {

    private static final Logger LOGGER = LoggerFactory.getLogger( DataTablesPaging.class );

    static final int PAGE_SIZE = CloudbedsJsonRequestFactory.DATATABLES_PAGE_SIZE;

    /** safety net against looping forever (100,000 rows) */
    static final int MAX_PAGES = 400;

    private static final long SLOT_MINUTES = 30;

    /** Fetches one page starting at the given row offset. */
    @FunctionalInterface
    public interface PageFetcher {
        JsonObject fetch( int displayStart ) throws IOException;
    }

    /** Fetches one page of the property-wide activity log for the given window. */
    @FunctionalInterface
    public interface ActivityLogPageFetcher {
        JsonObject fetch( Instant from, Instant to, int displayStart ) throws IOException;
    }

    private DataTablesPaging() {
        // static methods only
    }

    /**
     * Fetches all rows, one page at a time.
     * 
     * @param fetcher fetches a single page
     * @return all aaData rows in the order returned
     * @throws IOException on fetch failure or if the page limit is exceeded
     */
    public static List<JsonElement> fetchAllRows( PageFetcher fetcher ) throws IOException {
        return fetchRemainingRows( fetcher, fetcher.fetch( 0 ) );
    }

    /**
     * Fetches all rows given the first page has already been retrieved.
     */
    static List<JsonElement> fetchRemainingRows( PageFetcher fetcher, JsonObject firstPage ) throws IOException {
        List<JsonElement> rows = new ArrayList<>();
        JsonObject page = firstPage;
        int start = 0;
        for ( int pagesFetched = 1 ; ; pagesFetched++ ) {
            JsonArray data = page.getAsJsonArray( "aaData" );
            if ( data == null ) {
                throw new MissingUserDataException( "Missing aaData in response." );
            }
            data.forEach( rows::add );
            Integer total = getTotalDisplayRecords( page );
            start += PAGE_SIZE;
            if ( total == null ? data.size() == 0 : start >= total ) {
                return rows;
            }
            if ( pagesFetched >= MAX_PAGES ) {
                throw new IOException( "Exceeded " + MAX_PAGES + " pages of " + PAGE_SIZE + " rows; aborting." );
            }
            page = fetcher.fetch( start );
        }
    }

    /**
     * Fetches the property-wide activity log between {@code from} and {@code to} (both on a half-hour
     * boundary). Each window is kept within a single page where possible: a window with more than
     * {@link #PAGE_SIZE} events is split at a half-hour boundary and each half fetched separately.
     * A single half-hour slot that still doesn't fit is paged through.
     * 
     * @param fetcher fetches a single page for a window
     * @param from start of window
     * @param to end of window
     * @return all aaData rows (a row logged exactly on a split boundary may appear twice)
     * @throws IOException on fetch failure
     */
    public static List<JsonElement> fetchActivityLogRows( ActivityLogPageFetcher fetcher, Instant from, Instant to ) throws IOException {
        JsonObject firstPage = fetcher.fetch( from, to, 0 );
        Integer total = getTotalDisplayRecords( firstPage );
        long slots = Duration.between( from, to ).toMinutes() / SLOT_MINUTES;
        if ( total != null && total > PAGE_SIZE ) {
            if ( slots > 1 ) {
                Instant mid = from.plus( ( slots / 2 ) * SLOT_MINUTES, ChronoUnit.MINUTES );
                List<JsonElement> rows = new ArrayList<>( fetchActivityLogRows( fetcher, from, mid ) );
                rows.addAll( fetchActivityLogRows( fetcher, mid, to ) );
                return rows;
            }
            LOGGER.warn( "{} activity log entries between {} and {}; paging through a single half-hour slot "
                    + "(entries tied on a page boundary may be missed)", total, from, to );
        }
        return fetchRemainingRows( start -> fetcher.fetch( from, to, start ), firstPage );
    }

    private static Integer getTotalDisplayRecords( JsonObject page ) {
        JsonElement total = page.get( "iTotalDisplayRecords" );
        if ( total == null || total.isJsonNull() ) {
            return null;
        }
        try {
            return total.getAsInt();
        }
        catch ( NumberFormatException ex ) {
            return null;
        }
    }
}
