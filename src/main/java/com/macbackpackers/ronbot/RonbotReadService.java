package com.macbackpackers.ronbot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.cloudbeds.responses.BookingNote;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.ronbot.dto.BookingSummaryDto;
import com.macbackpackers.ronbot.dto.BookingTimelineDto;
import com.macbackpackers.ronbot.dto.JobHistoryDto;
import com.macbackpackers.ronbot.dto.TransactionDto;
import com.macbackpackers.scrapers.CloudbedsScraper;

@Service
public class RonbotReadService {

    private static final Logger LOGGER = LoggerFactory.getLogger( RonbotReadService.class );

    private final PropertyContextRegistry propertyContexts;

    public RonbotReadService( PropertyContextRegistry propertyContexts ) {
        this.propertyContexts = propertyContexts;
    }

    public BookingSummaryDto getBooking( String property, String reservationId, String bookingReference )
            throws IOException {
        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            String resolvedId = resolveReservationId( scraper, webClient, reservationId, bookingReference );
            Reservation reservation = scraper.getReservationRetry( webClient, resolvedId );
            return toBookingSummary( property, reservation );
        }
    }

    public List<TransactionDto> listTransactions( String property, String reservationId ) throws IOException {
        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            List<TransactionRecord> records = scraper.getTransactionsByReservation( webClient, reservationId );
            return records.stream().map( this::toTransactionDto ).collect( Collectors.toList() );
        }
    }

    public BookingTimelineDto getTimeline( String property, String reservationId ) throws IOException {
        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );
        WordPressDAO dao = ctx.getBean( WordPressDAO.class );

        BookingTimelineDto timeline = new BookingTimelineDto();
        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            Reservation reservation = scraper.getReservationRetry( webClient, reservationId );
            timeline.setBooking( toBookingSummary( property, reservation ) );
            List<TransactionRecord> records = scraper.getTransactionsByReservation( webClient, reservationId );
            timeline.setTransactions( records.stream().map( this::toTransactionDto ).collect( Collectors.toList() ) );
        }

        List<Map<String, Object>> jobs = dao.listJobsForReservation( reservationId, 100 );
        List<JobHistoryDto> jobDtos = new ArrayList<>();
        for ( Map<String, Object> row : jobs ) {
            JobHistoryDto dto = new JobHistoryDto();
            dto.setJobId( (Integer) row.get( "jobId" ) );
            dto.setClassname( Objects.toString( row.get( "classname" ), null ) );
            dto.setStatus( Objects.toString( row.get( "status" ), null ) );
            dto.setProcessedBy( Objects.toString( row.get( "processedBy" ), null ) );
            dto.setCreatedDate( Objects.toString( row.get( "createdDate" ), null ) );
            dto.setStartDate( Objects.toString( row.get( "startDate" ), null ) );
            dto.setEndDate( Objects.toString( row.get( "endDate" ), null ) );
            dto.setLastUpdatedDate( Objects.toString( row.get( "lastUpdatedDate" ), null ) );
            @SuppressWarnings( "unchecked" )
            Map<String, String> params = (Map<String, String>) row.get( "parameters" );
            dto.setParameters( params );
            jobDtos.add( dto );
        }
        timeline.setJobs( jobDtos );
        return timeline;
    }

    private String resolveReservationId( CloudbedsScraper scraper, WebClient webClient,
            String reservationId, String bookingReference ) throws IOException {
        if ( StringUtils.isNotBlank( reservationId ) ) {
            return reservationId.trim();
        }
        if ( StringUtils.isBlank( bookingReference ) ) {
            throw new IllegalArgumentException( "Either reservation_id or booking_reference is required" );
        }
        String query = bookingReference.trim();
        LOGGER.info( "Resolving booking reference {} via Cloudbeds search", query );
        List<Customer> matches = scraper.getReservations( webClient, query );
        if ( matches.isEmpty() ) {
            throw new MissingUserDataException( "No reservation found for booking_reference=" + query );
        }
        // Prefer exact identifier / third-party match when search returns multiple hits
        for ( Customer c : matches ) {
            Reservation full = scraper.getReservationRetry( webClient, c.getId() );
            if ( query.equalsIgnoreCase( StringUtils.trimToEmpty( full.getIdentifier() ) )
                    || query.equalsIgnoreCase( StringUtils.trimToEmpty( full.getThirdPartyIdentifier() ) )
                    || query.equals( full.getReservationId() ) ) {
                return full.getReservationId();
            }
        }
        return matches.get( 0 ).getId();
    }

    private BookingSummaryDto toBookingSummary( String property, Reservation r ) {
        BookingSummaryDto dto = new BookingSummaryDto();
        dto.setProperty( property );
        dto.setReservationId( r.getReservationId() );
        dto.setIdentifier( r.getIdentifier() );
        dto.setThirdPartyIdentifier( r.getThirdPartyIdentifier() );
        dto.setStatus( r.getStatus() );
        dto.setFirstName( r.getFirstName() );
        dto.setLastName( r.getLastName() );
        dto.setEmail( r.getEmail() );
        dto.setSourceName( r.getSourceName() );
        dto.setCheckinDate( r.getCheckinDate() );
        dto.setCheckoutDate( r.getCheckoutDate() );
        dto.setNights( r.getNights() );
        dto.setGrandTotal( r.getGrandTotal() );
        dto.setBalanceDue( r.getBalanceDue() );
        dto.setPaidValue( r.getPaidValue() );
        dto.setChannelPaymentType( r.getChannelPaymentType() );
        dto.setIsHotelCollectBooking( r.getIsHotelCollectBooking() );
        dto.setAdultsNumber( r.getAdultsNumber() );
        dto.setKidsNumber( r.getKidsNumber() );
        dto.setBookingDateHotelTime( r.getBookingDateHotelTime() );
        dto.setSpecialRequests( r.getSpecialRequests() );
        dto.setCreditCardLast4Digits( r.getCreditCardLast4Digits() );
        dto.setCreditCardType( r.getCreditCardType() );

        if ( r.getNotes() != null ) {
            List<Map<String, Object>> notes = new ArrayList<>();
            for ( BookingNote note : r.getNotes() ) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put( "created", note.getCreated() );
                n.put( "ownerName", note.getOwnerName() );
                n.put( "notes", note.getNotes() );
                notes.add( n );
            }
            dto.setNotes( notes );
        }

        if ( r.getBookingRooms() != null ) {
            List<Map<String, Object>> rooms = new ArrayList<>();
            for ( BookingRoom room : r.getBookingRooms() ) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put( "roomId", room.getRoomId() );
                rm.put( "roomNumber", room.getRoomNumber() );
                rm.put( "roomTypeName", room.getRoomTypeName() );
                rm.put( "guestCount", room.getGuestCount() );
                rooms.add( rm );
            }
            dto.setRooms( rooms );
        }
        return dto;
    }

    private TransactionDto toTransactionDto( TransactionRecord record ) {
        TransactionDto dto = new TransactionDto();
        dto.setId( record.getId() );
        dto.setReservationId( record.getReservationId() );
        dto.setDatetimeTransaction( record.getDatetimeTransaction() );
        dto.setDescription( record.getDescription() );
        dto.setNotes( record.getNotes() );
        dto.setType( record.getType() );
        dto.setVoidFlag( record.getVoidFlag() );
        dto.setCanBeVoided( record.getCanBeVoided() );
        dto.setDebit( record.getDebit() );
        dto.setCredit( record.getCredit() );
        dto.setPaid( record.getPaid() );
        dto.setTransactionType( record.getTransactionType() );
        dto.setCreditCardType( record.getCreditCardType() );
        dto.setCardNumberLast4( last4( record.getCardNumber() ) );
        dto.setPaymentStatus( record.getPaymentStatus() );
        dto.setGatewayName( record.getGatewayName() );
        dto.setOriginalDescription( record.getOriginalDescription() );
        return dto;
    }

    private static String last4( String cardNumber ) {
        if ( StringUtils.isBlank( cardNumber ) ) {
            return null;
        }
        String digits = cardNumber.replaceAll( "[^0-9]", "" );
        if ( digits.length() >= 4 ) {
            return digits.substring( digits.length() - 4 );
        }
        // Already masked values like ****1234
        if ( cardNumber.length() >= 4 ) {
            return cardNumber.substring( cardNumber.length() - 4 );
        }
        return cardNumber;
    }
}
