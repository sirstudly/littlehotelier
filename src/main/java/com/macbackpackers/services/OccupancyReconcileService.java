package com.macbackpackers.services;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.OccupancyVersion;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

/**
 * Builds desired current {@link OccupancyVersion} rows from Cloudbeds REST and reconciles
 * {@code wp_lh_occupancy}, then recomputes housekeeping badges.
 */
@Service
public class OccupancyReconcileService {

    private static final Logger LOGGER = LoggerFactory.getLogger( OccupancyReconcileService.class );

    @Autowired
    private CloudbedsScraper scraper;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private RoomBedMatcher roomBedMatcher;

    @Autowired
    private CloudbedsService cloudbedsService;

    @Autowired
    private HousekeepingStatusService housekeepingStatusService;

    @Autowired
    @Qualifier( "ioThreadPool" )
    private java.util.concurrent.ExecutorService ioThreadPool;

    /**
     * Full reconcile for the housekeeping date window (selected−1 .. selected exclusive end+1 day dump).
     */
    public void reconcileFromCloudbeds( WebClient webClient, LocalDate selectedDate ) throws IOException {
        LocalDate startDate = selectedDate.minusDays( 1 );
        LocalDate endDate = selectedDate.plusDays( 1 );
        List<OccupancyVersion> desired = new ArrayList<>();
        desired.addAll( fetchGuestOccupancy( webClient, startDate, endDate ) );
        desired.addAll( fetchClosureOccupancy( webClient, startDate ) );
        LOGGER.info( "Reconciling {} occupancy currents for housekeeping date {}", desired.size(), selectedDate );
        dao.reconcileOccupancyCurrents( desired );
        housekeepingStatusService.recomputeAndPublish( selectedDate );
    }

    /**
     * Applies a WS calendar snapshot as a full reconcile for assignments overlapping the window.
     */
    public void reconcileFromSnapshot( List<OccupancyVersion> desired, LocalDate selectedDate ) {
        if ( desired == null ) {
            desired = List.of();
        }
        LOGGER.info( "Reconciling {} occupancy currents from WS snapshot for {}", desired.size(), selectedDate );
        dao.reconcileOccupancyCurrents( desired );
        housekeepingStatusService.recomputeAndPublish( selectedDate );
    }

    private List<OccupancyVersion> fetchGuestOccupancy( WebClient webClient, LocalDate startDate, LocalDate endDate )
            throws IOException {
        var mdcContext = MDC.getCopyOfContextMap();
        List<OccupancyVersion> out = java.util.Collections.synchronizedList( new ArrayList<>() );
        List<Future<?>> futures = scraper.getReservations( webClient, startDate, endDate ).stream()
                .map( customer -> ioThreadPool.submit( () -> {
                    if ( mdcContext != null ) {
                        MDC.setContextMap( mdcContext );
                    }
                    try {
                        Reservation reservation = scraper.getReservationRetry( webClient, customer.getId() );
                        out.addAll( reservationToOccupancy( reservation ) );
                    }
                    finally {
                        MDC.clear();
                    }
                } ) )
                .collect( Collectors.toList() );

        boolean hasError = false;
        int requestTimeout = Integer.parseInt( dao.getDefaultOption( "hbo_cloudbeds_request_timeout", "900" ) );
        for ( Future<?> future : futures ) {
            try {
                future.get( requestTimeout, TimeUnit.SECONDS );
            }
            catch ( TimeoutException | InterruptedException | ExecutionException e ) {
                LOGGER.error( "Error retrieving reservation for occupancy reconcile", e );
                hasError = true;
                break;
            }
        }
        if ( hasError ) {
            for ( Future<?> future : futures ) {
                if ( false == future.isDone() ) {
                    future.cancel( true );
                }
            }
            throw new IOException( "Error retrieving reservation for occupancy reconcile" );
        }
        return out;
    }

    List<OccupancyVersion> reservationToOccupancy( Reservation r ) {
        if ( r == null || r.getBookingRooms() == null ) {
            return List.of();
        }
        List<OccupancyVersion> list = new ArrayList<>();
        for ( BookingRoom br : r.getBookingRooms() ) {
            if ( StringUtils.isBlank( br.getRoomId() ) || StringUtils.isBlank( br.getStartDate() )
                    || StringUtils.isBlank( br.getEndDate() ) ) {
                continue;
            }
            OccupancyVersion o = new OccupancyVersion();
            o.setAssignmentKey( StringUtils.defaultIfBlank( br.getId(), "res:" + r.getReservationId() + ":" + br.getRoomId() ) );
            o.setBookingRoomsId( br.getId() );
            try {
                o.setReservationId( Long.parseLong( r.getReservationId() ) );
            }
            catch ( NumberFormatException e ) {
                o.setReservationId( null );
            }
            o.setRoomId( br.getRoomId() );
            BedAssignment bed = roomBedMatcher.parse( br.getRoomNumber() );
            o.setRoom( bed.getRoom() );
            o.setBedName( bed.getBedName() );
            try {
                o.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
            }
            catch ( Exception e ) {
                o.setRoomTypeId( null );
            }
            String guestName = StringUtils.trimToNull(
                    StringUtils.defaultString( br.getGuestFirstName() ) + " " + StringUtils.defaultString( br.getGuestLastName() ) );
            if ( guestName == null ) {
                guestName = r.getFirstName() + " " + r.getLastName();
            }
            o.setGuestName( guestName.trim() );
            o.setCheckinDate( LocalDate.parse( br.getStartDate() ) );
            o.setCheckoutDate( LocalDate.parse( br.getEndDate() ) );
            String bedStatus = CloudbedsService.derivePerBedStatus( r.getStatus(), br );
            o.setBedStatus( bedStatus );
            o.setInHouse( br.isInHouse() || "checked_in".equalsIgnoreCase( bedStatus ) );
            o.setSource( OccupancyVersion.SOURCE_GUEST );
            list.add( o );
        }
        return list;
    }

    private List<OccupancyVersion> fetchClosureOccupancy( WebClient webClient, LocalDate stayDate ) throws IOException {
        List<Allocation> staff = cloudbedsService.getAllStaffAllocations( webClient, stayDate );
        List<OccupancyVersion> list = new ArrayList<>();
        int i = 0;
        for ( Allocation a : staff ) {
            OccupancyVersion o = new OccupancyVersion();
            LocalDate checkin = toLocalDate( a.getCheckinDate(), stayDate );
            LocalDate checkout = toLocalDate( a.getCheckoutDate(), stayDate.plusDays( 1 ) );
            String key = "closure:" + StringUtils.defaultString( a.getRoomId() ) + ":" + checkin + ":" + checkout + ":" + ( i++ );
            o.setAssignmentKey( key );
            o.setRoomId( a.getRoomId() );
            o.setRoom( a.getRoom() );
            o.setBedName( a.getBedName() );
            o.setRoomTypeId( a.getRoomTypeId() );
            o.setGuestName( "Room closure" );
            o.setCheckinDate( checkin );
            o.setCheckoutDate( checkout );
            o.setBedStatus( "out_of_service" );
            o.setInHouse( false );
            o.setSource( OccupancyVersion.SOURCE_CLOSURE );
            o.setReservationId( 0L );
            list.add( o );
        }
        return list;
    }

    private static LocalDate toLocalDate( java.util.Date date, LocalDate fallback ) {
        if ( date == null ) {
            return fallback;
        }
        if ( date instanceof java.sql.Date ) {
            return ( (java.sql.Date) date ).toLocalDate();
        }
        return date.toInstant().atZone( java.time.ZoneId.systemDefault() ).toLocalDate();
    }
}
