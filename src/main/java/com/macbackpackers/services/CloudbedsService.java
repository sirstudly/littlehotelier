package com.macbackpackers.services;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

@Service
public class CloudbedsService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    @Autowired
    private WordPressDAO dao;
    
    @Autowired
    private CloudbedsScraper scraper;
    
    @Autowired
    private RoomBedMatcher roomBedMatcher;

    @Value( "${cloudbeds.property.id:0}" )
    private String PROPERTY_ID;

    /**
     * Dumps the allocations starting on the given date (inclusive).
     * 
     * @param webClient web client instance to use
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check allocations for (inclusive)
     * @param endDate the end date to check allocations for (exclusive)
     * @throws IOException on read/write error
     */
    public void dumpAllocationsFrom( WebClient webClient, int jobId, LocalDate startDate, LocalDate endDate ) throws IOException {
        AllocationList allocations = new AllocationList();
        List<Reservation> reservations = scraper.getReservations( webClient, startDate, endDate ).stream()
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .collect( Collectors.toList() );
        reservations.stream()
                .map( r -> reservationToAllocation( jobId, r ) )
                .forEach( a -> allocations.addAll( a ) );
        dao.insertAllocations( allocations );

        // now update the comments
        List<GuestCommentReportEntry> guestComments = reservations.stream()
                .filter( r -> StringUtils.isNotBlank( r.getSpecialRequests() ) )
                .map( r -> new GuestCommentReportEntry(
                        Integer.parseInt( r.getReservationId() ),
                        r.getSpecialRequests() ) )
                .collect( Collectors.toList() );
        dao.updateGuestCommentsForReservations( guestComments );

        // finally, add any staff allocations
        AllocationList staffAllocations = new AllocationList( getAllStaffAllocations( webClient, startDate ) );
        staffAllocations.forEach( a -> a.setJobId( jobId ) );
        LOGGER.info( "Inserting {} staff allocations.", staffAllocations.size() );
        dao.insertAllocations( staffAllocations );
    }

    /**
     * Finds all staff allocations for the given date (and the day after):
     * <ul>
     * <li>If stayDate is staff bed, stayDate + 1 is staff bed -&gt; allocation for 2 days
     * <li>If stayDate is staff bed, stayDate + 1 is not staff bed -&gt; allocation for 1st day
     * <li>If stayDate is not staff bed, stayDate +1 is staff bed -&gt; allocation for 2nd day
     * </ul>
     * 
     * @param webClient web client instance to use
     * @param stayDate the date we're searching on
     * @return non-null List of all Allocations (blocked/out of service)
     * @throws IOException on failure
     */
    public List<Allocation> getAllStaffAllocations( WebClient webClient, LocalDate stayDate ) throws IOException {

        LocalDate stayDatePlus1 = stayDate.plusDays( 1 );
        LocalDate stayDatePlus2 = stayDate.plusDays( 2 );
        JsonObject rpt = scraper.getAllStaffAllocations( webClient, stayDate );

        List<String> staffBedsBefore = extractStaffBedsFromRoomAssignmentReport( rpt, stayDate );
        List<String> staffBedsAfter = extractStaffBedsFromRoomAssignmentReport( rpt, stayDatePlus1 );
        
        Map<RoomBedLookup, RoomBed> roomBedMap = dao.fetchAllRoomBeds();
        
        // ** this is messy, but hopefully will just be temporary **
        // iterate through the staff beds on the first day
        // if also present on 2nd day, create record for 2 days
        // otherwise, create only record for 1st day
        // iterate through staff beds on second day
        // if bed doesn't already exist in allocations, create one
        Map<RoomBedLookup, Allocation> bedNameAllocations = new HashMap<>();
        staffBedsBefore
                .forEach( bedname -> {
                    BedAssignment bedAssign = roomBedMatcher.parse( bedname );
                    RoomBedLookup lookupKey = new RoomBedLookup( bedAssign.getRoom(), bedAssign.getBedName() );
                    RoomBed rb = roomBedMap.get( lookupKey );
                    if( rb == null ) {
                        throw new MissingUserDataException( "Missing mapping for " + lookupKey );
                    }
                    Allocation a = createAllocationFromRoomBed( rb );
                    a.setCheckinDate( stayDate );
                    a.setCheckoutDate( staffBedsAfter.contains( bedname ) ? stayDatePlus2 : stayDatePlus1 );
                    bedNameAllocations.put( lookupKey, a );
                } );

        staffBedsAfter.stream()
                .filter( bedname -> false == staffBedsBefore.contains( bedname ) )
                .forEach( bedname -> {
                    BedAssignment bedAssign = roomBedMatcher.parse( bedname );
                    RoomBedLookup lookupKey = new RoomBedLookup( bedAssign.getRoom(), bedAssign.getBedName() );
                    RoomBed rb = roomBedMap.get( lookupKey );
                    if( rb == null ) {
                        throw new MissingUserDataException( "Missing mapping for " + lookupKey );
                    }
                    Allocation a = createAllocationFromRoomBed( rb );
                    a.setCheckinDate( stayDatePlus1 );
                    a.setCheckoutDate( stayDatePlus2 );
                    bedNameAllocations.put( lookupKey, a );
                } );

        return bedNameAllocations.values().stream().collect( Collectors.toList() );
    }

    /**
     * Extracts all staff beds from the given assignment report.
     * 
     * @param rpt the JSON report
     * @param stayDate the date we're searching on
     * @return non-null list of (staff) bed names
     */
    private List<String> extractStaffBedsFromRoomAssignmentReport( JsonObject rpt, LocalDate stayDate ) {
        return rpt.get( "rooms" ).getAsJsonObject()
                .get( stayDate.format( YYYY_MM_DD ) ).getAsJsonObject()
                .entrySet().stream() // now streaming room types...
                .flatMap( e -> e.getValue().getAsJsonObject()
                        .get( "rooms" ).getAsJsonObject()
                        .entrySet().stream() ) // now streaming beds
                // only match beds where type is "Blocked Dates" or "Out of Service"
                .filter( e -> StreamSupport.stream( e.getValue().getAsJsonArray().spliterator(), false )
                        .anyMatch( x -> x.getAsJsonObject().has( "type" )
                                && Arrays.asList( "Blocked Dates", "Out of Service" ).contains(
                                        x.getAsJsonObject().get( "type" ).getAsString() ) ) )
                .map( e -> e.getKey().trim() )
                .collect( Collectors.toList() );
    }

    /**
     * Creates a blank allocation for the given room/bed.
     * 
     * @param roombed room/bed assignment
     * @return blank allocation missing checkin/checkout dates
     */
    private Allocation createAllocationFromRoomBed( RoomBed roombed ) {
        Allocation a = new Allocation();
        a.setRoomId( roombed.getId() );
        a.setRoomTypeId( roombed.getRoomTypeId() );
        a.setRoom( roombed.getRoom() );
        a.setBedName( roombed.getBedName() );
        a.setReservationId( 0 );
        a.setDataHref( "room_closures" ); // for housekeeping page
        return a;
    }

    /**
     * Converts a Reservation object (which contains multiple bed assignments) into a List of
     * Allocation.
     * 
     * @param lhWebClient web client instance to use
     * @param jobId job ID to populate allocation
     * @param r reservation to be converted
     * @return non-null list of allocation
     */
    private List<Allocation> reservationToAllocation( int jobId, Reservation r ) {
        
        // we create one record for each "booking room"
        return r.getBookingRooms().stream()
            .map( br -> { 
                BedAssignment bed = roomBedMatcher.parse( br.getRoomNumber() );
                Allocation a = new Allocation();
                a.setBedName( bed.getBedName() );
                a.setBookedDate( LocalDate.parse( r.getBookingDateHotelTime().substring( 0, 10 ) ) );
                a.setBookingReference( 
                        StringUtils.defaultIfBlank( r.getThirdPartyIdentifier(), r.getIdentifier() ) );
                a.setBookingSource( r.getSourceName() );
                a.setCheckinDate( LocalDate.parse( br.getStartDate() ) );
                a.setCheckoutDate( LocalDate.parse( br.getEndDate() ) );
                a.setDataHref( "/connect/" + PROPERTY_ID + "#/reservations/" + r.getReservationId());
                a.setGuestName( r.getFirstName() + " " + r.getLastName() );
                a.setJobId( jobId );
                a.setNumberGuests( r.getAdultsNumber() + r.getKidsNumber() );
                a.setPaymentOutstanding( r.getBalanceDue() );
                a.setPaymentTotal( r.getGrandTotal() );
                a.setReservationId( Integer.parseInt( r.getReservationId() ) );
                a.setRoom( bed.getRoom() );
                a.setRoomId( br.getRoomId() );
                a.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
                a.setStatus( r.getStatus() );
                a.setViewed( true );
                a.setNotes( r.getNotesAsString() );
                return a;
            } )
            .collect( Collectors.toList() );
    }

}
