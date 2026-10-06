package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

/**
 * REST enrichment and heal for {@link BookingAssignment}: folio fields WS cannot supply,
 * plus the guest comments upsert for the guest comments report.
 */
@Service
public class BookingAssignmentEnrichService {

    private static final Logger LOGGER = LoggerFactory.getLogger( BookingAssignmentEnrichService.class );

    private static final String STATUS_CANCELED = "canceled";

    private static final String STATUS_NO_SHOW = "no_show";

    private static final String STATUS_CONFIRMED = "confirmed";

    private static final String STATUS_CHECKED_IN = "checked_in";

    private static final String STATUS_CHECKED_OUT = "checked_out";

    private static final long ROOMS_CACHE_MILLIS = TimeUnit.MINUTES.toMillis( 10 );

    private volatile Map<String, RoomBed> roomsByIdCache;

    private volatile long roomsByIdLoadedAt;

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
     * Heal: one light reservation list for [start, end], compared against current assignments.
     * REST-fetches only reservations that are missing, never enriched, changed in the list
     * (status / dates / balance / total), starting on or before {@code refreshEnd}, or that
     * have in-window currents but are absent from the list (REST decides whether they are gone).
     * Reservations the list reports as canceled are closed directly.
     *
     * @param refreshEnd stays starting on or before this date are always REST-refreshed; null to only
     *            fetch reservations that are missing, never enriched, changed or absent from the list
     */
    public void heal( WebClient webClient, LocalDate startDate, LocalDate endDate,
            LocalDate refreshEnd ) throws IOException {
        Map<Long, Customer> listById = new HashMap<>();
        for ( Customer c : scraper.getReservations( webClient, startDate, endDate ) ) {
            try {
                listById.put( Long.parseLong( c.getId() ), c );
            }
            catch ( NumberFormatException ignored ) {
                // skip
            }
        }

        Map<Long, List<BookingAssignment>> currentsByRes = new HashMap<>();
        for ( BookingAssignment a : dao.fetchCurrentBookingAssignmentsCheckingOutAfter( startDate.minusDays( 1 ) ) ) {
            if ( a.getReservationId() == null || a.getReservationId() <= 0 || a.isClosure() ) {
                continue;
            }
            currentsByRes.computeIfAbsent( a.getReservationId(), k -> new ArrayList<>() ).add( a );
        }

        HealPlan plan = planHeal( listById, currentsByRes, startDate, endDate, refreshEnd );

        for ( Long id : plan.toClose ) {
            dao.closeBookingAssignmentsForReservation( id );
        }
        LOGGER.info( "BookingAssignment heal: list={}, currentReservations={}, missing={}, notEnriched={}, "
                + "changed={}, nearTerm={}, absentFromList={}, closedCanceled={}, restFetch={}",
                listById.size(), currentsByRes.size(), plan.missing, plan.notEnriched, plan.changed,
                plan.nearTerm, plan.absentFromList, plan.toClose.size(), plan.toFetch.size() );
        if ( false == plan.toFetch.isEmpty() ) {
            fetchAndApply( webClient, new ArrayList<>( plan.toFetch ) );
        }
    }

    /** Decides which reservations heal must REST-fetch or close. */
    static HealPlan planHeal( Map<Long, Customer> listById, Map<Long, List<BookingAssignment>> currentsByRes,
            LocalDate startDate, LocalDate endDate, LocalDate refreshEnd ) {
        HealPlan plan = new HealPlan();

        for ( Map.Entry<Long, List<BookingAssignment>> e : currentsByRes.entrySet() ) {
            if ( e.getValue().stream().anyMatch( a -> a.getLastRestFetchedAt() == null ) ) {
                plan.toFetch.add( e.getKey() );
                plan.notEnriched++;
            }
        }

        for ( Map.Entry<Long, Customer> e : listById.entrySet() ) {
            Long id = e.getKey();
            Customer c = e.getValue();
            List<BookingAssignment> rows = currentsByRes.get( id );
            if ( STATUS_CANCELED.equalsIgnoreCase( c.getStatus() ) ) {
                if ( rows != null ) {
                    plan.toClose.add( id );
                    plan.toFetch.remove( id );
                }
                continue;
            }
            if ( plan.toFetch.contains( id ) ) {
                continue;
            }
            if ( rows == null ) {
                plan.toFetch.add( id );
                plan.missing++;
            }
            else if ( listDiffers( c, rows ) ) {
                plan.toFetch.add( id );
                plan.changed++;
            }
            else {
                LocalDate checkin = parseDate( c.getCheckinDate() );
                if ( refreshEnd != null && checkin != null && false == checkin.isAfter( refreshEnd ) ) {
                    plan.toFetch.add( id );
                    plan.nearTerm++;
                }
            }
        }

        // an empty list is more likely a failed call than an empty hostel; don't treat everything as gone
        if ( false == listById.isEmpty() ) {
            for ( Map.Entry<Long, List<BookingAssignment>> e : currentsByRes.entrySet() ) {
                Long id = e.getKey();
                if ( listById.containsKey( id ) || plan.toFetch.contains( id ) ) {
                    continue;
                }
                boolean inWindow = e.getValue().stream().anyMatch( a -> a.getCheckoutLocalDate() != null
                        && a.getCheckoutLocalDate().isAfter( startDate )
                        && a.getCheckinLocalDate() != null
                        && false == a.getCheckinLocalDate().isAfter( endDate ) );
                if ( inWindow ) {
                    plan.toFetch.add( id );
                    plan.absentFromList++;
                }
            }
        }
        return plan;
    }

