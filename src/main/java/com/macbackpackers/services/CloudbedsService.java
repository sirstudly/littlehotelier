package com.macbackpackers.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingReport;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.bdc.BookingComRefundRequest;
import com.macbackpackers.beans.cloudbeds.requests.CustomerInfo;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Guest;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.SharedDbDAO;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.ChargeHostelworldLateCancellationJob;
import com.macbackpackers.jobs.ChargeRemainingBalanceForBookingJob;
import com.macbackpackers.jobs.CreateFixedRateReservationJob;
import com.macbackpackers.jobs.PrepaidChargeJob;
import com.macbackpackers.jobs.SendCovidPrestayEmailJob;
import com.macbackpackers.jobs.SendGmailJob;
import com.macbackpackers.jobs.SendGroupBookingApprovalRequiredEmailJob;
import com.macbackpackers.jobs.SendGroupBookingPaymentReminderEmailJob;
import com.macbackpackers.jobs.SendPaymentLinkEmailJob;
import com.macbackpackers.jobs.SendTemplatedEmailJob;
import com.macbackpackers.scrapers.BookingComScraper;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.text.StringEscapeUtils;
import org.htmlunit.Page;
import org.htmlunit.UnexpectedPage;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlPasswordInput;
import org.htmlunit.html.HtmlTextInput;
import org.htmlunit.util.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_DEPOSIT_CHARGE_DECLINED;
import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_GROUP_BOOKING_PAYMENT_REMINDER;
import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED;
import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_PAYMENT_DECLINED;
import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_PAYMENT_LINK;
import static com.macbackpackers.services.PaymentProcessorService.CHARGE_REMAINING_BALANCE_NOTE;
import static com.macbackpackers.utils.MDCUtils.wrapWithMDC;

