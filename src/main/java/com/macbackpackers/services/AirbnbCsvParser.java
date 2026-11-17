package com.macbackpackers.services;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import com.macbackpackers.beans.AirbnbPayment;

/**
 * Parses Airbnb transaction CSV exports into one consolidated row per confirmation code.
 */
public final class AirbnbCsvParser {

    public static final String TYPE_RESERVATION = "Reservation";
    public static final String TYPE_PAYOUT = "Payout";
    public static final String TYPE_RESOLUTION = "Resolution Adjustment";
    public static final String TYPE_CANCELLATION_FEE = "Cancellation Fee";
    public static final String TYPE_ADJUSTMENT = "Adjustment";

    private AirbnbCsvParser() {
    }

    public static class ParseResult {
        private final List<AirbnbPayment> payments = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public List<AirbnbPayment> getPayments() {
            return payments;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public static ParseResult parse( String csvContent ) throws IOException {
        ParseResult result = new ParseResult();
        if ( StringUtils.isBlank( csvContent ) ) {
            result.getErrors().add( "CSV content is empty" );
            return result;
        }
        // Strip UTF-8 BOM if present
        String cleaned = csvContent.charAt( 0 ) == '\uFEFF' ? csvContent.substring( 1 ) : csvContent;

        Map<String, List<CSVRecord>> byConfirmation = new LinkedHashMap<>();
        try ( CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreEmptyLines()
                .withTrim().parse( new StringReader( cleaned ) ) ) {
            Map<String, Integer> headerMap = parser.getHeaderMap();
            requireHeader( headerMap, "Confirmation Code" );
            requireHeader( headerMap, "Guest" );
            requireHeader( headerMap, "Start date" );
            requireHeader( headerMap, "End date" );
            requireHeader( headerMap, "Amount" );
            requireHeader( headerMap, "Service fee" );
            requireHeader( headerMap, "Gross earnings" );

            for ( CSVRecord record : parser ) {
                String type = StringUtils.trimToEmpty( record.get( "Type" ) );
                if ( TYPE_PAYOUT.equalsIgnoreCase( type ) ) {
                    continue;
                }
                String confirmation = StringUtils.trimToEmpty( getOptional( record, "Confirmation Code" ) );
                if ( confirmation.isEmpty() ) {
                    continue;
                }
                byConfirmation.computeIfAbsent( confirmation, k -> new ArrayList<>() ).add( record );
            }
        }

        for ( Map.Entry<String, List<CSVRecord>> entry : byConfirmation.entrySet() ) {
            try {
                result.getPayments().add( consolidate( entry.getKey(), entry.getValue() ) );
            }
            catch ( Exception e ) {
                result.getErrors().add( entry.getKey() + ": " + e.getMessage() );
            }
        }
        return result;
    }

    private static void requireHeader( Map<String, Integer> headerMap, String name ) {
        if ( headerMap == null || false == headerMap.containsKey( name ) ) {
            throw new IllegalArgumentException( "Missing required CSV column: " + name );
        }
    }

    private static AirbnbPayment consolidate( String confirmation, List<CSVRecord> records )
            throws ParseException {
        AirbnbPayment payment = new AirbnbPayment();
        payment.setConfirmationCode( confirmation );

        boolean hasReservation = false;
        boolean hasResolution = false;
        boolean hasCancellation = false;
        boolean hasAdjustment = false;
        BigDecimal netAmount = BigDecimal.ZERO;
        BigDecimal netFee = BigDecimal.ZERO;
        BigDecimal netGross = BigDecimal.ZERO;
        CSVRecord reservationLine = null;

        List<String> types = new ArrayList<>();
        for ( CSVRecord record : records ) {
            String type = StringUtils.trimToEmpty( getOptional( record, "Type" ) );
            types.add( type );
            if ( TYPE_RESERVATION.equalsIgnoreCase( type ) ) {
                hasReservation = true;
                if ( reservationLine == null ) {
                    reservationLine = record;
                }
            }
            else if ( TYPE_RESOLUTION.equalsIgnoreCase( type ) ) {
                hasResolution = true;
            }
            else if ( TYPE_CANCELLATION_FEE.equalsIgnoreCase( type ) ) {
                hasCancellation = true;
            }
            else if ( TYPE_ADJUSTMENT.equalsIgnoreCase( type ) ) {
                hasAdjustment = true;
            }
            netAmount = netAmount.add( parseMoney( getOptional( record, "Amount" ) ) );
            netFee = netFee.add( parseMoney( getOptional( record, "Service fee" ) ) );
            netGross = netGross.add( parseMoney( getOptional( record, "Gross earnings" ) ) );
        }

        payment.setHasReservation( hasReservation ? "Y" : "N" );
        payment.setHasResolutionAdjustment( hasResolution ? "Y" : "N" );
        payment.setHasCancellationFee( hasCancellation ? "Y" : "N" );
        payment.setHasAdjustment( hasAdjustment ? "Y" : "N" );
        payment.setLineTypes( types.stream().distinct().collect( Collectors.joining( "," ) ) );
        payment.setNetAmount( netAmount.setScale( 2, RoundingMode.HALF_UP ) );
        payment.setNetServiceFee( netFee.setScale( 2, RoundingMode.HALF_UP ) );
        payment.setNetGrossEarnings( netGross.setScale( 2, RoundingMode.HALF_UP ) );

        CSVRecord identity = reservationLine != null ? reservationLine : records.get( 0 );
        String guest = StringUtils.trimToNull( getOptional( identity, "Guest" ) );
        String start = StringUtils.trimToNull( getOptional( identity, "Start date" ) );
        String end = StringUtils.trimToNull( getOptional( identity, "End date" ) );
        if ( hasReservation ) {
            if ( guest == null || start == null || end == null ) {
                throw new IllegalArgumentException( "Reservation line missing Guest/Start date/End date" );
            }
            String amountRaw = StringUtils.trimToNull( getOptional( identity, "Amount" ) );
            String feeRaw = StringUtils.trimToNull( getOptional( identity, "Service fee" ) );
            String grossRaw = StringUtils.trimToNull( getOptional( identity, "Gross earnings" ) );
            if ( amountRaw == null || feeRaw == null || grossRaw == null ) {
                throw new IllegalArgumentException(
                        "Reservation line missing Amount/Service fee/Gross earnings" );
            }
            payment.setAmount( parseMoney( amountRaw ) );
            payment.setServiceFee( parseMoney( feeRaw ) );
            payment.setGrossEarnings( parseMoney( grossRaw ) );
        }
        payment.setGuestName( guest );
        payment.setStartDate( start == null ? null : parseDate( start ) );
        payment.setEndDate( end == null ? null : parseDate( end ) );
        String booked = StringUtils.trimToNull( getOptional( identity, "Booking date" ) );
        payment.setBookedDate( booked == null ? null : parseDate( booked ) );
        return payment;
    }

    /**
     * Auto-eligible for child job without manual review: single Reservation line, no siblings,
     * positive chosen post amount.
     */
    public static boolean isAutoEligible( AirbnbPayment payment ) {
        if ( payment == null ) {
            return false;
        }
        if ( false == payment.isYn( payment.getHasReservation() ) ) {
            return false;
        }
        if ( payment.isYn( payment.getHasResolutionAdjustment() )
                || payment.isYn( payment.getHasCancellationFee() )
                || payment.isYn( payment.getHasAdjustment() ) ) {
            return false;
        }
        BigDecimal post = payment.getPostAmount();
        return post != null && post.compareTo( BigDecimal.ZERO ) > 0;
    }

    static BigDecimal parseMoney( String raw ) {
        if ( StringUtils.isBlank( raw ) ) {
            return BigDecimal.ZERO.setScale( 2, RoundingMode.HALF_UP );
        }
        String cleaned = raw.replace( ",", "" ).replace( "£", "" ).trim();
        return new BigDecimal( cleaned ).setScale( 2, RoundingMode.HALF_UP );
    }

    static Date parseDate( String raw ) throws ParseException {
        // Airbnb CSV uses MM/dd/yyyy
        SimpleDateFormat fmt = new SimpleDateFormat( "MM/dd/yyyy", Locale.US );
        fmt.setLenient( false );
        return new Date( fmt.parse( raw.trim() ).getTime() );
    }

    private static String getOptional( CSVRecord record, String name ) {
        try {
            return record.isMapped( name ) ? record.get( name ) : null;
        }
        catch ( IllegalArgumentException e ) {
            return null;
        }
    }
}
