package com.macbackpackers.ronbot;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.ronbot.dto.AvailabilityDto;
import com.macbackpackers.ronbot.dto.BookingSummaryDto;
import com.macbackpackers.ronbot.dto.BookingTimelineDto;
import com.macbackpackers.ronbot.dto.ChannelProductionDto;
import com.macbackpackers.ronbot.dto.OccupancyDto;
import com.macbackpackers.ronbot.dto.ReservationSearchCriteria;
import com.macbackpackers.ronbot.dto.ReservationSearchDto;
import com.macbackpackers.ronbot.dto.StayContinuationDto;
import com.macbackpackers.ronbot.dto.TransactionDto;

@Profile( "ronbot-parent" )
@RestController
@RequestMapping( "/ronbot" )
public class RonbotReadController {

    private final RonbotReadService readService;
    private final PropertyContextRegistry propertyContexts;

    public RonbotReadController( RonbotReadService readService, PropertyContextRegistry propertyContexts ) {
        this.readService = readService;
        this.propertyContexts = propertyContexts;
    }

    @GetMapping( "/health" )
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put( "status", "ok" );
        body.put( "properties", propertyContexts.availableProperties() );
        return body;
    }

    @GetMapping( "/properties" )
    public Map<String, Object> properties() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put( "properties", propertyContexts.availableProperties().stream()
                .sorted()
                .collect( Collectors.toList() ) );
        return body;
    }

    /**
     * Search by visible reservation id, third-party/OTA ref, guest name, or internal id.
     * Returns 0–N matches (empty → 404 via MissingUserDataException).
     */
    @GetMapping( "/{property}/reservations" )
    public List<BookingSummaryDto> searchBookings(
            @PathVariable String property,
            @RequestParam( name = "query" ) String query ) throws IOException {
        if ( StringUtils.isBlank( query ) ) {
            throw new IllegalArgumentException( "Provide query (visible reservation id, OTA ref, or guest name)" );
        }
        return readService.searchBookings( property, query );
    }

    /**
     * Same as {@link #searchBookings} with the path segment as the search query.
     */
    @GetMapping( "/{property}/reservations/{query}" )
    public List<BookingSummaryDto> searchBookingsByPath(
            @PathVariable String property,
            @PathVariable String query ) throws IOException {
        return readService.searchBookings( property, query );
    }

    /**
     * Full Cloudbeds reservation list for free text and/or inclusive date ranges
     * (stay/checkin/checkout/booked), statuses and OTA source names.
     */
    @GetMapping( "/{property}/reservation-search" )
    public ReservationSearchDto searchReservations(
            @PathVariable String property,
            @ModelAttribute ReservationSearchCriteria criteria ) throws IOException {
        return readService.searchReservations( property, criteria );
    }

    @GetMapping( "/{property}/reservations/{query}/transactions" )
    public List<TransactionDto> listTransactions(
            @PathVariable String property,
            @PathVariable String query ) throws IOException {
        return readService.listTransactions( property, query );
    }

    @GetMapping( "/{property}/reservations/{query}/timeline" )
    public BookingTimelineDto timeline(
            @PathVariable String property,
            @PathVariable String query ) throws IOException {
        return readService.getTimeline( property, query );
    }

    /**
     * Whether the guest is staying on past {@code asOf} (same reservation extended, or a linked
     * follow-on booking in the same beds). Defaults {@code asOf}=today (Europe/London).
     */
    @GetMapping( "/{property}/reservations/{query}/stay-continuation" )
    public StayContinuationDto stayContinuation(
            @PathVariable String property,
            @PathVariable String query,
            @RequestParam( name = "asOf", required = false ) String asOf ) throws IOException {
        return readService.getStayContinuation( property, query, asOf );
    }

    /**
     * Live sellable bed/room counts by room type for an inclusive date range.
     * Defaults: {@code from}=today, {@code to}=today+1 (Europe/London).
     */
    @GetMapping( "/{property}/availability" )
    public AvailabilityDto availability(
            @PathVariable String property,
            @RequestParam( name = "from", required = false ) String from,
            @RequestParam( name = "to", required = false ) String to ) throws IOException {
        return readService.getAvailability( property, from, to );
    }

    /**
     * Live Channel Production totals by source for one stay-date month ({@code month}=YYYY-MM).
     */
    @GetMapping( "/{property}/channel-production" )
    public ChannelProductionDto channelProduction(
            @PathVariable String property,
            @RequestParam( name = "month" ) String month ) throws IOException {
        return readService.getChannelProduction( property, month );
    }

    /**
     * Beds-occupied rate over an inclusive stay-date range (max 366 nights), with a monthly breakdown.
     */
    @GetMapping( "/{property}/occupancy" )
    public OccupancyDto occupancy(
            @PathVariable String property,
            @RequestParam( name = "from" ) String from,
            @RequestParam( name = "to" ) String to ) throws IOException {
        return readService.getOccupancy( property, from, to );
    }

    @ExceptionHandler( IllegalArgumentException.class )
    public ResponseEntity<Map<String, String>> badRequest( IllegalArgumentException ex ) {
        return ResponseEntity.badRequest().body( Map.of( "error", ex.getMessage() ) );
    }

    @ExceptionHandler( MissingUserDataException.class )
    public ResponseEntity<Map<String, String>> notFound( MissingUserDataException ex ) {
        return ResponseEntity.status( HttpStatus.NOT_FOUND ).body( Map.of( "error", ex.getMessage() ) );
    }

    @ExceptionHandler( IOException.class )
    public ResponseEntity<Map<String, String>> io( IOException ex ) {
        return ResponseEntity.status( HttpStatus.BAD_GATEWAY )
                .body( Map.of( "error", "Cloudbeds request failed: " + ex.getMessage() ) );
    }
}