    /** True when list status, stay span, balance or grand total disagree with the current rows. */
    static boolean listDiffers( Customer c, List<BookingAssignment> rows ) {
        LocalDate minCheckin = null;
        LocalDate maxCheckout = null;
        Set<String> statuses = new HashSet<>();
        for ( BookingAssignment a : rows ) {
            LocalDate in = a.getCheckinLocalDate();
            LocalDate out = a.getCheckoutLocalDate();
            if ( in != null && ( minCheckin == null || in.isBefore( minCheckin ) ) ) {
                minCheckin = in;
            }
            if ( out != null && ( maxCheckout == null || out.isAfter( maxCheckout ) ) ) {
                maxCheckout = out;
            }
            if ( a.getBedStatus() != null ) {
                statuses.add( a.getBedStatus().toLowerCase() );
            }
        }
        LocalDate listIn = parseDate( c.getCheckinDate() );
        LocalDate listOut = parseDate( c.getCheckoutDate() );
        if ( listIn != null && false == listIn.equals( minCheckin ) ) {
            return true;
        }
        if ( listOut != null && false == listOut.equals( maxCheckout ) ) {
            return true;
        }
        if ( StringUtils.isNotBlank( c.getStatus() ) && false == statuses.contains( c.getStatus().toLowerCase() ) ) {
            return true;
        }
        BookingAssignment first = rows.get( 0 );
        if ( moneyDiffers( c.getBalanceDue(), first.getPaymentOutstanding() ) ) {
            return true;
        }
        return moneyDiffers( parseMoney( c.getGrandTotal() ), first.getPaymentTotal() );
    }

    private static boolean moneyDiffers( BigDecimal listValue, BigDecimal current ) {
        if ( listValue == null ) {
            return false;
        }
        return current == null || listValue.compareTo( current ) != 0;
    }

    private static BigDecimal parseMoney( String value ) {
        if ( StringUtils.isBlank( value ) ) {
            return null;
        }
        try {
            return new BigDecimal( value.trim() );
        }
        catch ( NumberFormatException e ) {
            return null;
        }
    }

    private static LocalDate parseDate( String value ) {
        if ( StringUtils.isBlank( value ) || value.trim().length() < 10 ) {
            return null;
        }
        try {
            return LocalDate.parse( value.trim().substring( 0, 10 ) );
        }
        catch ( DateTimeParseException e ) {
            return null;
        }
    }

    /** Outcome of {@link #planHeal}. */
    static class HealPlan {
        final Set<Long> toFetch = new LinkedHashSet<>();
        final Set<Long> toClose = new LinkedHashSet<>();
        int missing;
        int notEnriched;
        int changed;
        int nearTerm;
        int absentFromList;
    }

    private Map<String, RoomBed> getRoomsById() {
        Map<String, RoomBed> cached = roomsByIdCache;
        if ( cached == null || System.currentTimeMillis() - roomsByIdLoadedAt > ROOMS_CACHE_MILLIS ) {
            Map<String, RoomBed> map = new HashMap<>();
            for ( RoomBed rb : dao.fetchAllRoomBeds().values() ) {
                map.put( rb.getId(), rb );
            }
            roomsByIdCache = map;
            roomsByIdLoadedAt = System.currentTimeMillis();
            cached = map;
        }
        return cached;
    }

