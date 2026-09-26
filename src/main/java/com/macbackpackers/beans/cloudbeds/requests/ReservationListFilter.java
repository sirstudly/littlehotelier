
package com.macbackpackers.beans.cloudbeds.requests;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Filter criteria for the Cloudbeds {@code mapi/reservation/list} endpoint.
 * All criteria are optional; date ranges are inclusive.
 */
public class ReservationListFilter {

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    private LocalDate stayDateStart;
    private LocalDate stayDateEnd;
    private LocalDate checkinDateStart;
    private LocalDate checkinDateEnd;
    private LocalDate checkoutDateStart;
    private LocalDate checkoutDateEnd;
    private LocalDate bookedDateStart;
    private LocalDate bookedDateEnd;
    private String statuses;
    private String sourceIds;
    private String searchInput;

    public ReservationListFilter stayDate( LocalDate start, LocalDate end ) {
        this.stayDateStart = start;
        this.stayDateEnd = end;
        return this;
    }

    public ReservationListFilter checkinDate( LocalDate start, LocalDate end ) {
        this.checkinDateStart = start;
        this.checkinDateEnd = end;
        return this;
    }

    public ReservationListFilter checkoutDate( LocalDate start, LocalDate end ) {
        this.checkoutDateStart = start;
        this.checkoutDateEnd = end;
        return this;
    }

    public ReservationListFilter bookedDate( LocalDate start, LocalDate end ) {
        this.bookedDateStart = start;
        this.bookedDateEnd = end;
        return this;
    }

    /**
     * @param statuses comma-delimited list of statuses; blank or "all" for no filter
     * @return this
     */
    public ReservationListFilter statuses( String statuses ) {
        this.statuses = statuses;
        return this;
    }

    /**
     * @param sourceIds comma-delimited list of booking source ids (e.g. "dir-1,7-398394-1")
     * @return this
     */
    public ReservationListFilter sourceIds( String sourceIds ) {
        this.sourceIds = sourceIds;
        return this;
    }

    /**
     * @param searchInput free-text search (matches name, reservation number, third-party reference, etc.)
     * @return this
     */
    public ReservationListFilter searchInput( String searchInput ) {
        this.searchInput = searchInput;
        return this;
    }

    /**
     * Returns the {@code filters} object to be serialized into the request body.
     * 
     * @return non-null (possibly empty) map of filters
     */
    public Map<String, Object> toFiltersMap() {
        Map<String, Object> filters = new LinkedHashMap<>();
        putDateRange( filters, "stayDate", stayDateStart, stayDateEnd );
        putDateRange( filters, "checkinDate", checkinDateStart, checkinDateEnd );
        putDateRange( filters, "checkoutDate", checkoutDateStart, checkoutDateEnd );
        putDateRange( filters, "bookedDate", bookedDateStart, bookedDateEnd );
        if ( StringUtils.isNotBlank( statuses ) && false == "all".equalsIgnoreCase( statuses.trim() ) ) {
            filters.put( "reservationStatus", splitCsv( statuses ) );
        }
        if ( StringUtils.isNotBlank( sourceIds ) ) {
            filters.put( "reservationSource", splitCsv( sourceIds ) );
        }
        if ( StringUtils.isNotBlank( searchInput ) ) {
            filters.put( "searchInput", searchInput.trim() );
        }
        return filters;
    }

    private static void putDateRange( Map<String, Object> filters, String key, LocalDate start, LocalDate end ) {
        if ( start != null ) {
            filters.put( key, Arrays.asList( start.format( YYYY_MM_DD ),
                    ( end == null ? start : end ).format( YYYY_MM_DD ) ) );
        }
    }

    private static List<String> splitCsv( String csv ) {
        return Arrays.stream( csv.split( "," ) )
                .map( String::trim )
                .filter( StringUtils::isNotBlank )
                .collect( Collectors.toList() );
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
