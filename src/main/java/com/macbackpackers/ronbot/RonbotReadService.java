package com.macbackpackers.ronbot;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.BookingNote;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.ronbot.dto.AvailabilityDto;
import com.macbackpackers.ronbot.dto.BookingSummaryDto;
import com.macbackpackers.ronbot.dto.BookingTimelineDto;
import com.macbackpackers.ronbot.dto.ContinuingRoomDto;
import com.macbackpackers.ronbot.dto.JobHistoryDto;
import com.macbackpackers.ronbot.dto.RoomTypeAvailabilityDto;
import com.macbackpackers.ronbot.dto.StayContinuationDto;
import com.macbackpackers.ronbot.dto.TransactionDto;
import com.macbackpackers.scrapers.CloudbedsScraper;

@Profile( "ronbot-parent" )
@Service
public class RonbotReadService {

    private static final Logger LOGGER = LoggerFactory.getLogger( RonbotReadService.class );

    /** Cap full reservation loads when search returns many fuzzy hits (e.g. common names). */
    private static final int MAX_SEARCH_RESULTS = 20;

    private final PropertyContextRegistry propertyContexts;

    public RonbotReadService( PropertyContextRegistry propertyContexts ) {
        this.propertyContexts = propertyContexts;
    }

    /**
     * Search reservations by visible identifier, third-party/OTA ref, guest name,
     * or internal reservation id.
     * <p>
     * Name-like queries hit the local allocation calendar first (fast), then load each
     * match via Cloudbeds {@code get_reservation} by id. Cloudbeds free-text search is
     * only used when the calendar has no hits (or for identifier-like queries).
     *
     * @return non-empty list of booking summaries; exact id/identifier/third-party matches preferred
     */
    public List<BookingSummaryDto> searchBookings( String property, String query ) throws IOException {
        String q = StringUtils.trimToNull( query );
        if ( q == null ) {
            throw new IllegalArgumentException( "query is required" );
        }

        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );
        WordPressDAO dao = ctx.getBean( WordPressDAO.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            if ( !looksLikeReservationIdentifier( q ) ) {
                List<BookingSummaryDto> fromDb = searchBookingsViaCalendar( property, q, scraper, dao, webClient );
                if ( !fromDb.isEmpty() ) {
                    return fromDb;
                }
                LOGGER.info( "Calendar miss for name-like query={}; falling back to Cloudbeds search", q );
            }

            return searchBookingsViaCloudbeds( property, q, scraper, webClient );
        }
    }

    /**
     * Resolve a unique reservation for timeline/transactions. Prefers exact id/identifier/third-party
     * matches; fails if search is ambiguous.
     * <p>
     * For name-like queries: calendar DB first, then Cloudbeds by id. Identifier-like queries
     * try direct {@code get_reservation}, then Cloudbeds search.
     */
    public String requireUniqueReservationId( String property, String query ) throws IOException {
        String q = StringUtils.trimToNull( query );
        if ( q == null ) {
            throw new IllegalArgumentException( "query is required" );
        }

        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );
        WordPressDAO dao = ctx.getBean( WordPressDAO.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            if ( !looksLikeReservationIdentifier( q ) ) {
                List<String> calendarIds = dao.searchReservationIdsInLatestCalendar( q, MAX_SEARCH_RESULTS );
                if ( calendarIds.size() == 1 ) {
                    LOGGER.info( "Resolved query={} via calendar db id={}", q, calendarIds.get( 0 ) );
                    return calendarIds.get( 0 );
                }
                if ( calendarIds.size() > 1 ) {
                    throw new IllegalArgumentException(
                            "Ambiguous query=" + q + ": " + calendarIds.size()
                                    + " calendar matches. Use get_booking to pick one." );
                }
                LOGGER.info( "Calendar miss for name-like query={}; falling back to Cloudbeds", q );
            }
            else {
                try {
                    Reservation direct = scraper.getReservation( webClient, q );
                    if ( direct != null && StringUtils.isNotBlank( direct.getReservationId() ) ) {
                        LOGGER.info( "Resolved query={} via direct get_reservation id={}", q, direct.getReservationId() );
                        return direct.getReservationId();
                    }
                }
                catch ( Exception ex ) {
                    LOGGER.debug( "Direct get_reservation failed for query={}; falling back to search", q, ex );
                }
            }

            LOGGER.info( "Resolving unique reservation via Cloudbeds search for query={}", q );
            List<Customer> matches = scraper.getReservations( webClient, q );
            if ( matches.isEmpty() ) {
                throw new MissingUserDataException( "No reservation found for query=" + q );
            }

            List<Customer> exact = matches.stream()
                    .filter( c -> isExactMatch( q, c ) )
                    .collect( Collectors.toList() );

            if ( exact.size() == 1 ) {
                return exact.get( 0 ).getId();
            }
            if ( exact.size() > 1 ) {
                throw new IllegalArgumentException(
                        "Ambiguous query=" + q + ": " + exact.size()
                                + " exact matches. Use get_booking to pick one." );
            }
            if ( matches.size() == 1 ) {
                return matches.get( 0 ).getId();
            }
            throw new IllegalArgumentException(
                    "Ambiguous query=" + q + ": " + matches.size()
                            + " matches. Use get_booking to pick one." );
        }
    }

    /**
     * True when {@code query} looks like a Cloudbeds / OTA reservation id or ref
     * (compact token containing a digit), not a guest name.
     */
    static boolean looksLikeReservationIdentifier( String query ) {
        String q = StringUtils.trimToEmpty( query );
        if ( q.isEmpty() || q.indexOf( ' ' ) >= 0 || q.indexOf( '\t' ) >= 0 ) {
            return false;
        }
        // Single-token names ("Smith") stay on the DB-first path; ids/refs almost always contain a digit.
        for ( int i = 0; i < q.length(); i++ ) {
            if ( Character.isDigit( q.charAt( i ) ) ) {
                return true;
            }
        }
        return false;
    }

    private List<BookingSummaryDto> searchBookingsViaCalendar( String property, String q,
            CloudbedsScraper scraper, WordPressDAO dao, WebClient webClient ) {
        List<String> reservationIds = dao.searchReservationIdsInLatestCalendar( q, MAX_SEARCH_RESULTS );
        if ( reservationIds.isEmpty() ) {
            return Collections.emptyList();
        }

        LOGGER.info( "Resolved query={} via calendar db ({} id(s))", q, reservationIds.size() );
        List<BookingSummaryDto> results = new ArrayList<>( reservationIds.size() );
        for ( String reservationId : reservationIds ) {
            try {
                Reservation r = scraper.getReservation( webClient, reservationId );
                if ( r != null && StringUtils.isNotBlank( r.getReservationId() ) ) {
                    results.add( toBookingSummary( property, r ) );
                }
            }
            catch ( Exception ex ) {
                LOGGER.warn( "get_reservation failed for calendar hit id={}: {}", reservationId, ex.toString() );
            }
        }
        return results;
    }

    private List<BookingSummaryDto> searchBookingsViaCloudbeds( String property, String q,
            CloudbedsScraper scraper, WebClient webClient ) throws IOException {
        LOGGER.info( "Searching Cloudbeds reservations for query={}", q );
        List<Customer> matches = scraper.getReservations( webClient, q );
        if ( matches.isEmpty() ) {
            throw new MissingUserDataException( "No reservation found for query=" + q );
        }

        List<Customer> exact = matches.stream()
                .filter( c -> isExactMatch( q, c ) )
                .collect( Collectors.toList() );
        List<Customer> toSummarize = exact.isEmpty() ? matches : exact;
        if ( toSummarize.size() > MAX_SEARCH_RESULTS ) {
            LOGGER.info( "Truncating search results from {} to {}", toSummarize.size(), MAX_SEARCH_RESULTS );
            toSummarize = toSummarize.subList( 0, MAX_SEARCH_RESULTS );
        }

        // Map search rows only — do not call get_reservation per hit.
        // Cursor's MCP tools/call client times out at a hard ~60s; full loads
        // for name searches routinely exceed that. Use get_booking_timeline
        // (or list_transactions) with reservationId for folio/notes/rooms.
        List<BookingSummaryDto> results = new ArrayList<>( toSummarize.size() );
        for ( Customer c : toSummarize ) {
            results.add( toBookingSummary( property, c ) );
        }
        return results;
    }

    public List<TransactionDto> listTransactions( String property, String query ) throws IOException {
        String reservationId = requireUniqueReservationId( property, query );
        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            List<TransactionRecord> records = scraper.getTransactionsByReservation( webClient, reservationId );
            return records.stream().map( this::toTransactionDto ).collect( Collectors.toList() );
        }
    }

    public BookingTimelineDto getTimeline( String property, String query ) throws IOException {
        String reservationId = requireUniqueReservationId( property, query );
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

    /**
     * Live sellable availability by room type for an inclusive date range.
     *
     * @param property property code
     * @param fromRaw optional YYYY-MM-DD (default today)
     * @param toRaw optional YYYY-MM-DD (default today+1)
     */
    public AvailabilityDto getAvailability( String property, String fromRaw, String toRaw ) throws IOException {
        LocalDate from = parseDateOrDefault( fromRaw, LocalDate.now() );
        LocalDate to = parseDateOrDefault( toRaw, LocalDate.now().plusDays( 1 ) );
        if ( to.isBefore( from ) ) {
            throw new IllegalArgumentException( "to must be on or after from" );
        }
        long nights = ChronoUnit.DAYS.between( from, to ) + 1;
        if ( nights > 14 ) {
            throw new IllegalArgumentException( "Date range too long (max 14 nights inclusive); got " + nights );
        }

        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            LOGGER.info( "Fetching availability for property={} from={} to={}", property, from, to );
            JsonObject findResp = scraper.fetchRoomTypesFind( webClient );
            List<RoomTypeMeta> metas = parseRoomTypeMetas( findResp ).stream()
                    .filter( m -> !isExcludedFromAvailability( property, m.name ) )
                    .collect( Collectors.toList() );
            List<String> roomTypeIds = metas.stream().map( m -> m.id ).collect( Collectors.toList() );

            Map<String, Map<LocalDate, Integer>> availability = scraper.fetchAvailability(
                    webClient, from, to, roomTypeIds );

            AvailabilityDto dto = new AvailabilityDto();
            dto.setProperty( property );
            dto.setFrom( from.toString() );
            dto.setTo( to.toString() );

            Map<String, Integer> totalsByDate = new LinkedHashMap<>();
            for ( LocalDate night = from; !night.isAfter( to ); night = night.plusDays( 1 ) ) {
                totalsByDate.put( night.toString(), 0 );
            }

            List<RoomTypeAvailabilityDto> roomTypes = new ArrayList<>();
            for ( RoomTypeMeta meta : metas ) {
                RoomTypeAvailabilityDto rt = new RoomTypeAvailabilityDto();
                rt.setRoomTypeId( meta.id );
                rt.setName( meta.name );
                rt.setCapacity( meta.capacity );
                rt.setIsPrivate( meta.isPrivate );

                Map<LocalDate, Integer> byDate = availability.getOrDefault( meta.id, Map.of() );
                Map<String, Integer> availableByDate = new LinkedHashMap<>();
                for ( LocalDate night = from; !night.isAfter( to ); night = night.plusDays( 1 ) ) {
                    int sell = byDate.getOrDefault( night, 0 );
                    availableByDate.put( night.toString(), sell );
                    totalsByDate.merge( night.toString(), sell, Integer::sum );
                }
                rt.setAvailableByDate( availableByDate );
                roomTypes.add( rt );
            }

            roomTypes.sort( Comparator.comparing(
                    r -> StringUtils.defaultString( r.getName() ), String.CASE_INSENSITIVE_ORDER ) );
            dto.setRoomTypes( roomTypes );
            dto.setTotalsByDate( totalsByDate );
            return dto;
        }
    }

    /**
     * Whether the guest is staying on past {@code asOf} — either the same reservation’s checkout
     * moved, or a follow-on booking occupies the same beds starting on checkout night.
     *
     * @param asOfRaw optional YYYY-MM-DD (default today)
     */
    public StayContinuationDto getStayContinuation( String property, String query, String asOfRaw )
            throws IOException {
        LocalDate asOf = parseDateOrDefault( asOfRaw, LocalDate.now() );
        String reservationId = requireUniqueReservationId( property, query );

        ConfigurableApplicationContext ctx = propertyContexts.require( property );
        CloudbedsScraper scraper = ctx.getBean( CloudbedsScraper.class );

        try ( WebClient webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            Reservation reservation = scraper.getReservationRetry( webClient, reservationId );
            LocalDate checkout = reservation.getCheckoutDateAsLocalDate();

            StayContinuationDto dto = new StayContinuationDto();
            dto.setAsOf( asOf.toString() );
            dto.setCheckoutDate( reservation.getCheckoutDate() );
            dto.setBooking( toBookingSummary( property, reservation ) );

            List<ContinuingRoomDto> rooms = new ArrayList<>();
            List<BookingSummaryDto> following = new ArrayList<>();

            if ( checkout.isAfter( asOf ) ) {
                dto.setKind( "same_reservation" );
                dto.setStayingOn( true );
                for ( BookingRoom room : safeRooms( reservation ) ) {
                    LocalDate end = roomEndDate( room, checkout );
                    ContinuingRoomDto cr = baseContinuingRoom( room );
                    if ( end.isAfter( asOf ) ) {
                        cr.setContinues( true );
                        cr.setReason( "same_reservation" );
                    }
                    else {
                        cr.setContinues( false );
                        cr.setReason( "ends_on_or_before_asOf" );
                    }
                    rooms.add( cr );
                }
            }
            else if ( checkout.equals( asOf ) ) {
                JsonObject report = scraper.getRoomAssignmentsReport( webClient, checkout );
                Map<String, List<RoomAssignmentHit>> byBed = indexAssignmentsByBed( report, checkout );
                Map<String, Reservation> loadedFollowOns = new LinkedHashMap<>();

                for ( BookingRoom room : safeRooms( reservation ) ) {
                    ContinuingRoomDto cr = baseContinuingRoom( room );
                    String bedKey = normalizeBedLabel( room.getRoomNumber() );
                    List<RoomAssignmentHit> hits = byBed.getOrDefault( bedKey, List.of() );
                    RoomAssignmentHit other = hits.stream()
                            .filter( h -> !reservationId.equals( h.bookingId ) )
                            .findFirst()
                            .orElse( null );

                    if ( other == null ) {
                        cr.setContinues( false );
                        cr.setReason( "vacant" );
                        rooms.add( cr );
                        continue;
                    }

                    Reservation followOn = loadedFollowOns.get( other.bookingId );
                    if ( followOn == null ) {
                        followOn = scraper.getReservationRetry( webClient, other.bookingId );
                        loadedFollowOns.put( other.bookingId, followOn );
                    }

                    if ( followOn.isCanceledOrNoShow() ) {
                        cr.setContinues( false );
                        cr.setReason( "inactive_follow_on" );
                        rooms.add( cr );
                        continue;
                    }

                    if ( isSameGuest( reservation, followOn ) ) {
                        cr.setContinues( true );
                        cr.setReason( "linked_reservation" );
                        cr.setFollowingReservationId( followOn.getReservationId() );
                        rooms.add( cr );
                    }
                    else {
                        cr.setContinues( false );
                        cr.setReason( "different_guest" );
                        rooms.add( cr );
                    }
                }

                boolean anyContinue = rooms.stream().anyMatch( ContinuingRoomDto::isContinues );
                dto.setStayingOn( anyContinue );
                dto.setKind( anyContinue ? "linked_reservation" : "none" );

                Set<String> seenFollowIds = new LinkedHashSet<>();
                for ( ContinuingRoomDto cr : rooms ) {
                    if ( cr.isContinues() && StringUtils.isNotBlank( cr.getFollowingReservationId() )
                            && seenFollowIds.add( cr.getFollowingReservationId() ) ) {
                        Reservation fo = loadedFollowOns.get( cr.getFollowingReservationId() );
                        if ( fo != null ) {
                            following.add( toBookingSummary( property, fo ) );
                        }
                    }
                }
            }
            else {
                dto.setKind( "none" );
                dto.setStayingOn( false );
                for ( BookingRoom room : safeRooms( reservation ) ) {
                    ContinuingRoomDto cr = baseContinuingRoom( room );
                    cr.setContinues( false );
                    cr.setReason( "already_checked_out" );
                    rooms.add( cr );
                }
            }

            dto.setContinuingRooms( rooms );
            dto.setFollowingBookings( following );
            return dto;
        }
    }

    private static List<BookingRoom> safeRooms( Reservation reservation ) {
        return reservation.getBookingRooms() != null ? reservation.getBookingRooms() : List.of();
    }

    private static ContinuingRoomDto baseContinuingRoom( BookingRoom room ) {
        ContinuingRoomDto cr = new ContinuingRoomDto();
        cr.setRoomId( room.getRoomId() );
        cr.setRoomNumber( room.getRoomNumber() );
        cr.setRoomTypeName( room.getRoomTypeName() );
        return cr;
    }

    private static LocalDate roomEndDate( BookingRoom room, LocalDate reservationCheckout ) {
        String end = StringUtils.trimToNull( room.getEndDate() );
        if ( end == null ) {
            return reservationCheckout;
        }
        try {
            return LocalDate.parse( end );
        }
        catch ( DateTimeParseException ex ) {
            return reservationCheckout;
        }
    }

    /** HTML-unescape + trim; matches LT conflict-bed matching. */
    static String normalizeBedLabel( String roomNumber ) {
        if ( roomNumber == null ) {
            return "";
        }
        return StringEscapeUtils.unescapeHtml4( roomNumber ).trim();
    }

    /**
     * Same guest when customerIds match, or (fallback) same normalized name and matching emails
     * when both emails are present.
     */
    static boolean isSameGuest( Reservation a, Reservation b ) {
        String aCust = StringUtils.trimToNull( a.getCustomerId() );
        String bCust = StringUtils.trimToNull( b.getCustomerId() );
        if ( aCust != null && bCust != null ) {
            return aCust.equals( bCust );
        }
        String aName = normalizeGuestName( a.getFirstName(), a.getLastName() );
        String bName = normalizeGuestName( b.getFirstName(), b.getLastName() );
        if ( aName.isEmpty() || !aName.equals( bName ) ) {
            return false;
        }
        String aEmail = StringUtils.trimToNull( a.getEmail() );
        String bEmail = StringUtils.trimToNull( b.getEmail() );
        if ( aEmail != null && bEmail != null ) {
            return aEmail.equalsIgnoreCase( bEmail );
        }
        // Names match and at least one side lacks email — treat as same guest
        return true;
    }

    static String normalizeGuestName( String firstName, String lastName ) {
        return ( StringUtils.defaultString( firstName ) + " " + StringUtils.defaultString( lastName ) )
                .trim()
                .replaceAll( "\\s+", " " )
                .toLowerCase( Locale.ROOT );
    }

    /**
     * Index room-assignments report hits by normalized bed label for a single night.
     */
    static Map<String, List<RoomAssignmentHit>> indexAssignmentsByBed( JsonObject report, LocalDate night ) {
        Map<String, List<RoomAssignmentHit>> byBed = new LinkedHashMap<>();
        if ( report == null ) {
            return byBed;
        }
        JsonElement roomsEl = report.get( "rooms" );
        if ( roomsEl == null || !roomsEl.isJsonObject() ) {
            return byBed;
        }
        JsonObject roomsByDate = roomsEl.getAsJsonObject();
        JsonElement dayEl = roomsByDate.get( night.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
        if ( dayEl == null || !dayEl.isJsonObject() ) {
            return byBed;
        }
        for ( Map.Entry<String, JsonElement> roomTypeEntry : dayEl.getAsJsonObject().entrySet() ) {
            if ( !roomTypeEntry.getValue().isJsonObject() ) {
                continue;
            }
            JsonObject roomTypeObj = roomTypeEntry.getValue().getAsJsonObject();
            JsonElement bedsEl = roomTypeObj.get( "rooms" );
            if ( bedsEl == null || !bedsEl.isJsonObject() ) {
                continue;
            }
            for ( Map.Entry<String, JsonElement> bedEntry : bedsEl.getAsJsonObject().entrySet() ) {
                String bedKey = normalizeBedLabel( bedEntry.getKey() );
                if ( bedKey.isEmpty() || !bedEntry.getValue().isJsonArray() ) {
                    continue;
                }
                JsonArray arr = bedEntry.getValue().getAsJsonArray();
                for ( JsonElement hitEl : arr ) {
                    if ( !hitEl.isJsonObject() ) {
                        continue;
                    }
                    JsonObject hit = hitEl.getAsJsonObject();
                    String bookingId = jsonString( hit, "booking_id" );
                    if ( bookingId.isEmpty() ) {
                        continue;
                    }
                    byBed.computeIfAbsent( bedKey, k -> new ArrayList<>() )
                            .add( new RoomAssignmentHit(
                                    bookingId,
                                    jsonString( hit, "guest_first_name" ),
                                    jsonString( hit, "guest_last_name" ) ) );
                }
            }
        }
        return byBed;
    }

    /** Package-visible for unit tests. */
    static final class RoomAssignmentHit {
        final String bookingId;
        final String guestFirstName;
        final String guestLastName;

        RoomAssignmentHit( String bookingId, String guestFirstName, String guestLastName ) {
            this.bookingId = bookingId;
            this.guestFirstName = guestFirstName;
            this.guestLastName = guestLastName;
        }
    }

    private static LocalDate parseDateOrDefault( String raw, LocalDate fallback ) {
        String trimmed = StringUtils.trimToNull( raw );
        if ( trimmed == null ) {
            return fallback;
        }
        try {
            return LocalDate.parse( trimmed );
        }
        catch ( DateTimeParseException ex ) {
            throw new IllegalArgumentException( "Invalid date '" + trimmed + "' (expected YYYY-MM-DD)" );
        }
    }

    /**
     * Internal Cloudbeds placeholders that must not appear in staff availability
     * (neither room-type rows nor nightly totals).
     */
    static boolean isExcludedFromAvailability( String property, String roomTypeName ) {
        if ( StringUtils.isBlank( roomTypeName ) ) {
            return false;
        }
        String name = roomTypeName.trim();
        String upper = name.toUpperCase( Locale.ROOT );
        // "PAID BED", "PAID BEDS", "PAID BED - NOT FOR SALE", etc.
        if ( upper.contains( "PAID BED" ) ) {
            return true;
        }
        if ( upper.equals( "SPLITS" ) ) {
            return true;
        }
        // Castle Rock only — offline / internal private
        if ( "crh".equalsIgnoreCase( property ) && upper.equals( "ROOM 52" ) ) {
            return true;
        }
        return false;
    }

    private static List<RoomTypeMeta> parseRoomTypeMetas( JsonObject findResp ) {
        List<RoomTypeMeta> out = new ArrayList<>();
        if ( findResp == null ) {
            return out;
        }
        JsonElement dataEl = findResp.get( "data" );
        if ( dataEl == null || !dataEl.isJsonArray() ) {
            return out;
        }
        JsonArray data = dataEl.getAsJsonArray();
        for ( JsonElement el : data ) {
            if ( !el.isJsonObject() ) {
                continue;
            }
            JsonObject row = el.getAsJsonObject();
            String id = jsonString( row, "id" );
            if ( id.isEmpty() ) {
                continue;
            }
            // Skip virtual / allotment-only types if flagged
            if ( "1".equals( jsonString( row, "is_virtual" ) ) || "Y".equalsIgnoreCase( jsonString( row, "is_virtual" ) ) ) {
                continue;
            }
            String title = jsonString( row, "title" );
            if ( title.isEmpty() ) {
                title = jsonString( row, "short_title" );
            }
            if ( title.isEmpty() ) {
                title = id;
            }
            int capacity = parsePositiveInt( row, "num_beds" );
            if ( capacity <= 0 ) {
                capacity = parsePositiveInt( row, "room_capacity" );
            }
            boolean isPrivate = "Y".equalsIgnoreCase( jsonString( row, "is_private" ) );
            out.add( new RoomTypeMeta( id, title, capacity > 0 ? capacity : null, isPrivate ) );
        }
        return out;
    }

    private static String jsonString( JsonObject obj, String key ) {
        JsonElement e = obj.get( key );
        if ( e == null || e.isJsonNull() ) {
            return "";
        }
        return e.getAsString().trim();
    }

    private static int parsePositiveInt( JsonObject obj, String key ) {
        JsonElement e = obj.get( key );
        if ( e == null || e.isJsonNull() ) {
            return 0;
        }
        try {
            if ( e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ) {
                return e.getAsInt();
            }
            return Integer.parseInt( e.getAsString().trim() );
        }
        catch ( NumberFormatException ex ) {
            return 0;
        }
    }

    private static final class RoomTypeMeta {
        final String id;
        final String name;
        final Integer capacity;
        final boolean isPrivate;

        RoomTypeMeta( String id, String name, Integer capacity, boolean isPrivate ) {
            this.id = id;
            this.name = name;
            this.capacity = capacity;
            this.isPrivate = isPrivate;
        }
    }

    static boolean isExactMatch( String query, Customer c ) {
        return query.equalsIgnoreCase( StringUtils.trimToEmpty( c.getId() ) )
                || query.equalsIgnoreCase( StringUtils.trimToEmpty( c.getIdentifier() ) )
                || query.equalsIgnoreCase( StringUtils.trimToEmpty( c.getThirdPartyIdentifier() ) );
    }

    /**
     * Lightweight summary from Cloudbeds search list rows (no get_reservation).
     * Enough for staff to pick a match; folio/EVL/notes need timeline tools.
     */
    private BookingSummaryDto toBookingSummary( String property, Customer c ) {
        BookingSummaryDto dto = new BookingSummaryDto();
        dto.setProperty( property );
        dto.setReservationId( c.getId() );
        dto.setIdentifier( c.getIdentifier() );
        dto.setThirdPartyIdentifier( c.getThirdPartyIdentifier() );
        dto.setStatus( c.getStatus() );
        dto.setFirstName( c.getFirstName() );
        dto.setLastName( c.getLastName() );
        dto.setEmail( c.getEmail() );
        dto.setSourceName( c.getSourceName() );
        dto.setCheckinDate( c.getCheckinDate() );
        dto.setCheckoutDate( c.getCheckoutDate() );
        dto.setNights( c.getNights() );
        dto.setBalanceDue( c.getBalanceDue() );
        dto.setIsHotelCollectBooking( c.getIsHotelCollectBooking() );
        dto.setBookingDateHotelTime( c.getBookingDate() );
        if ( StringUtils.isNotBlank( c.getGrandTotal() ) ) {
            try {
                dto.setGrandTotal( new BigDecimal( c.getGrandTotal().trim() ) );
            }
            catch ( NumberFormatException ignored ) {
                // leave null
            }
        }
        return dto;
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
        dto.setChannelPriceListed( r.getChannelPriceListed() );
        dto.setChannelBalance( r.getChannelBalance() );
        dto.setVisitorLevyTotal( r.getVisitorLevyTotal() );
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
                rm.put( "roomTypeId", room.getRoomTypeId() );
                rm.put( "startDate", room.getStartDate() );
                rm.put( "endDate", room.getEndDate() );
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