    /**
     * Upserts the comments of current guest assignments checking out today or later into the
     * guest comments report table.
     */
    public void refreshGuestComments() {
        List<GuestCommentReportEntry> comments = new ArrayList<>();
        for ( BookingAssignment a : dao.fetchCurrentBookingAssignmentsCheckingOutAfter( LocalDate.now().minusDays( 1 ) ) ) {
            if ( StringUtils.isNotBlank( a.getComments() ) && a.getReservationId() != null
                    && a.getReservationId() > 0 ) {
                comments.add( new GuestCommentReportEntry( a.getReservationId().intValue(), a.getComments() ) );
            }
        }
        LOGGER.info( "BookingAssignment guest comments: {} reservation(s)", comments.size() );
        if ( false == comments.isEmpty() ) {
            dao.updateGuestCommentsForReservations( comments );
        }
    }

    void applyReservationEnrich( Reservation r ) {
        if ( r == null || r.getBookingRooms() == null ) {
            return;
        }
        long reservationId;
        try {
            reservationId = Long.parseLong( r.getReservationId() );
        }
        catch ( NumberFormatException e ) {
            return;
        }

        Map<String, BookingAssignment> currentsByKey = new HashMap<>();
        for ( BookingAssignment cur : dao.fetchCurrentBookingAssignmentsForReservation( reservationId ) ) {
            currentsByKey.put( cur.getAssignmentKey(), cur );
        }

        Set<String> liveKeys = new HashSet<>();
        for ( BookingAssignment fromRest : buildAssignments( r ) ) {
            // WS drops canceled tiles; do the same so canceled rows never linger as currents
            if ( STATUS_CANCELED.equalsIgnoreCase( fromRest.getBedStatus() ) ) {
                dao.closeBookingAssignment( fromRest.getAssignmentKey() );
                continue;
            }
            liveKeys.add( fromRest.getAssignmentKey() );
            keepCalendarBedStatus( fromRest, currentsByKey.get( fromRest.getAssignmentKey() ) );
            dao.upsertBookingAssignment( fromRest );
            dao.patchBookingAssignmentFolio( fromRest.getAssignmentKey(), fromRest );
        }

        // booking rooms removed from the reservation (or superseded res:… fallback keys)
        for ( BookingAssignment cur : currentsByKey.values() ) {
            if ( false == liveKeys.contains( cur.getAssignmentKey() ) ) {
                dao.closeBookingAssignment( cur.getAssignmentKey() );
            }
        }
    }

    /**
     * REST only has a per-bed {@code in_house} flag, so a bed checked out early (or a reservation
     * whose status lags) comes back as {@code confirmed} / {@code checked_in}. The calendar (WS)
     * status is per bed; don't let REST move a bed backwards from it.
     */
    static void keepCalendarBedStatus( BookingAssignment fromRest, BookingAssignment current ) {
        if ( current == null ) {
            return;
        }
        String cur = current.getBedStatus();
        String rest = fromRest.getBedStatus();
        boolean backwards = ( STATUS_CHECKED_OUT.equals( cur )
                && ( STATUS_CONFIRMED.equals( rest ) || STATUS_CHECKED_IN.equals( rest ) ) )
                || ( STATUS_CHECKED_IN.equals( cur ) && STATUS_CONFIRMED.equals( rest ) );
        if ( backwards ) {
            fromRest.setBedStatus( cur );
            fromRest.setInHouseYn( current.getInHouseYn() );
        }
    }

    /**
     * One assignment per booking room of a REST reservation, including REST-only folio fields
     * (EVL total, special requests, viewed). Canceled booking rooms are included.
     */
    List<BookingAssignment> buildAssignments( Reservation r ) {
        List<BookingAssignment> rows = new ArrayList<>();
        if ( r == null || r.getBookingRooms() == null ) {
            return rows;
        }
        BigDecimal levy = EdinburghVisitorLevyCalculator.getVisitorLevyTotal( r );
        String comments = r.getSpecialRequests();
        Boolean viewed = StringUtils.isNotBlank( r.getDocumentNumber() );
        Map<String, RoomBed> roomsById = getRoomsById();
        for ( BookingRoom br : r.getBookingRooms() ) {
            BookingAssignment a = reservationRoomToAssignment( r, br, roomsById );
            if ( a == null ) {
                continue;
            }
            a.setVisitorLevyTotal( levy );
            a.setComments( comments );
            a.setViewed( viewed );
            rows.add( a );
        }
        return rows;
    }