@Service
public class CloudbedsService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private SharedDbDAO sharedDao;
    
    @Autowired
    private AuthenticationService authService;
    
    @Autowired
    private CloudbedsScraper scraper;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private BookingComScraper bdcScraper;

    @Autowired
    private CaptchaSolverService captchaService;

    @Autowired
    private GmailService gmailService;
    
    @Autowired
    private RoomBedMatcher roomBedMatcher;

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    AutowireCapableBeanFactory autowireBeanFactory;

    @Autowired
    @Qualifier("ioThreadPool")
    private ExecutorService ioThreadPool;

    @Value( "${hostelworld.latecancellation.hours:48}" )
    private int HWL_LATE_CANCEL_HOURS;

    private final DateTimeFormatter DD_MMM_YYYY = DateTimeFormatter.ofPattern( "dd-MMM-yyyy" );

    // all allowable characters for lookup key
    private static String LOOKUPKEY_CHARSET = "2345678ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static int LOOKUPKEY_LENGTH = 7;

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
        var mdcContext = MDC.getCopyOfContextMap();
        try {
            List<Allocation> allocations = Collections.synchronizedList( new ArrayList<>() );
            List<GuestCommentReportEntry> guestComments = Collections.synchronizedList( new ArrayList<>() );

            // Get reservations list for parallel processing
            List<Future<List<Allocation>>> futures = scraper.getReservations( webClient, startDate, endDate ).stream()
                    .map( customer -> ioThreadPool.submit( wrapWithMDC( mdcContext, () -> {
                        Reservation reservation = scraper.getReservationRetry( webClient, customer.getId() );
                        return reservationToAllocation( jobId, reservation );
                    } ) ) )
                    .toList();

            // Collect results - if ANY fail, the entire operation fails
            boolean hasError = false;
            for ( Future<List<Allocation>> future : futures ) {
                try {
                    // we need a fairly long timeout; cloudbeds could take a while when multiple requests are sent off at the same time
                    int requestTimeout = Integer.parseInt( dao.getDefaultOption( "hbo_cloudbeds_request_timeout", "900" ) );
                    List<Allocation> result = future.get( requestTimeout, TimeUnit.SECONDS ); // Configurable timeout per request
                    allocations.addAll( result );
                    result.stream()
                            .filter( a -> StringUtils.isNotBlank( a.getComments() ) )
                            .forEach( a -> guestComments.add( new GuestCommentReportEntry( a.getReservationId(), a.getComments() ) ) );
                }
                catch ( TimeoutException | InterruptedException | ExecutionException e ) {
                    LOGGER.error( "Error retrieving reservation", e );
                    hasError = true;
                    break; // Exit the loop on first error
                }
            }

            // If there was an error, cancel all remaining futures and throw exception
            if ( hasError ) {
                for ( Future<List<Allocation>> future : futures ) {
                    if ( !future.isDone() ) {
                        future.cancel( true ); // Cancel running tasks
                    }
                }
                throw new IOException( "Error retrieving reservation - operation rolled back" );
            }

            dao.insertAllocations( new AllocationList( allocations ) );
            dao.updateGuestCommentsForReservations( guestComments );
        }
        finally {
            // MDC lost as side-effect of calling MDCUtils.wrapWithMDC
            MDC.setContextMap( mdcContext );
        }

        // finally, add any staff allocations
        AllocationList staffAllocations = new AllocationList( getAllStaffAllocations( webClient, startDate ) );
        staffAllocations.forEach( a -> a.setJobId( jobId ) );
        LOGGER.info( "Inserting {} staff allocations.", staffAllocations.size() );
        dao.insertAllocations( staffAllocations );
    }

    /**
     * Dumps the reservations starting on the given date (inclusive).
     * 
     * @param webClient web client instance to use
     * @param jobId the job ID to associate with this dump
     * @param startDate the start date to check reservations for (inclusive)
     * @param endDate the end date to check reservations for (inclusive)
     * @throws IOException on read/write error
     */
    public void dumpBookingReportFrom( WebClient webClient, int jobId, LocalDate startDate, LocalDate endDate ) throws IOException {
        List<BookingReport> bookingReport = scraper.getReservationsByCheckinDate( webClient, startDate, endDate ).stream()
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .map( r -> new BookingReport( jobId, r ) )
                .collect( Collectors.toList() );
        dao.insertBookingReport( bookingReport );
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
        JsonObject rpt = scraper.getRoomAssignmentsReport( webClient, stayDate );

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
                        // if you get this error; probably a room/bed name was changed
                        // dump a call to getReservation() and update wp_lh_rooms with the correct room_id, room_type, bed_name
                        // or view the /connect/room_types/find_one API response under Property Details, Accommodation Types
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
     * Finds all staff allocations for the given date. Use this to dump raw "staff" data.
     * 
     * @param webClient web client instance to use
     * @param stayDate the date we're searching on
     * @return non-null List of all Allocations (blocked/out of service)
     * @throws IOException on failure
     */
    public List<Allocation> getAllStaffAllocationsDaily( WebClient webClient, LocalDate stayDate ) throws IOException {

        JsonObject rpt = scraper.getRoomAssignmentsReport( webClient, stayDate );
        List<String> staffBeds = extractStaffBedsFromRoomAssignmentReport( rpt, stayDate );

        Map<RoomBedLookup, RoomBed> roomBedMap = dao.fetchAllRoomBeds();
        Map<RoomBedLookup, Allocation> bedNameAllocations = new HashMap<>();
        staffBeds.forEach( bedname -> {
                BedAssignment bedAssign = roomBedMatcher.parse( bedname );
                RoomBedLookup lookupKey = new RoomBedLookup( bedAssign.getRoom(), bedAssign.getBedName() );
                RoomBed rb = roomBedMap.get( lookupKey );
                if ( rb == null ) {
                    throw new MissingUserDataException( "Missing mapping for " + lookupKey );
                }
                Allocation a = createAllocationFromRoomBed( rb );
                a.setCheckinDate( stayDate );
                a.setCheckoutDate( stayDate.plusDays( 1 ) );
                bedNameAllocations.put( lookupKey, a );
            } );

        return bedNameAllocations.values().stream().collect( Collectors.toList() );
    }

    /**
     * Finds all HWL cancellations between the given dates and:
     * <ul>
     *   <li>if cancellation was done by 'System'</li>
     *   <li>if first night has not been charged</li>
     *   <li>if cancellation occurs {@code cancellationWindowHours} before checkin</li>
     *   <li>then create job to charge first night with card-on-file</li>
     *   <li>(which will also) and append note (charge attempt) to reservation</li>
     *   <li>(which will also) and create separate email job to notify guest</li> 
     * </ul>
     * @param webClient
     * @param cancelDateStart cancellation date inclusive
     * @param cancelDateEnd cancellation date inclusive
     * @throws IOException
     */
    public void createChargeHostelworldLateCancellationJobs( WebClient webClient, 
            LocalDate cancelDateStart, LocalDate cancelDateEnd ) throws IOException {
        LOGGER.info( "Looking up reservations canceled between " + cancelDateStart + " to " + cancelDateEnd );

        // if we're running this daily
        // then this would apply to all cancellations done today/yesterday
        // and checkin date would have to be between
        //     day before yesterday (earliest) - it is possible to cancel *after* the checkin date
        // and day after tomorrow (latest) - if cancel was done monday at 23:59, 
        //                             then would charge if checkin on wednesday but not thursday
        List<Reservation> cxlRes = scraper.getCancelledReservationsForBookingSources( webClient, 
                cancelDateStart.minusDays( 1 ), cancelDateEnd.plusDays( 1 ), 
                cancelDateStart, cancelDateEnd, "Hostelworld & Hostelbookers", "Hostelworld (Hotel Collect Booking)" );
        
        cxlRes.stream()
            .peek( r -> LOGGER.info( "Res #" + r.getReservationId() + " (" + r.getThirdPartyIdentifier() 
                    + ") " + r.getFirstName() + " " + r.getLastName() + " cancelled on " + r.getCancellationDate() 
                    + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() ) )
            .filter( r -> BigDecimal.ZERO.equals( r.getPaidValue() ) ) // nothing paid yet
            .filter( r -> r.isLateCancellation( HWL_LATE_CANCEL_HOURS ) )
            .filter( r -> isCancellationDoneBySystem( webClient, r.getIdentifier() ) )
            .filter( r -> false == isExistsRefund( webClient, r ) )
            .forEach( r -> {
                LOGGER.info( "Creating ChargeHostelworldLateCancellationJob for " + r.getReservationId() + ": " + r.getFirstName() + " " + r.getLastName() );
                ChargeHostelworldLateCancellationJob j = new ChargeHostelworldLateCancellationJob();
                j.setStatus( JobStatus.submitted );
                j.setReservationId( r.getReservationId() );
                dao.insertJob( j );
            } );
    }

    /**
     * Finds all HWL cancellations between the given dates and:
     * <ul>
     *   <li>if cancellation was done by 'System'</li>
     *   <li>if first night has not been charged</li>
     *   <li>if cancellation occurs {@code cancellationWindowHours} before checkin</li>
     *   <li>then create job to charge first night with card-on-file</li>
     *   <li>(which will also) and append note (charge attempt) to reservation</li>
     *   <li>(which will also) and create separate email job to notify guest</li> 
     * </ul>
     * @param webClient
     * @param cancelDateStart cancellation date inclusive
     * @param cancelDateEnd cancellation date inclusive
     * @throws IOException
     */
    public void createChargeHostelworldLateCancellationJobsForAugust( WebClient webClient, 
            LocalDate cancelDateStart, LocalDate cancelDateEnd ) throws IOException {
        
        // 7 days for CRH; 14 days for RMB/HSH
        final int CANCEL_PERIOD_DAYS = dao.getOption( "siteurl" ).contains( "castlerock" ) ? 7 : 14;

        // if we're running this daily
        // then this would apply to all cancellations done today/yesterday
        // and checkin date would have to be between
        //     day before yesterday (earliest) - it is possible to cancel *after* the checkin date
        // and day after tomorrow (latest) - if cancel was done monday at 23:59, 
        //                             then would charge if checkin on wednesday but not thursday
        List<Reservation> cxlRes = scraper.getCancelledReservationsForBookingSources( webClient, 
                cancelDateStart.minusDays( 1 ), cancelDateEnd.plusDays( 1 + CANCEL_PERIOD_DAYS ), 
                cancelDateStart, cancelDateEnd, "Hostelworld & Hostelbookers", "Hostelworld (Hotel Collect Booking)" );
        
        cxlRes.stream()
            .peek( r -> LOGGER.info( "Res #" + r.getReservationId() + " (" + r.getThirdPartyIdentifier() 
                    + ") " + r.getFirstName() + " " + r.getLastName() + " cancelled on " + r.getCancellationDate() 
                    + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() ) )
            .filter( r -> BigDecimal.ZERO.equals( r.getPaidValue() ) ) // nothing paid yet
            .filter( r -> r.isCheckinDateInAugust() )
            .filter( r -> r.isLateCancellation( 24 * CANCEL_PERIOD_DAYS - 9 ) ) // (from midnight the following day) : 13:00 to 00:00 = 9 hrs
            .filter( r -> isCancellationDoneBySystem( webClient, r.getIdentifier() ) )
            .filter( r -> false == isExistsRefund( webClient, r ) )
            .forEach( r -> {
                LOGGER.info( "Creating ChargeHostelworldLateCancellationJob (August) for " + r.getReservationId() );
                ChargeHostelworldLateCancellationJob j = new ChargeHostelworldLateCancellationJob();
                j.setStatus( JobStatus.submitted );
                j.setReservationId( r.getReservationId() );
                dao.insertJob( j );
            } );
    }

    /**
     * Create ChargeHogmanayBookingJob for all bookings staying thru Dec-31 of this year.
     *
     * @param webClient
     * @throws IOException
     */
    public void createChargeHogmanayBookingJobs( WebClient webClient ) throws IOException {

        final LocalDate DEC_31 = LocalDate.of( LocalDate.now().getYear(), 12, 31 );
        scraper.getReservations( webClient, DEC_31, DEC_31, null, null, "confirmed,not_confirmed" )
                .stream()
                .filter( c -> false == c.isLongTermer() )
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .peek( r -> LOGGER.info( "Res #" + r.getReservationId() + " (" + r.getThirdPartyIdentifier() + ") "
                        + r.getFirstName() + " " + r.getLastName() + " booked from " + r.getCheckinDate() + " to " + r.getCheckoutDate()
                        + " with balance of " + r.getBalanceDue() ) )
                .filter( r -> BigDecimal.ZERO.compareTo( r.getBalanceDue() ) < 0 ) // still something to pay
                .filter( r -> false == r.isChannelCollectBooking() )
                .filter( r -> false == r.containsNote( CHARGE_REMAINING_BALANCE_NOTE ) )
                .filter( r -> r.containsNote("Hogmanay Advanced Payment email sent."))
                .forEach( r -> {
                    LOGGER.info( "Creating ChargeRemainingBalanceForBookingJob for " + r.getReservationId() );
                    ChargeRemainingBalanceForBookingJob j = new ChargeRemainingBalanceForBookingJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    dao.insertJob( j );
                } );
    }

    /**
     * Returns all reservations for BDC with virtual cards that can be charged immediately.
     * 
     * @return List<String> cloudbeds reservation ids
     * @throws Exception
     */
    public List<String> getAllVCCBookingsThatCanBeCharged_LookupViaBDC() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForBDC", WebClient.class )) {
            return bdcScraper.getAllVCCBookingsThatCanBeCharged( webClient )
                    .stream()
                    .map( bdc -> getReservationForBDC( bdc ) )
                    .filter( r -> r.isPresent() )
                    .map( r -> r.get() )
                    .peek( r -> LOGGER.info( "Found BDC reservation {} - {} with VCC for {} {}",
                            r.getThirdPartyIdentifier(), r.getReservationId(), r.getFirstName(), r.getLastName() ) )
                    .map( r -> r.getReservationId() )
                    .collect( Collectors.toList() );
        }
    }

    /**
     * Returns all reservations for BDC with virtual cards that can be charged immediately.
     *
     * @return List<String> cloudbeds reservation ids
     * @throws Exception
     */
    public Set<String> getAllVCCBookingsThatCanBeCharged() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            final LocalDate TODAY = LocalDate.now();
            // find all BDC prepaid bookings with checkins/checkouts for today
            return Stream.concat(scraper.getReservations( webClient, null, null, null, null,
                            TODAY, TODAY, null, null, "confirmed,not_confirmed,checked_in,checked_out,no_show" ).stream(),
                    scraper.getReservations( webClient, null, null, TODAY, TODAY,
                            null, null, null, null, "confirmed,not_confirmed" ).stream())
                    .filter( c -> c.getSourceName().contains( "Booking.com" ) )
                    .filter( c -> false == c.isHotelCollectBooking() )
                    .filter( c -> c.getBalanceDue().compareTo( BigDecimal.ZERO ) > 0 )
                    .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                    .peek( r -> LOGGER.info( "Found BDC reservation {} - {} with VCC for {} {}",
                            r.getThirdPartyIdentifier(), r.getReservationId(), r.getFirstName(), r.getLastName() ) )
                    .map( r -> r.getReservationId() )
                    .collect( Collectors.toSet() );
        }
    }

    /**
     * Returns all reservations for BDC with virtual cards that need to be refunded.
     *
     * @return List<BookingComRefundRequest> bdc refunds with associated cloudbeds reservation ids
     * @throws IOException
     */
    public List<BookingComRefundRequest> getAllVCCBookingsThatMustBeRefunded() throws IOException {
        try (WebClient webClient = appContext.getBean("webClientForBDC", WebClient.class)) {
            return bdcScraper.getAllVCCBookingsThatMustBeRefunded(webClient)
                    .stream()
                    .map(rr -> {
                        Optional<Reservation> r = getReservationForBDC(rr.getBookingRef());
                        if (r.isPresent()) {
                            // save the cloudbeds id
                            rr.setReservationId(r.get().getReservationId());
                            LOGGER.info("Found BDC reservation {} - {} with VCC for {} {}",
                                    r.get().getThirdPartyIdentifier(), r.get().getReservationId(),
                                    r.get().getFirstName(), r.get().getLastName());
                        }
                        else {
                            LOGGER.error("Unable to find BDC reservation {}", rr.getBookingRef());
                        }
                        return rr;
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Searches Cloudbeds for the given BDC reservation.
     * 
     * @param bdcReference Booking.com reference
     * @return matched cloudbeds reservation if found
     */
    private Optional<Reservation> getReservationForBDC( String bdcReference ) {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            return scraper.getReservations( webClient, bdcReference ).stream()
                    .filter( p -> p.getSourceName().contains( "Booking.com" ) ) // just in case
                    .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                    .filter( r -> r.getThirdPartyIdentifier().equals( bdcReference ) )
                    .findFirst();
        }
        catch ( IOException ex ) {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Cancels an existing reservation.
     * 
     * @param reservationId unique booking reference
     * @param note user note to add to reservation (optional)
     */
    public void cancelReservation( String reservationId, String note ) {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            Reservation r = scraper.getReservationRetry( webClient, reservationId );
            if ( "canceled".equals( r.getStatus() ) && note != null && false == r.containsNote( note ) ) {
                LOGGER.info( "Already canceled; adding note to booking" );
                scraper.addNote( webClient, reservationId, note );
            }
            else if ( false == "confirmed".equals( r.getStatus() ) ) {
                throw new IllegalStateException( "Reservation " + reservationId + " not in correct state to be canceled: " + r.getStatus() );
            }
            else {
                scraper.cancelBooking( webClient, reservationId );
                if ( note != null ) {
                    scraper.addNote( webClient, reservationId, note );
                }
            }
        }
        catch ( IOException ex ) {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Creates a job that sends a support email if a booking matches a name/email against the blacklist.
     *
     * @param allocationScraperJobId allocation scraper job ID
     */
    public void createEmailsForBookingsOnBlacklist( int allocationScraperJobId ) {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            String backofficeUrl = dao.getMandatoryOption("hbo_backoffice_url") + "/" + dao.getMandatoryOption("hbo_blacklist_url");
            dao.fetchBookingsMatchingBlacklist( allocationScraperJobId, sharedDao.fetchBlacklistEntries() ).stream()
                .map( a -> scraper.getReservationRetry( webClient, String.valueOf( a.getReservationId() ) ) )
                .filter( r -> false == r.containsNote( "Guest Blacklisted email sent." ) )
                .filter( r -> "confirmed".equals( r.getStatus() ) || "not_confirmed".equals( r.getStatus() ) )
                .forEach( r -> {
                    SendGmailJob j = new SendGmailJob();
                    autowireBeanFactory.autowireBean( j );
                    j.setStatus( JobStatus.submitted );
                    j.setToAddress( "me" );
                    j.setReservationId( r.getReservationId() );
                    j.setSubject( "Guest Blacklisted" );
                    j.setEmailBody( "Reservation #" + r.getIdentifier() + " for " + r.getFirstName() + " " + r.getLastName()
                            + " (" + r.getEmail() + ") matches an entry in our blacklist. See " + backofficeUrl + " for details. -RONBOT" );
                    dao.insertJob( j );
                });
        }
    }

    /**
     * Creates jobs for sending out emails by stay date, checkin date.
     * 
     * @param webClient
     * @param emailTemplate
     * @param stayDateStart (optional)
     * @param stayDateEnd (optional)
     * @param checkinDateStart (optional)
     * @param checkinDateEnd (optional)
     * @param bookingDateStart (optional)
     * @param bookingDateEnd (optional)
     * @param statuses comma-delimited list of statuses to apply for
     * @param reservationFilterFn apply filter to the given reservation (optional)
     * @param replacementFn apply any replacements for the given reservation (optional)
     * @throws IOException
     */
    public void createSendTemplatedEmailJobs( WebClient webClient, String emailTemplate, LocalDate stayDateStart, LocalDate stayDateEnd,
                                              LocalDate checkinDateStart, LocalDate checkinDateEnd,
                                              LocalDate bookingDateStart, LocalDate bookingDateEnd,
                                              String statuses, Function<Reservation, Boolean> reservationFilterFn,
                                              Function<Reservation, Map<String, String>> replacementFn ) throws IOException {
        scraper.fetchEmailTemplate( webClient, emailTemplate ); // check if it exists before creating a bunch of jobs
        scraper.getReservations( webClient, stayDateStart, stayDateEnd, checkinDateStart, checkinDateEnd, null, null,
                        bookingDateStart, bookingDateEnd, statuses ).stream()
                .filter( c -> false == c.isLongTermer() )
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> false == r.containsNote( emailTemplate + " email sent." ) )
                .filter( r -> r.getEmail().contains( "@" ) ) // Airbnb has N/A in email address field?
                .filter( r -> reservationFilterFn == null || reservationFilterFn.apply( r ) )
                .forEach( r -> {
                    LOGGER.info( "Creating SendTemplatedEmailJob for Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    SendTemplatedEmailJob j = new SendTemplatedEmailJob();
                    autowireBeanFactory.autowireBean( j );
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    j.setEmailTemplate( emailTemplate );
                    j.setNoteArchived( true );
                    if( replacementFn != null ) {
                        j.setReplacementMap( replacementFn.apply( r ) );
                    }
                    dao.insertJob( j );
                } );
    }

    /**
     * Creates charge jobs for any prepaid canceled BDC bookings over the given dates.
     * 
     * @param webClient
     * @param checkinDateStart (optional inclusive)
     * @param checkinDateEnd (optional inclusive)
     * @throws IOException
     */
    public void createCanceledPrepaidBDCBookingsChargeJobs(WebClient webClient,
            LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {

        scraper.getCancelledReservationsForBookingSources( webClient,
                checkinDateStart, checkinDateEnd, null, null,
                "Booking.com (Channel Collect Booking)" ).stream()
                .filter( r -> r.isPrepaid() )
                .filter( r -> r.getGrandTotal().equals( r.getBalanceDue() ) ) // nothing charged
                .forEach( r -> {
                    LOGGER.info( "Creating PrepaidChargeJob for Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    PrepaidChargeJob j = new PrepaidChargeJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    dao.insertJob( j );
                } );
    }

    /**
     * Creates send email jobs for all checkins on the given date.
     * 
     * @param webClient
     * @param checkinDate
     * @throws IOException
     */
    public void createSendCovidPrestayEmailJobs( WebClient webClient, LocalDate checkinDate ) throws IOException {

        scraper.getReservations( webClient, null, null, LocalDate.now(), checkinDate, "confirmed,not_confirmed" ).stream()
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> false == r.containsNote( CloudbedsScraper.TEMPLATE_COVID_PRESTAY ) )
                .forEach( r -> {
                    // AirBNB does not populate with a valid email address
                    if ( r.getEmail().contains( "@" ) ) {
                        LOGGER.info( "Creating SendCovidPrestayEmailJob for Res #" + r.getReservationId()
                                + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                                + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                        SendCovidPrestayEmailJob j = new SendCovidPrestayEmailJob();
                        j.setStatus( JobStatus.submitted );
                        j.setReservationId( r.getReservationId() );
                        dao.insertJob( j );
                    }
                    else {
                        LOGGER.error( "Invalid email on Res #" + r.getReservationId()
                                + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                                + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    }
                } );
    }

    /**
     * Creates send email jobs for all group bookings between the given booking dates.
     * 
     * @param webClient
     * @param bookingDateStart
     * @param bookingDateEnd
     * @throws IOException
     */
    public void createSendGroupBookingApprovalRequiredEmailJobs( WebClient webClient, LocalDate bookingDateStart, LocalDate bookingDateEnd ) throws IOException {

        final int GROUP_BOOKING_SIZE = dao.getGroupBookingSize();
        scraper.getReservationsByBookingDate( webClient, bookingDateStart, bookingDateEnd, "confirmed,not_confirmed" ).stream()
                // do not include any bookings that have been taken by staff
                .filter( c -> false == Arrays.asList( "Walk-In", "Phone", "Reception", "RECEPTION", "Default Travel Agent" ).contains( c.getSourceName() ) )
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> false == r.containsNote( CloudbedsScraper.TEMPLATE_GROUP_BOOKING_APPROVAL_REQUIRED ) )
                .filter( r -> false == r.containsNote( CloudbedsScraper.TEMPLATE_GROUP_BOOKING_APPROVAL_REQUIRED_PREPAID ) )
                .filter( r -> r.getEmail().contains("@")) // email not available for airbnb
                .filter( r -> r.getNumberOfGuests() >= GROUP_BOOKING_SIZE )
                .forEach( r -> {
                    LOGGER.info( "Creating SendGroupBookingApprovalRequiredEmailJob for Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() + (r.isChannelCollectBooking() ? " which is a PREPAID booking." : "") );
                    SendGroupBookingApprovalRequiredEmailJob j = new SendGroupBookingApprovalRequiredEmailJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    j.setPrepaid( r.isChannelCollectBooking() );
                    dao.insertJob( j );
                } );
    }

    /**
     * Creates send email jobs for all upcoming unpaid group bookings.
     * 
     * @param webClient
     * @param checkinDateStart
     * @param checkinDateEnd
     * @throws IOException
     */
    public void createSendGroupBookingPaymentReminderEmailJobs( WebClient webClient, LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {

        final int GROUP_BOOKING_SIZE = dao.getGroupBookingSize();
        scraper.getReservationsByCheckinDate( webClient, checkinDateStart, checkinDateEnd ).stream()
                .filter( c -> Arrays.asList( "confirmed", "not_confirmed" ).contains( c.getStatus() ) )
                .filter( c -> c.getBalanceDue().compareTo( BigDecimal.ZERO ) > 0 )
                .filter( c -> c.isHotelCollectBooking() )
                .filter( c -> false == "Airbnb (API)".equals( c.getSourceName() ) ) // Airbnb is channel-collect apparently
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> r.getNumberOfGuests() >= GROUP_BOOKING_SIZE )
                .filter( r -> false == r.containsNote( TEMPLATE_GROUP_BOOKING_PAYMENT_REMINDER ) )
                // don't send the payment reminder if they booked within the payment reminder window
                .filter( r -> r.getBookingDateAsLocalDate().compareTo( LocalDate.now().minusDays( Period.between( LocalDate.now(), checkinDateEnd ).getDays() ) ) < 0 )
                .forEach( r -> {
                    LOGGER.info( "Creating SendGroupBookingPaymentReminderEmailJob for Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() + " with amount due of £" + r.getBalanceDue() );
                    SendGroupBookingPaymentReminderEmailJob j = new SendGroupBookingPaymentReminderEmailJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    dao.insertJob( j );
                } );
    }

    /**
     * Creates new bookings for any existing bookings for LT guests due to checkout on the given
     * day.
     * 
     * @param webClient
     * @param forDate (if booking exists with a checkout on this date, create new 7-day booking in
     *            the same room if an existing booking isn't blocking it)
     * @param days number of days the new booking is for
     * @param dailyRate fixed price per day
     * @throws IOException
     */
    public void createFixedRateLongTermReservations(WebClient webClient, LocalDate forDate, int days, BigDecimal dailyRate ) throws IOException {

        // first find out the room assignments from forDate for the next week so we know of any clashes
        JsonObject rpt = scraper.getRoomAssignmentsReport( webClient, forDate, forDate.plusDays( days ) );
        Set<String> conflictBeds = new HashSet<>();
        for(LocalDate d = forDate; d.isBefore( forDate.plusDays( days ) ); d = d.plusDays( 1 )) {
            conflictBeds.addAll(rpt.get( "rooms" ).getAsJsonObject()
                .get( d.format( DateTimeFormatter.ISO_LOCAL_DATE ) ).getAsJsonObject()
                .entrySet().stream() // now streaming room types...
                .flatMap( e -> e.getValue().getAsJsonObject()
                        .get( "rooms" ).getAsJsonObject()
                        .entrySet().stream() ) // now streaming beds
                // only match beds where there has been an assignment
                .filter( e -> e.getValue().getAsJsonArray().iterator().hasNext() )
                .map( e -> e.getKey().trim() )
                .collect( Collectors.toList() ));
        }

        scraper.getReservations( webClient, forDate.minusDays( 1 ), forDate.minusDays( 1 ) ).stream()
                .filter( c -> c.isLongTermer() )
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> r.getCheckoutDateAsLocalDate().equals( forDate ) )
                .filter( r -> false == "checked_out".equals( r.getStatus() ) )
                .filter( r -> r.getBookingRooms().size() == 1 )
                .filter( r -> false == conflictBeds.contains( StringEscapeUtils.unescapeHtml4( r.getBookingRooms().get( 0 ).getRoomNumber() ).trim() ) )
                .forEach( r -> {
                    LOGGER.info( "Creating CreateFixedRateReservationJob from Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    CreateFixedRateReservationJob j = new CreateFixedRateReservationJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    j.setCheckinDate( forDate );
                    j.setCheckoutDate( forDate.plusDays( days ) );
                    j.setRatePerDay( dailyRate );
                    dao.insertJob( j );
                } );
    }

    /**
     * Creates {@link SendPaymentLinkEmailJob}s for all LT bookings with the given stay date.
     * 
     * @param webClient
     * @param stayDate
     * @throws IOException
     */
    public void createSendPaymentLinkEmailJobs(WebClient webClient, LocalDate stayDate ) throws IOException {

        scraper.getReservations( webClient, stayDate, stayDate ).stream()
                .filter( c -> c.isLongTermer() )
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> false == r.isPaid() )
                .filter( r -> false == "castlerock@macbackpackers.com".equalsIgnoreCase( r.getEmail() ) )
                .forEach( r -> {
                    LOGGER.info( "Creating new SendPaymentLinkEmailJob from Res #" + r.getReservationId()
                            + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                            + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    SendPaymentLinkEmailJob j = new SendPaymentLinkEmailJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    dao.insertJob( j );
                } );
    }

    /**
     * Archives all transaction related messages for this booking ONLY IF the booking is fully paid for (no balance owing).
     *
     * @param webClient
     * @param reservationId
     */
    public void archiveAllTransactionNotes( WebClient webClient, String reservationId ) {
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        if ( res.getBalanceDue().compareTo( BigDecimal.ZERO ) > 0 ) {
            LOGGER.info( "Outstanding balance of " + scraper.getCurrencyFormat().format( res.getBalanceDue() )
                    + " remaining. Not archiving transaction notes." );
        }
        else {
            res.getNotes()
                    .stream()
                    .filter( n -> n.getOwnerName().equals( "RON BOT" ) && false == n.isArchived() )
                    // just need to archive all those declined messages now that everything has been paid for
                    .filter( n -> n.getNotes().contains( "Failed to charge booking:" )
                            || n.getNotes().contains( TEMPLATE_PAYMENT_DECLINED + " email sent." )
                            || n.getNotes().contains( TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED + " email sent." )
                            || n.getNotes().contains( TEMPLATE_DEPOSIT_CHARGE_DECLINED + " email sent." )
                            || n.getNotes().contains( TEMPLATE_PAYMENT_LINK + " email sent." )
                            || n.getNotes().contains( TEMPLATE_GROUP_BOOKING_PAYMENT_REMINDER + " email sent." ) )
                    .forEach( n -> {
                        try {
                            LOGGER.info( "Archiving note: " + n.getNotes() );
                            scraper.archiveNote( webClient, reservationId, n.getId() );
                        }
                        catch ( IOException e ) {
                            throw new RuntimeException( e );
                        }
                    } );
        }
    }

    /**
     * Iterates through the activity log of the reservation and checks that the status was moved to
     * Cancelled by the System user (ie. indicating that guest cancelled via HWL and it wasn't done
     * manually by someone on the desk).
     * 
     * @param webClient
     * @param identifier the cloudbeds unique id
     * @return true iff most recent status move to Cancelled was done by System user
     * @throws RuntimeException (wraps IOException)
     */
    public boolean isCancellationDoneBySystem( WebClient webClient, String identifier ) {
        try {
            Pattern pattern = Pattern.compile( "Reservation status modified from .* to Canceled" );
            Optional<ActivityLogEntry> statusChange = scraper.getActivityLog( webClient, identifier ).stream()
                    .filter( p -> pattern.matcher( p.getContents() ).find() )
                    .findFirst();
            return statusChange.isPresent() && "System".equals( statusChange.get().getCreatedBy() );
        }
        catch ( IOException ex ) {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Checks if refund exists.
     * @param webClient
     * @param res
     * @return true if refund exists.
     */
    public boolean isExistsRefund( WebClient webClient, Reservation res ) {
        try {
            return scraper.isExistsRefund( webClient, res );
        }
        catch ( IOException ex ) {
            throw new RuntimeException( ex );
        }
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
                .get( stayDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) ).getAsJsonObject()
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
                a.setDataHref( "/connect/" + scraper.getPropertyId() + "#/reservations/" + r.getReservationId());
                a.setGuestName( r.getFirstName() + " " + r.getLastName() );
                a.setEmail( r.getEmail() );
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
                a.setComments( r.getSpecialRequests() );
                a.setRatePlanName( r.getUsedRoomTypes() );
                return a;
            } )
            .collect( Collectors.toList() );
    }

    /**
     * Sends a payment confirmation email for the given transaction.
     * 
     * @param webClient web client instance to use
     * @param vendorTxCode
     * @throws IOException 
     * @throws MessagingException 
     */
    public void sendStripePaymentConfirmationGmail( WebClient webClient, String vendorTxCode ) throws MessagingException, IOException {
        StripeTransaction txn = dao.fetchStripeTransaction( vendorTxCode );
        EmailTemplateInfo template = scraper.getStripePaymentConfirmationEmailTemplate( webClient );
        Reservation res = txn.getReservationId() == null ? null : scraper.getReservationRetry( webClient, txn.getReservationId() );
        final String note = template.getTemplateName() + " " + txn.getId() + " email sent.";

        if ( res != null && res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( txn.getEmail(), txn.getFirstName() + " " + txn.getLastName(), template.getSubject(),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[vendor tx code]", txn.getVendorTxCode() )
                                    .replace( "[payment total]", scraper.getCurrencyFormat().format( txn.getPaymentAmount() ) )
                                    .replace( "[card type]", StringUtils.upperCase( txn.getCardType() ) )
                                    .replace( "[last 4 digits]", txn.getLast4Digits() ) ) );
            if ( res != null ) {
                scraper.addArchivedNote( webClient, res.getReservationId(), note );
            }
        }
    }

    /**
     * Sends an email to the guest for the given reservation when they cancel a hostelworld
     * reservation and have been charged the first night.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     */
    public void sendHostelworldLateCancellationEmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException {
        EmailTemplateInfo template = scraper.getHostelworldLateCancellationEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res,
                    b -> b.replace( "[first night charge]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when they cancel a hostelworld
     * reservation and have been charged the first night.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     * @throws MessagingException
     */
    public void sendHostelworldLateCancellationGmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getHostelworldLateCancellationEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[first night charge]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the non-refundable reservation has
     * been charged successfully.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     */
    public void sendNonRefundableSuccessfulEmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException {

        EmailTemplateInfo template = scraper.getNonRefundableSuccessfulEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res,
                    b -> b.replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
            scraper.addArchivedNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the
     * non-refundable reservation has been charged successfully.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     * @throws MessagingException
     */
    public void sendNonRefundableSuccessfulGmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getNonRefundableSuccessfulEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
            scraper.addArchivedNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when an attempt to charge the
     * non-refundable reservation but was declined.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @param paymentURL the payment URL to include in the email
     * @throws IOException
     */
    public void sendNonRefundableDeclinedEmail( WebClient webClient, String reservationId, BigDecimal amount, String paymentURL ) throws IOException {

        EmailTemplateInfo template = scraper.getNonRefundableDeclinedEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res,
                    b -> b.replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) )
                            .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when an attempt to charge the
     * non-refundable reservation but was declined.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @param paymentURL the payment URL to include in the email
     * @throws IOException
     * @throws MessagingException
     */
    public void sendNonRefundableDeclinedGmail( WebClient webClient, String reservationId, BigDecimal amount, String paymentURL ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getNonRefundableDeclinedEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) )
                                    .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
            scraper.addNote( webClient, reservationId, note + " " + paymentURL );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the deposit has been charged
     * successfully.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     */
    public void sendDepositChargeSuccessfulEmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException {

        EmailTemplateInfo template = scraper.getDepositChargeSuccessfulEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res,
                    b -> b.replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
            scraper.addArchivedNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the deposit has been charged
     * successfully.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @throws IOException
     * @throws MessagingException
     */
    public void sendDepositChargeSuccessfulGmail( WebClient webClient, String reservationId, BigDecimal amount ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getDepositChargeSuccessfulEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
            scraper.addArchivedNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the refund has been processed
     * successfully.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param txnId primary key on refund table
     * @param amount amount being refunded
     * @throws IOException
     * @throws MessagingException
     */
    public void sendRefundSuccessfulGmail( WebClient webClient, String reservationId, int txnId, BigDecimal amount ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getRefundSuccessfulEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " txn " + txnId + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }
    
    /**
     * Sends an email to the guest prior to checkin.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @throws IOException
     * @throws MessagingException
     */
    public void sendCovidPrestayGmail( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        EmailTemplateInfo template = scraper.getCovidPrestayEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            String bookingURL = generateUniqueBookingURL( reservationId );
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[booking URL]", "<a href='" + bookingURL + "'>" + bookingURL + "</a>" ) ) );

            // add archived note to booking (so it doesn't show up on the calendar)
            scraper.addArchivedNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends the group booking approval required email.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @throws IOException
     * @throws MessagingException
     */
    public void sendGroupBookingApprovalRequiredGmail( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        sendGroupBookingGmail( webClient, reservationId, scraper.getGroupBookingApprovalRequiredEmailTemplate( webClient ) );
    }

    /**
     * Sends the group booking approval required email for PREPAID bookings.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @throws IOException
     * @throws MessagingException
     */
    public void sendGroupBookingApprovalRequiredPrepaidGmail( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        sendGroupBookingGmail( webClient, reservationId, scraper.getGroupBookingApprovalRequiredPrepaidEmailTemplate( webClient ) );
    }

    /**
     * Sends the group booking payment reminder email if balance is due.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @throws IOException
     * @throws MessagingException
     */
    public void sendGroupBookingPaymentReminderGmail( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        sendGroupBookingGmail( webClient, reservationId, scraper.getGroupBookingPaymentReminderEmailTemplate( webClient ) );
    }

    private void sendGroupBookingGmail( WebClient webClient, String reservationId, EmailTemplateInfo template ) throws IOException, MessagingException {
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            String lookupKey = generateUniqueBookingKey( reservationId );
            String bookingURL = dao.getBookingsURL() + lookupKey;
            String paymentURL = dao.getBookingPaymentsURL() + lookupKey;
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[booking URL]", "<a href='" + bookingURL + "'>" + bookingURL + "</a>" )
                                    .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the deposit charge was declined.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @param paymentURL URL to payment portal
     * @throws IOException
     */
    public void sendDepositChargeDeclinedEmail( WebClient webClient, String reservationId, BigDecimal amount, String paymentURL ) throws IOException {

        EmailTemplateInfo template = scraper.getDepositChargeDeclinedEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res,
                    b -> b.replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) )
                            .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) );
            scraper.addNote( webClient, reservationId, note + " " + paymentURL );
        }
    }

    /**
     * Sends an email to the guest for the given reservation when the deposit charge was declined.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param amount amount being charged
     * @param paymentURL URL to payment portal
     * @throws IOException
     * @throws MessagingException
     */
    public void sendDepositChargeDeclinedGmail( WebClient webClient, String reservationId, BigDecimal amount, String paymentURL ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.getDepositChargeDeclinedEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replace( "[first name]", res.getFirstName() )
                                    .replace( "[charge amount]", "£" + scraper.getCurrencyFormat().format( amount ) )
                                    .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
            scraper.addNote( webClient, reservationId, note + " " + paymentURL );
        }
    }

    /**
     * Sends an email to the guest with a payment URL for the booking.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @throws IOException
     * @throws MessagingException
     */
    public void sendPaymentLinkGmail( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        EmailTemplateInfo template = scraper.getPaymentLinkEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        String paymentURL = generateUniquePaymentURL( reservationId, null );
        gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                        .replace( "__IMG_ALIGN__", template.getTopImageAlign() )
                        .replace( "__IMG_SRC__", template.getTopImageSrc() )
                        .replace( "__EMAIL_CONTENT__", template.getEmailBody()
                                .replace( "[first name]", res.getFirstName() )
                                .replace( "[start date]", DD_MMM_YYYY.format( res.getCheckinDateAsLocalDate() ) )
                                .replace( "[nights]", res.getNights() )
                                .replace( "[payment URL]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
        final String note = template.getTemplateName() + " email sent.";
        scraper.addNote( webClient, reservationId, note + " " + paymentURL );
    }

    /**
     * Sends an email to the guest for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param templateName email template (mandatory)
     * @throws IOException
     */
    public void sendTemplatedEmail( WebClient webClient, String reservationId, String templateName ) throws IOException {

        EmailTemplateInfo template = scraper.fetchEmailTemplate( webClient, templateName );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            sendEmailFromTemplate( webClient, template, res, null );
            scraper.addNote( webClient, reservationId, note );
        }
    }

    /**
     * Sends an email to the guest for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId associated reservation
     * @param templateName email template (mandatory)
     * @param replaceMap string replacement map
     * @param isArchiveNote true to archive note straight away, false otherwise
     * @throws IOException
     */
    public void sendTemplatedGmail( WebClient webClient, String reservationId, String templateName,
            Map<String, String> replaceMap, boolean isArchiveNote ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.fetchEmailTemplate( webClient, templateName );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replace( "[conf number]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replace( "__IMG_ALIGN__", template.getTopImageAlign() == null ? "0" : template.getTopImageAlign() )
                            .replace( "__IMG_SRC__", template.getTopImageSrc() == null ? "about:blank" : template.getTopImageSrc() )
                            .replace( "__EMAIL_CONTENT__", template.getEmailBody() ),
                    b -> replaceMap.keySet().stream().reduce( b.replace( "[first name]", res.getFirstName() ),
                            ( str, key ) -> str.replaceAll( key, replaceMap.get( key ) ) ) );

            if ( isArchiveNote ) {
                scraper.addArchivedNote( webClient, reservationId, note );
            }
            else {
                scraper.addNote( webClient, reservationId, note );
            }
        }
    }

    /**
     * Sends an email from a booking from an email template.
     * 
     * @param webClient
     * @param template email template
     * @param res reservation to send from
     * @param emailTo email recipient (overrides reservation email)
     * @param transformBodyFn and transformation on the email body
     * @throws IOException
     */
    private synchronized void sendEmailFromTemplate( WebClient webClient, EmailTemplateInfo template, Reservation res,
            String emailTo, Function<String, String> transformBodyFn ) throws IOException {
        // synchronized because CAPTCHA can only handle one request at a time
        scraper.sendEmailFromTemplate( webClient, template, res, emailTo, transformBodyFn,
                captchaService.solveRecaptchaV3( webClient, res.getReservationId() ) );
    }    

    /**
     * Sends an email from a booking from an email template.
     * 
     * @param webClient
     * @param template email template
     * @param res reservation to send from
     * @param transformBodyFn and transformation on the email body
     * @throws IOException
     */
    private void sendEmailFromTemplate( WebClient webClient, EmailTemplateInfo template, Reservation res,
            Function<String, String> transformBodyFn ) throws IOException {
        sendEmailFromTemplate( webClient, template, res, res.getEmail(), transformBodyFn );
    }

    /**
     * Marks the latest card (on booking) invalid on booking.com.
     * 
     * @param reservationId cloudbeds unique reservation id
     * @throws Exception
     */
    public void markCreditCardInvalidOnBDC( String reservationId ) throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            Reservation r = scraper.getReservationRetry( webClient, reservationId );

            if ( false == "Booking.com".equals( r.getSourceName() ) ) {
                throw new UnrecoverableFault( "Unsupported booking source " + r.getSourceName() );
            }
            if ( r.getBalanceDue().compareTo( BigDecimal.ZERO ) <= 0 ) {
                throw new IllegalStateException( "Outstanding balance must be greater than 0. Nothing to do." );
            }
            if ( r.containsNote( "Marking card ending in " + r.getCreditCardLast4Digits() + " as invalid for reservation" ) ) {
                LOGGER.info( "Looks like we've already done this." );
                return;
            }

            try (WebClient webClientForBDC = appContext.getBean( "webClientForBDC", WebClient.class )) {
                bdcScraper.markCreditCardAsInvalid( webClientForBDC, r.getThirdPartyIdentifier(), r.getCreditCardLast4Digits() );
            }

            // if we got this far, then we've updated BDC. leave a note as well...
            final String INVALID_CC_NOTE = "Marked card ending in " + r.getCreditCardLast4Digits() + " invalid on BDC.";
            if ( false == r.containsNote( INVALID_CC_NOTE ) ) {
                scraper.addNote( webClient, reservationId, INVALID_CC_NOTE );
            }
        }
    }

    /**
     * Logs into Cloudbeds providing the necessary credentials and saves
     * the current session parameters so this app can do it's thaang.
     * 
     * @param webClient web client for 2captcha requests
     * @throws Exception 
     */
    public void loginAndSaveSession( WebClient webClient ) throws Exception {
        doLogin( webClient,
                dao.getOption( "hbo_cloudbeds_username" ),
                dao.getOption( "hbo_cloudbeds_password" ) );
    }

    /**
     * Logs into Cloudbeds with the necessary credentials.
     * 
     * @param webClient used for sending 2captcha requests
     * @param username user credentials
     * @param password user credentials
     * @throws IOException
     * @throws URISyntaxException
     */
    public synchronized void doLogin( WebClient webClient, String username, String password ) throws IOException, URISyntaxException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing username/password" );
        }

        // we need to navigate first before loading the cookies for that domain
        HtmlPage page = webClient.getPage( "https://hotels.cloudbeds.com/auth/login" );

        HtmlTextInput usernameField = page.getHtmlElementById( "email" );
        usernameField.type( username );

        HtmlPasswordInput passwordField = page.getHtmlElementById( "password" );
        passwordField.type( password );

        // no longer present? march 2022
//        String token = captchaService.solveRecaptchaV2( webClient, page.getBaseURL().toString(), page.getWebResponse().getContentAsString() );
//        HtmlHiddenInput captchaResponse = page.getFirstByXPath( "//input[@name='visible_captcha']" );
//        captchaResponse.setValueAttribute( token );

        HtmlButton loginButton = page.getFirstByXPath( "//button[@type='submit']" );
        Page responsePage = loginButton.click();
        if ( responsePage instanceof UnexpectedPage ) {
            LOGGER.error( responsePage.getWebResponse().getStatusCode() + ": " + responsePage.getWebResponse().getStatusMessage() );
            LOGGER.error( responsePage.getWebResponse().getContentAsString() );
            throw new UnrecoverableFault( "Login failure." );
        }
        page = (HtmlPage) responsePage;
        LOGGER.info( "Login clicked. Waiting for " +  webClient.waitForBackgroundJavaScript( 10000 ) + " JS processes remaining.");

        String code = null;
        if ( page.getBaseURL().getPath().startsWith( "/auth/awaiting_user_verification" ) ) {
            HtmlTextInput scaCode = page.getElementByName( "token" );
            String googleAuth2faCode = authService.fetchCloudbedsGoogleAuth2faCode();
            if ( StringUtils.isNotBlank( googleAuth2faCode ) ) {
                LOGGER.info( "Attempting TOTP verification: " + googleAuth2faCode );
                scaCode.type( googleAuth2faCode );
            }
            else {
                LOGGER.info( "Attempting SMS verification" );
                code = authService.fetchCloudbeds2FACode( webClient );
                scaCode.type( code );
            }
            loginButton = page.getFirstByXPath( "//button[contains(text(),'Submit')]" );
            page = loginButton.click();
        }

        LOGGER.info( "Loading dashboard..." );
        LOGGER.info( "Waiting for " +  webClient.waitForBackgroundJavaScript( 30000 ) + " JS processes remaining.");

        // if we're actually logged in, we should be able to get the hostel name
        Cookie hc = webClient.getCookieManager().getCookie( "hotel_name" );
        if ( hc == null ) {
            LOGGER.info( "Hostel cookie not set? Currently on " + page.getBaseURL().getPath() );
            if( code != null ) {
                LOGGER.info( "Attempting to re-enter 2FA code " + code );
                HtmlTextInput scaCode = page.getElementByName( "token" );
                scaCode.type( code );
                loginButton = page.getFirstByXPath( "//button[contains(text(),'Submit')]" );
                page = loginButton.click();
                LOGGER.info( "Loading dashboard..." );
                LOGGER.info( "Waiting for " +  webClient.waitForBackgroundJavaScript( 30000 ) + " JS processes remaining.");
                hc = webClient.getCookieManager().getCookie( "hotel_name" );
            }
            if( hc == null ) {
                LOGGER.error( "Currently on " + page.getBaseURL().getPath() );
                LOGGER.error(page.asXml());
                throw new UnrecoverableFault("Failed login. Hostel cookie not set.");
            }
        }
        LOGGER.info( "PROPERTY NAME is: " + URLDecoder.decode( hc.getValue(), "UTF-8" ) );

        // save credentials to disk so we don't need to do this again
        jsonRequestFactory.setCookies(
                webClient.getCookieManager().getCookies().stream()
                        .map( c -> c.getName() + "=" + c.getValue() )
                        .collect( Collectors.joining( ";" ) ) );
        jsonRequestFactory.setUserAgent(
                webClient.getBrowserVersion().getUserAgent() );
    }

    /**
     * Creates a new payment URL for the given reservation.
     * 
     * @param reservationId unique Cloudbeds reservation ID
     * @param paymentRequested (optional) payment requested
     * @return payment URL
     */
    public String generateUniquePaymentURL( String reservationId, BigDecimal paymentRequested ) {
        String lookupKey = generateRandomLookupKey( LOOKUPKEY_LENGTH );
        String paymentURL = dao.getBookingPaymentsURL() + lookupKey;
        dao.insertBookingLookupKey( reservationId, lookupKey, paymentRequested );
        return paymentURL;
    }

    /**
     * Creates a new booking URL for the given reservation.
     * 
     * @param reservationId unique Cloudbeds reservation ID
     * @return booking URL
     */
    public String generateUniqueBookingURL( String reservationId ) {
        String lookupKey = generateUniqueBookingKey( reservationId );
        return dao.getBookingsURL() + lookupKey;
    }

    /**
     * Creates a new unique booking key for the given reservation.
     * 
     * @param reservationId unique Cloudbeds reservation ID
     * @return booking key
     */
    public String generateUniqueBookingKey( String reservationId ) {
        String lookupKey = generateRandomLookupKey( LOOKUPKEY_LENGTH );
        dao.insertBookingLookupKey( reservationId, lookupKey, null );
        return lookupKey;
    }

    /**
     * Returns a random lookup key with the given length.
     * 
     * @param keyLength length of lookup key
     * @return string generated key
     */
    private String generateRandomLookupKey( int keyLength ) {
        StringBuffer result = new StringBuffer();
        Random r = new Random();
        for ( int i = 0 ; i < keyLength ; i++ ) {
            result.append( LOOKUPKEY_CHARSET.charAt( r.nextInt( LOOKUPKEY_CHARSET.length() ) ) );
        }
        return result.toString();
    }

    /**
     * Create a booking for the following dates and daily rate using the given reservation as a
     * template (for customer, room assignment, etc) and create a {SendPaymentLinkEmailJob} for it.
     * 
     * @param reservationId reservation to clone
     * @param checkinDate checkin date (inclusive)
     * @param checkoutDate checkout date (exclusive)
     * @param ratePerDay amount to charge per diem (or list of rates)
     * @throws IOException 
     */
    public void createFixedRateReservationAndEmailPaymentLink( String reservationId, LocalDate checkinDate, LocalDate checkoutDate, BigDecimal... ratePerDay ) throws IOException {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            int bookingId = createFixedRateReservation( webClient, reservationId, checkinDate, checkoutDate, ratePerDay );
            LOGGER.info( "Successfully added reservation #" + bookingId );
            Reservation r = scraper.getReservationRetry( webClient, String.valueOf( bookingId ) );
            if ( r.getEmail().endsWith( "@macbackpackers.com" ) ) {
                LOGGER.info( "Email for {} {} set to macbackpackers address. Payment link not sent.", r.getFirstName(), r.getLastName() );
            }
            else {
                LOGGER.info( "Creating SendPaymentLinkEmailJob for new reservation." );
                SendPaymentLinkEmailJob j = new SendPaymentLinkEmailJob();
                j.setReservationId( String.valueOf( bookingId ) );
                j.setStatus( JobStatus.submitted );
                dao.insertJob( j );
            }
        }
    }

    /**
     * Create a booking for the following dates and daily rate using the given reservation as a
     * template (for customer, room assignment, etc).
     * 
     * @param reservationId reservation to clone
     * @param checkinDate booking start date (inclusive)
     * @param checkoutDate booking end date (exclusive)
     * @param ratesPerDay amount to charge per diem (or list of rates for all dates)
     * @return newly created reservation id
     * @throws IOException 
     */
    public int createFixedRateReservation( WebClient webClient, String reservationId, LocalDate checkinDate, LocalDate checkoutDate, BigDecimal... ratesPerDay ) throws IOException {
        Reservation r = scraper.getReservationRetry( webClient, reservationId );
        Guest guest = scraper.getGuestById( webClient, r.getCustomerId() );
        long nights = ChronoUnit.DAYS.between( checkinDate, checkoutDate );
        BigDecimal total = null;
        if ( nights > 1 && ratesPerDay.length == 1 ) {
            total = ratesPerDay[0].multiply( new BigDecimal( nights ) );
        }
        else if ( nights > 1 && ratesPerDay.length == nights ) {
            total = Arrays.asList( ratesPerDay ).stream().reduce( BigDecimal.ZERO, BigDecimal::add );
        }
        else {
            throw new MissingUserDataException( "Unsupported operation on createFixedRateReservation(): nights "
                    + nights + " and rates " + ToStringBuilder.reflectionToString( ratesPerDay ) );
        }
        final DecimalFormat NUMBER_FORMAT = new DecimalFormat( "###0.##" );
        if ( false == r.getSelectedSource().isActive() ) {
            throw new MissingUserDataException( "Booking source " + r.getSelectedSource().getSubSource() + " is not active!" );
        }
        if ( r.getBookingRooms().size() != 1 ) {
            throw new MissingUserDataException( "Duplicating bookings with more than 1 bed assignment is not supported!" );
        }
        String newReservationData = IOUtils.toString( CloudbedsService.class.getClassLoader()
                .getResourceAsStream( "add_reservation_data.json" ), StandardCharsets.UTF_8 )
                .replace( "__SOURCE_ID__", r.getSelectedSource().getId() )
                .replace( "__SOURCE_NAME__", Objects.toString( r.getSelectedSource().getName(), r.getSelectedSource().getSubSource() ) )
                .replace( "__IS_ROOT_SOURCE__", r.getIsRootSource() )
                .replace( "__ORIGINAL_SOURCE_ID__", String.valueOf( r.getSelectedSource().getOriginalId() ) )
                .replace( "__SOURCE_PARENT_ID__", String.valueOf( r.getSelectedSource().getParentId() ) )
                .replace( "__IS_HOTEL_COLLECT_BOOKING__", r.getIsHotelCollectBooking() )
                .replace( "__ROOM_TYPE_NAME__", r.getBookingRooms().get( 0 ).getRoomTypeNameShort() )
                .replace( "__ROOM_TYPE_NAME_LONG__", r.getBookingRooms().get( 0 ).getRoomTypeName() )
                .replace( "__START_DATE__", checkinDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) )
                .replace( "__END_DATE__", checkoutDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) )
                .replace( "__MAX_GUESTS__", "1" )
                .replace( "__RATE_ID__", r.getBookingRooms().get( 0 ).getRateId() )
                .replace( "__ROOM_TYPE_ID__", r.getBookingRooms().get( 0 ).getRoomTypeId() )
                .replace( "__ROOM_ID__", r.getBookingRooms().get( 0 ).getRoomId() )
                .replace( "__DETAILED_RATES__", gson.toJson( getDetailedRates( checkinDate, checkoutDate, ratesPerDay ) ) )
                .replace( "__TOTAL__", NUMBER_FORMAT.format( total ) )
                .replace( "__NIGHTS__", String.valueOf( nights ) )
                .replace( "__RATE_PER_DAY__", NUMBER_FORMAT.format( ratesPerDay[0] ) )
                .replace( "__ROOM_TYPE_ABBREV__", r.getBookingRooms().get( 0 ).getRoomTypeNameShort() )
                .replace( "__PROPERTY_ID__", scraper.getPropertyId() )
                .replace( "__CUSTOMER_INFO__", gson.toJson( new CustomerInfo( guest ) ) );
        LOGGER.info( "creating new reservation: " + newReservationData );
        JsonObject jobject = scraper.addReservation( webClient, newReservationData );
        return jobject.get( "booking_id" ).getAsInt();
    }

    private JsonObject[] getDetailedRates( LocalDate startDate, LocalDate endDate, BigDecimal... ratesPerDay ) {
        List<JsonObject> result = new ArrayList<>();
        int i = 0;
        for ( LocalDate cursor = startDate; cursor.isBefore( endDate ); cursor = cursor.plusDays( 1 ), i++ ) {
            JsonObject e = new JsonObject();
            e.addProperty( "date", cursor.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
            e.addProperty( "rate", ratesPerDay.length == 1 ? ratesPerDay[0].floatValue() : ratesPerDay[i].floatValue() );
            e.addProperty( "adults", 0 );
            e.addProperty( "kids", 0 );
            result.add( e );
        }
        return result.toArray( new JsonObject[0] );
    }
}
