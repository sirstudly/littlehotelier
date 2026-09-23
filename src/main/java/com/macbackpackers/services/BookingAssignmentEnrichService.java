package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

/**
 * REST enrichment and heal for {@link BookingAssignment}: folio fields WS cannot supply,
 * plus dual-write into {@code wp_lh_calendar} for legacy Allocation consumers.
 */
@Service
public class BookingAssignmentEnrichService {

    private static final Logger LOGGER = LoggerFactory.getLogger( BookingAssignmentEnrichService.class );

    @Autowired
    private CloudbedsScraper scraper;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private RoomBedMatcher roomBedMatcher;

    @Autowired
    @Qualifier( "ioThreadPool" )
    private java.util.concurrent.ExecutorService ioThreadPool;

    /**
     * Fetches a single reservation and patches all matching current assignment rows.
     */
    public void enrichReservation( WebClient webClient, String reservationId ) throws IOException {
        if ( StringUtils.isBlank( reservationId ) ) {
            return;
        }
        Reservation reservation = scraper.getReservationRetry( webClient, reservationId );
        applyReservationEnrich( reservation );
    }

    /**
     * REST-fetches all current guest assignments with null {@code last_rest_fetched_at}.
     */
    public void enrichAllNeedingRest( WebClient webClient ) throws IOException {
        List<Long> reservationIds = dao.fetchReservationIdsNeedingRestEnrich();
        if ( reservationIds == null || reservationIds.isEmpty() ) {
            LOGGER.info( "BookingAssignment enrich: nothing pending" );
            return;
        }
        LOGGER.info( "BookingAssignment enrich: fetching {} reservations", reservationIds.size() );
        fetchAndApply( webClient, reservationIds );
    }

    /**
     * Heal: light reservation list for [start, end], REST-fetch ids missing from currents or never enriched;
     * then project all currents into {@code wp_lh_calendar} under {@code jobId}.
     */
    public void healAndDualWrite( WebClient webClient, int jobId, java.time.LocalDate startDate,
            java.time.LocalDate endDate ) throws IOException {
        Set<Long> listIds = new HashSet<>();
        scraper.getReservations( webClient, startDate, endDate ).forEach( c -> {
            try {
                listIds.add( Long.parseLong( c.getId() ) );
            }
            catch ( NumberFormatException ignored ) {
                // skip
            }
        } );

        Set<Long> currentIds = new HashSet<>();
        List<Long> needEnrich = new ArrayList<>();
        for ( BookingAssignment a : dao.fetchCurrentBookingAssignments() ) {
            if ( a.getReservationId() == null || a.getReservationId() <= 0 ) {
                continue;
            }
            currentIds.add( a.getReservationId() );
            if ( a.getLastRestFetchedAt() == null ) {
                needEnrich.add( a.getReservationId() );
            }
        }

        List<Long> toFetch = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for ( Long id : needEnrich ) {
            if ( seen.add( id ) ) {
                toFetch.add( id );
            }
        }
        for ( Long id : listIds ) {
            if ( false == currentIds.contains( id ) && seen.add( id ) ) {
                toFetch.add( id );
            }
        }

        LOGGER.info( "BookingAssignment heal: list={}, currents={}, restFetch={}",
                listIds.size(), currentIds.size(), toFetch.size() );
        if ( false == toFetch.isEmpty() ) {
            fetchAndApply( webClient, toFetch );
        }

        dualWriteCalendar( jobId );
    }

    /**
     * Projects current booking assignments into {@code wp_lh_calendar} for the given job id
     * (bridge for blacklist / prepaid queries during migration).
     */
    public void dualWriteCalendar( int jobId ) {
        dao.deleteAllocations( jobId );
        List<Allocation> rows = new ArrayList<>();
        List<GuestCommentReportEntry> comments = new ArrayList<>();
        for ( BookingAssignment a : dao.fetchCurrentBookingAssignments() ) {
            Allocation alloc = toAllocation( jobId, a );
            if ( alloc != null ) {
                rows.add( alloc );
                if ( StringUtils.isNotBlank( a.getComments() ) && a.getReservationId() != null
                        && a.getReservationId() > 0 ) {
                    comments.add( new GuestCommentReportEntry(
                            a.getReservationId().intValue(), a.getComments() ) );
                }
            }
        }
        LOGGER.info( "BookingAssignment dual-write: {} calendar rows for job {}", rows.size(), jobId );
        if ( false == rows.isEmpty() ) {
            dao.insertAllocations( new AllocationList( rows ) );
        }
        if ( false == comments.isEmpty() ) {
            dao.updateGuestCommentsForReservations( comments );
        }
    }

    void applyReservationEnrich( Reservation r ) {
        if ( r == null || r.getBookingRooms() == null ) {
            return;
        }
        BigDecimal levy = EdinburghVisitorLevyCalculator.getVisitorLevyTotal( r );
        String comments = r.getSpecialRequests();
        String ratePlan = r.getUsedRoomTypes();
        Boolean viewed = StringUtils.isNotBlank( r.getDocumentNumber() );

        for ( BookingRoom br : r.getBookingRooms() ) {
            BookingAssignment fromRest = reservationRoomToAssignment( r, br );
            if ( fromRest == null ) {
                continue;
            }
            dao.upsertBookingAssignment( fromRest );
            dao.patchBookingAssignmentEnrich( fromRest.getAssignmentKey(), levy, comments, ratePlan, viewed );
        }
    }

