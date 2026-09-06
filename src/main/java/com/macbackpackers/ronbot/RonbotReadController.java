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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.ronbot.dto.BookingSummaryDto;
import com.macbackpackers.ronbot.dto.BookingTimelineDto;
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

    @GetMapping( "/{property}/reservations/{reservationId}" )
    public BookingSummaryDto getBookingById(
            @PathVariable String property,
            @PathVariable String reservationId ) throws IOException {
        return readService.getBooking( property, reservationId, null );
    }

    @GetMapping( "/{property}/reservations" )
    public BookingSummaryDto getBookingByQuery(
            @PathVariable String property,
            @RequestParam( name = "reservation_id", required = false ) String reservationId,
            @RequestParam( name = "booking_reference", required = false ) String bookingReference )
            throws IOException {
        if ( StringUtils.isBlank( reservationId ) && StringUtils.isBlank( bookingReference ) ) {
            throw new IllegalArgumentException( "Provide reservation_id or booking_reference" );
        }
        return readService.getBooking( property, reservationId, bookingReference );
    }

    @GetMapping( "/{property}/reservations/{reservationId}/transactions" )
    public List<TransactionDto> listTransactions(
            @PathVariable String property,
            @PathVariable String reservationId ) throws IOException {
        return readService.listTransactions( property, reservationId );
    }

    @GetMapping( "/{property}/reservations/{reservationId}/timeline" )
    public BookingTimelineDto timeline(
            @PathVariable String property,
            @PathVariable String reservationId ) throws IOException {
        return readService.getTimeline( property, reservationId );
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