    /**
     * Historical rows for a past stay: see {@link #applyBackfillRules}.
     */
    public List<BookingAssignment> buildBackfillAssignments( Reservation r ) {
        List<BookingAssignment> rows = buildAssignments( r );
        applyBackfillRules( r, rows, new Timestamp( System.currentTimeMillis() ) );
        return rows;
    }

    /**
     * Turns REST-built rows into historical SCD2 rows: personal data nulled, {@code valid_from} at the
     * booking date, closed at check-in when the reservation (or booking room) was canceled or a no-show,
     * and stamped as REST-fetched so enrich/heal leave them alone.
     */
    static void applyBackfillRules( Reservation r, List<BookingAssignment> rows, Timestamp fetchedAt ) {
        boolean closedReservation = STATUS_CANCELED.equalsIgnoreCase( r.getStatus() )
                || STATUS_NO_SHOW.equalsIgnoreCase( r.getStatus() );
        for ( BookingAssignment a : rows ) {
            a.setGuestName( null );
            a.setEmail( null );
            a.setNotes( null );
            a.setComments( null );
            a.setLastRestFetchedAt( fetchedAt );

            Timestamp checkin = Timestamp.valueOf( a.getCheckinLocalDate().atStartOfDay() );
            Timestamp validFrom = a.getBookedDate() == null ? checkin
                    : Timestamp.valueOf( toLocalDate( a.getBookedDate() ).atStartOfDay() );
            a.setValidFrom( validFrom );
            if ( closedReservation || STATUS_CANCELED.equalsIgnoreCase( a.getBedStatus() ) ) {
                a.setValidTo( checkin.before( validFrom ) ? validFrom : checkin );
            }
            else {
                a.setValidTo( null );
            }
        }
    }

    private static LocalDate toLocalDate( java.util.Date d ) {
        if ( d instanceof java.sql.Date ) {
            return ( (java.sql.Date) d ).toLocalDate();
        }
        return d.toInstant().atZone( java.time.ZoneId.of( "GMT" ) ).toLocalDate();
    }

    private BookingAssignment reservationRoomToAssignment( Reservation r, BookingRoom br,
            Map<String, RoomBed> roomsById ) {
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
        a.setGuestName( ( StringUtils.trimToEmpty( r.getFirstName() ) + " "
                + StringUtils.trimToEmpty( r.getLastName() ) ).trim() );
        a.setNumberGuests( ( r.getAdultsNumber() == null ? 0 : r.getAdultsNumber() )
                + ( r.getKidsNumber() == null ? 0 : r.getKidsNumber() ) );
        a.setNotes( r.getNotesAsString() );
        a.setBookingReference( StringUtils.defaultIfBlank( r.getThirdPartyIdentifier(), r.getIdentifier() ) );
        a.setReservationIdentifier( StringUtils.trimToNull( r.getIdentifier() ) );
        a.setBedStatus( CloudbedsService.derivePerBedStatus( r.getStatus(), br ) );
        a.setInHouse( br.isInHouse() );
        a.setCheckinDate( java.time.LocalDate.parse( br.getStartDate() ) );
        a.setCheckoutDate( java.time.LocalDate.parse( br.getEndDate() ) );
        a.setRoomId( StringUtils.trimToNull( br.getRoomId() ) );
        try {
            a.setRoomTypeId( Integer.parseInt( br.getRoomTypeId() ) );
        }
        catch ( Exception ignored ) {
            // leave null
        }
        // same room/bed resolution as the WS mapper so the two writers agree on placement
        RoomBed rb = a.getRoomId() == null ? null : roomsById.get( a.getRoomId() );
        if ( a.getRoomId() == null ) {
            a.setRoom( "Unallocated" );
        }
        else if ( rb != null ) {
            a.setRoom( rb.getRoom() );
            a.setBedName( rb.getBedName() );
            a.setRoomTypeId( rb.getRoomTypeId() );
        }
        else {
            BedAssignment bed = roomBedMatcher.parse( br.getRoomNumber() );
            a.setRoom( bed.getRoom() );
            a.setBedName( bed.getBedName() );
        }
        if ( StringUtils.isNotBlank( r.getBookingDateHotelTime() ) && r.getBookingDateHotelTime().length() >= 10 ) {
            a.setBookedDate( java.time.LocalDate.parse( r.getBookingDateHotelTime().substring( 0, 10 ) ) );
        }
        a.setDataHref( "/connect/" + scraper.getPropertyId() + "#/reservations/" + r.getReservationId() );
        a.setRatePlanName( r.getUsedRoomTypes() );
        return a;
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