    private BookingAssignment reservationRoomToAssignment( Reservation r, BookingRoom br ) {
        if ( br == null || StringUtils.isBlank( br.getStartDate() ) || StringUtils.isBlank( br.getEndDate() ) ) {
            return null;
        }
        BookingAssignment a = new BookingAssignment();
        String roomPart = StringUtils.defaultIfBlank( br.getRoomId(), "unassigned" );
        a.setAssignmentKey( StringUtils.defaultIfBlank( br.getId(),
                "res:" + r.getReservationId() + ":" + roomPart ) );
        a.setBookingRoomsId( br.getId() );
        try {
            a.setReservationId( Long.parseLong( r.getReservationId() ) );
        }
        catch ( NumberFormatException e ) {
            return null;
        }
        a.setSource( BookingAssignment.SOURCE_GUEST );
        a.setPaymentTotal( r.getGrandTotal() );
        a.setPaymentOutstanding( r.getBalanceDue() );
        a.setBookingSource( r.getSourceName() );
        a.setHotelCollect( r.isHotelCollectBooking() );
        a.setEmail( r.getEmail() );
        a.setGuestName( r.getFirstName() + " " + r.getLastName() );
        a.setNumberGuests( ( r.getAdultsNumber() == null ? 0 : r.getAdultsNumber() )
                + ( r.getKidsNumber() == null ? 0 : r.getKidsNumber() ) );
        a.setNotes( r.getNotesAsString() );
        a.setBookingReference( StringUtils.defaultIfBlank( r.getThirdPartyIdentifier(), r.getIdentifier() ) );
        a.setBedStatus( CloudbedsService.derivePerBedStatus( r.getStatus(), br ) );
        a.setInHouse( br.isInHouse() );
        a.setCheckinDate( java.time.LocalDate.parse( br.getStartDate() ) );
        a.setCheckoutDate( java.time.LocalDate.parse( br.getEndDate() ) );
        a.setRoomId( StringUtils.trimToNull( br.getRoomId() ) );
        BedAssignment bed = roomBedMatcher.parse( br.getRoomNumber() );
        a.setRoom( StringUtils.defaultIfBlank( bed.getRoom(),
                StringUtils.isBlank( br.getRoomId() ) ? "Unallocated" : null ) );
        a.setBedName( bed.getBedName() );
        try {
            a.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
        }
        catch ( Exception ignored ) {
            // leave null
        }
        if ( StringUtils.isNotBlank( r.getBookingDateHotelTime() ) && r.getBookingDateHotelTime().length() >= 10 ) {
            a.setBookedDate( java.time.LocalDate.parse( r.getBookingDateHotelTime().substring( 0, 10 ) ) );
        }
        a.setDataHref( "/connect/" + scraper.getPropertyId() + "#/reservations/" + r.getReservationId() );
        a.setRatePlanName( r.getUsedRoomTypes() );
        return a;
    }

    private Allocation toAllocation( int jobId, BookingAssignment a ) {
        if ( a == null ) {
            return null;
        }
        Allocation alloc = new Allocation();
        alloc.setJobId( jobId );
        alloc.setRoomId( a.getRoomId() );
        alloc.setRoom( StringUtils.defaultIfBlank( a.getRoom(), "Unallocated" ) );
        alloc.setBedName( a.getBedName() );
        alloc.setRoomTypeId( a.getRoomTypeId() == null ? 0 : a.getRoomTypeId() );
        if ( a.getReservationId() != null ) {
            alloc.setReservationId( a.getReservationId().intValue() );
        }
        alloc.setGuestName( a.getGuestName() );
        alloc.setEmail( a.getEmail() );
        alloc.setCheckinDate( a.getCheckinDate() );
        alloc.setCheckoutDate( a.getCheckoutDate() );
        alloc.setPaymentTotal( a.getPaymentTotal() );
        alloc.setPaymentOutstanding( a.getPaymentOutstanding() );
        alloc.setVisitorLevyTotal( a.getVisitorLevyTotal() );
        alloc.setRatePlanName( a.getRatePlanName() );
        alloc.setNumberGuests( a.getNumberGuests() == null ? 0 : a.getNumberGuests() );
        alloc.setDataHref( a.getDataHref() );
        alloc.setStatus( a.getBedStatus() );
        alloc.setBookingReference( a.getBookingReference() );
        alloc.setBookingSource( a.getBookingSource() );
        alloc.setHotelCollect( a.isHotelCollect() );
        alloc.setBookedDate( a.getBookedDate() );
        alloc.setNotes( a.getNotes() );
        alloc.setComments( a.getComments() );
        alloc.setViewed( a.isViewed() );
        return alloc;
    }

    private void fetchAndApply( WebClient webClient, List<Long> reservationIds ) throws IOException {
        var mdcContext = MDC.getCopyOfContextMap();
        List<Future<?>> futures = new ArrayList<>();
        for ( Long id : reservationIds ) {
            if ( id == null ) {
                continue;
            }
            futures.add( ioThreadPool.submit( () -> {
                if ( mdcContext != null ) {
                    MDC.setContextMap( mdcContext );
                }
                try {
                    enrichReservation( webClient, String.valueOf( id ) );
                }
                catch ( IOException e ) {
                    throw new RuntimeException( e );
                }
                finally {
                    MDC.clear();
                }
            } ) );
        }
        boolean hasError = false;
        int requestTimeout = Integer.parseInt( dao.getDefaultOption( "hbo_cloudbeds_request_timeout", "900" ) );
        for ( Future<?> future : futures ) {
            try {
                future.get( requestTimeout, TimeUnit.SECONDS );
            }
            catch ( TimeoutException | InterruptedException | ExecutionException e ) {
                LOGGER.error( "BookingAssignment enrich failed", e );
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
            throw new IOException( "BookingAssignment enrich failed" );
        }
    }
}
