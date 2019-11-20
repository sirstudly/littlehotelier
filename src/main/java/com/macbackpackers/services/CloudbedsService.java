package com.macbackpackers.services;

import static org.openqa.selenium.support.ui.ExpectedConditions.stalenessOf;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleContains;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.mail.MessagingException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingReport;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.SagepayTransaction;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.ChargeHostelworldLateCancellationJob;
import com.macbackpackers.jobs.PrepaidChargeJob;
import com.macbackpackers.jobs.SendTemplatedEmailJob;
import com.macbackpackers.scrapers.BookingComScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.matchers.BedAssignment;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

@Service
public class CloudbedsService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private WordPressDAO dao;
    
    @Autowired
    private CloudbedsScraper scraper;
    
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
    private GenericObjectPool<WebDriver> driverFactory;

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${hostelworld.latecancellation.hours:48}" )
    private int HWL_LATE_CANCEL_HOURS;

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
     * Finds all staff allocations for the given date. Use this to dump raw "staff" data.
     * 
     * @param webClient web client instance to use
     * @param stayDate the date we're searching on
     * @return non-null List of all Allocations (blocked/out of service)
     * @throws IOException on failure
     */
    public List<Allocation> getAllStaffAllocationsDaily( WebClient webClient, LocalDate stayDate ) throws IOException {

        JsonObject rpt = scraper.getAllStaffAllocations( webClient, stayDate );
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
        
        // if we're running this daily
        // then this would apply to all cancellations done today/yesterday
        // and checkin date would have to be between
        //     day before yesterday (earliest) - it is possible to cancel *after* the checkin date
        // and day after tomorrow (latest) - if cancel was done monday at 23:59, 
        //                             then would charge if checkin on wednesday but not thursday
        List<Reservation> cxlRes = scraper.getCancelledReservationsForBookingSources( webClient, 
                cancelDateStart.minusDays( 1 ), cancelDateEnd.plusDays( 1 ), 
                cancelDateStart, cancelDateEnd, "Hostelworld & Hostelbookers" );
        
        cxlRes.stream()
            .peek( r -> LOGGER.info( "Res #" + r.getReservationId() + " (" + r.getThirdPartyIdentifier() 
                    + ") " + r.getFirstName() + " " + r.getLastName() + " cancelled on " + r.getCancellationDate() 
                    + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() ) )
            .filter( r -> BigDecimal.ZERO.equals( r.getPaidValue() ) ) // nothing paid yet
            .filter( r -> r.isLateCancellation( HWL_LATE_CANCEL_HOURS ) )
            .filter( r -> isCancellationDoneBySystem( webClient, r.getIdentifier() ) )
            .forEach( r -> {
                LOGGER.info( "Creating ChargeHostelworldLateCancellationJob for " + r.getReservationId() );
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
                cancelDateStart, cancelDateEnd, "Hostelworld & Hostelbookers" );
        
        cxlRes.stream()
            .peek( r -> LOGGER.info( "Res #" + r.getReservationId() + " (" + r.getThirdPartyIdentifier() 
                    + ") " + r.getFirstName() + " " + r.getLastName() + " cancelled on " + r.getCancellationDate() 
                    + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() ) )
            .filter( r -> BigDecimal.ZERO.equals( r.getPaidValue() ) ) // nothing paid yet
            .filter( r -> r.isCheckinDateInAugust() )
            .filter( r -> r.isLateCancellation( 24 * CANCEL_PERIOD_DAYS - 9 ) ) // (from midnight the following day) : 13:00 to 00:00 = 9 hrs
            .filter( r -> isCancellationDoneBySystem( webClient, r.getIdentifier() ) )
            .forEach( r -> {
                LOGGER.info( "Creating ChargeHostelworldLateCancellationJob (August) for " + r.getReservationId() );
                ChargeHostelworldLateCancellationJob j = new ChargeHostelworldLateCancellationJob();
                j.setStatus( JobStatus.submitted );
                j.setReservationId( r.getReservationId() );
                dao.insertJob( j );
            } );
    }

    /**
     * Searches for all bookings between the given dates and creates a PrepaidChargeJob for all
     * virtual card bookings that can be charged immediately.
     * 
     * @param driver
     * @param wait
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @throws Exception
     */
    public void createBDCPrepaidChargeJobs( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws Exception {
        WebDriver driver = driverFactory.borrowObject();
        try {
            WebDriverWait wait = new WebDriverWait( driver, maxWaitSeconds );
            bdcScraper.getAllVCCBookingsThatCanBeCharged( driver, wait, checkinDateStart, checkinDateEnd )
                .stream().forEach( this::createPrepaidChargeJob );
        }
        finally {
            driverFactory.returnObject( driver );
        }
    }

    /**
     * Searches Cloudbeds for the given BDC reservation and creates a PrepaidChargeJob for it.
     * 
     * @param bdcReference Booking.com reference
     */
    private void createPrepaidChargeJob( String bdcReference ) {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            scraper.getReservations( webClient, bdcReference ).stream()
                    .filter( p -> p.getSourceName().contains( "Booking.com" ) ) // just in case
                    .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                    .filter( r -> r.getThirdPartyIdentifier().equals( bdcReference ) )
                    .forEach( r -> {
                        LOGGER.info( "Creating a PrepaidChargeJob for BDC-{} ({}) - {}, {} ({} to {})",
                                bdcReference, r.getReservationId(), r.getLastName(), r.getFirstName(),
                                r.getCheckinDate(), r.getCheckoutDate() );
                        PrepaidChargeJob j = new PrepaidChargeJob();
                        j.setStatus( JobStatus.submitted );
                        j.setReservationId( r.getReservationId() );
                        dao.insertJob( j );
                    } );
        }
        catch ( IOException ex ) {
            throw new RuntimeException( ex );
        }
    }

    /**
     * Creates SendTemplatedEmailJobs for the given parameters.
     * 
     * @param webClient
     * @param templateName mandatory email template
     * @param stayDateStart (optional inclusive)
     * @param stayDateEnd (optional inclusive)
     * @param checkinDateStart (optional inclusive)
     * @param checkinDateEnd (optional inclusive)
     * @param statuses (optional)
     * @throws IOException
     */
    public void createBulkEmailJob( WebClient webClient, String templateName,
            LocalDate stayDateStart, LocalDate stayDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd, String statuses ) throws IOException {

        scraper.getReservations( webClient,
                stayDateStart, stayDateEnd, checkinDateStart, checkinDateEnd, statuses ).stream()
                .map( c -> scraper.getReservationRetry( webClient, c.getId() ) )
                .filter( r -> false == "Macb Tour".equals( r.getLastName() ) )
                .filter( r -> false == "Emma Young".equals( r.getFirstName() + " " + r.getLastName() ) )
                .filter( r -> false == r.containsNote( templateName + " email sent." ) )
                .forEach( r -> {
                    LOGGER.info( "Creating SendTemplatedEmailJob for Res #" + r.getReservationId()
                    + " (" + r.getThirdPartyIdentifier() + ") " + r.getFirstName() + " " + r.getLastName()
                    + " from " + r.getCheckinDate() + " to " + r.getCheckoutDate() );
                    SendTemplatedEmailJob j = new SendTemplatedEmailJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( r.getReservationId() );
                    j.setEmailTemplate( templateName );
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
            Pattern pattern = Pattern.compile( "Reservation Status Modified from .* to Cancelled" );
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
                a.setDataHref( "/connect/" + scraper.getPropertyId() + "#/reservations/" + r.getReservationId());
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
                a.setRatePlanName( r.getUsedRoomTypes() );
                return a;
            } )
            .collect( Collectors.toList() );
    }

    /**
     * Sends an email to the guest for a successful payment.
     * 
     * @param webClient web client instance to use
     * @param res associated reservation
     * @param txn successful transaction
     * @throws IOException
     */
    public void sendSagepayPaymentConfirmationEmail( WebClient webClient, String reservationId, int sagepayTxnId ) throws IOException {

        EmailTemplateInfo template = scraper.getSagepayPaymentConfirmationEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent for txn " + sagepayTxnId;

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            SagepayTransaction txn = dao.fetchSagepayTransaction( sagepayTxnId );
            sendEmailFromTemplate( webClient, template, res, txn.getEmail(),
                b -> b.replaceAll( "\\[vendor tx code\\]", txn.getVendorTxCode() )
                    .replaceAll( "\\[payment total\\]", scraper.getCurrencyFormat().format( txn.getPaymentAmount() ) )
                    .replaceAll( "\\[card type\\]", txn.getCardType() )
                    .replaceAll( "\\[last 4 digits\\]", txn.getLastFourDigits() ) );
            scraper.addNote( webClient, reservationId, note );
        }
    }
    
    /**
     * Sends a payment confirmation email using the given template, reservation and transaction.
     * 
     * Same as {@link CloudbedsScraper#sendSagepayPaymentConfirmationEmail(WebClient, Reservation, SagepayTransaction)
     * but sent via Gmail.
     * 
     * @param webClient web client instance to use
     * @param txn successful sagepay transaction
     * @throws IOException 
     * @throws MessagingException 
     */
    public void sendSagepayPaymentConfirmationGmail( WebClient webClient, String reservationId, int sagepayTxnId ) throws IOException, MessagingException {
        EmailTemplateInfo template = scraper.getSagepayPaymentConfirmationEmailTemplate( webClient );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent for txn " + sagepayTxnId;

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            SagepayTransaction txn = dao.fetchSagepayTransaction( sagepayTxnId );
            gmailService.sendEmail( txn.getEmail(), txn.getFirstName() + " " + txn.getLastName(), template.getSubject(),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[vendor tx code\\]", txn.getVendorTxCode() )
                                    .replaceAll( "\\[payment total\\]", scraper.getCurrencyFormat().format( txn.getPaymentAmount() ) )
                                    .replaceAll( "\\[card type\\]", txn.getCardType() )
                                    .replaceAll( "\\[last 4 digits\\]", txn.getLastFourDigits() ) ) );
            scraper.addNote( webClient, res.getReservationId(), note );
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
                    b -> b.replaceAll( "\\[first night charge\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
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
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() )
                                    .replaceAll( "\\[first night charge\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
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
                    b -> b.replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
            scraper.addNote( webClient, reservationId, note );
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
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() )
                                    .replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
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
                    b -> b.replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) )
                            .replaceAll( "\\[payment URL\\]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) );
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
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() )
                                    .replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) )
                                    .replaceAll( "\\[payment URL\\]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
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
                    b -> b.replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) );
            scraper.addNote( webClient, reservationId, note );
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
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() )
                                    .replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) ) ) );
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
                    b -> b.replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) )
                            .replaceAll( "\\[payment URL\\]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) );
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
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() )
                                    .replaceAll( "\\[charge amount\\]", "£" + scraper.getCurrencyFormat().format( amount ) )
                                    .replaceAll( "\\[payment URL\\]", "<a href='" + paymentURL + "'>" + paymentURL + "</a>" ) ) );
            scraper.addNote( webClient, reservationId, note + " " + paymentURL );
        }
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
     * @throws IOException
     */
    public void sendTemplatedGmail( WebClient webClient, String reservationId, String templateName ) throws IOException, MessagingException {

        EmailTemplateInfo template = scraper.fetchEmailTemplate( webClient, templateName );
        Reservation res = scraper.getReservationRetry( webClient, reservationId );
        final String note = template.getTemplateName() + " email sent.";

        if ( res.containsNote( note ) ) {
            LOGGER.info( template.getTemplateName() + " email already sent. Doing nothing." );
        }
        else {
            gmailService.sendEmail( res.getEmail(), res.getFirstName() + " " + res.getLastName(),
                    template.getSubject().replaceAll( "\\[conf number\\]", res.getIdentifier() ),
                    IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                            .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                            .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                            .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                    .replaceAll( "\\[first name\\]", res.getFirstName() ) ) );
            scraper.addNote( webClient, reservationId, note );
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
        WebDriver driver = driverFactory.borrowObject();
        try (WebClient webClient = appContext.getBean( "webClientForCloudbedsNoValidate", WebClient.class )) {
            WebDriverWait wait = new WebDriverWait( driver, maxWaitSeconds );
            Reservation r = scraper.getReservationRetry( webClient, reservationId );

            LOGGER.info( r.getThirdPartyIdentifier() + ": " + r.getFirstName() + " " + r.getLastName() );
            LOGGER.info( "Source: " + r.getSourceName() );
            LOGGER.info( "Status: " + r.getStatus() );
            LOGGER.info( "Checkin: " + r.getCheckinDate() );
            LOGGER.info( "Checkout: " + r.getCheckoutDate() );
            LOGGER.info( "Grand Total: " + r.getGrandTotal() );
            LOGGER.info( "Balance Due: " + r.getBalanceDue() );

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
            bdcScraper.markCreditCardAsInvalid( driver, wait, r.getThirdPartyIdentifier(), r.getCreditCardLast4Digits() );

            // if we got this far, then we've updated BDC. leave a note as well...
            final String INVALID_CC_NOTE = "Marked card ending in " + r.getCreditCardLast4Digits() + " invalid on BDC.";
            if ( false == r.containsNote( INVALID_CC_NOTE ) ) {
                scraper.addNote( webClient, reservationId, INVALID_CC_NOTE );
            }
        }
        finally {
            driverFactory.returnObject( driver );
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
        final int MAX_WAIT_SECONDS = 60;
        WebDriver driver = driverFactory.borrowObject();
        try {
            WebDriverWait wait = new WebDriverWait( driver, MAX_WAIT_SECONDS );
            doLogin( webClient, driver, wait,
                    dao.getOption( "hbo_cloudbeds_username" ),
                    dao.getOption( "hbo_cloudbeds_password" ) );
        }
        catch ( Exception ex ) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            FileUtils.copyFile( scrFile, new File( "logs/cloudbeds_login_failed.png" ) );
            LOGGER.info( "Error attempting to login. Screenshot saved as cloudbeds_login_failed.png" );
        }
        finally {
            driverFactory.returnObject( driver );
        }
    }

    /**
     * Logs into Cloudbeds with the necessary credentials.
     * 
     * @param webClient used for sending 2captcha requests
     * @param driver web client to use
     * @param wait
     * @param username user credentials
     * @param password user credentials
     * @throws IOException 
     * @throws URISyntaxException 
     */
    public synchronized void doLogin( WebClient webClient, WebDriver driver, WebDriverWait wait, String username, String password ) throws IOException, URISyntaxException {

        if ( username == null || password == null ) {
            throw new MissingUserDataException( "Missing username/password" );
        }

        // we need to navigate first before loading the cookies for that domain
        driver.get( "https://hotels.cloudbeds.com/auth/login" );

        WebElement usernameField = driver.findElement( By.id( "email" ) );
        usernameField.sendKeys( username );

        WebElement passwordField = driver.findElement( By.id( "password" ) );
        passwordField.sendKeys( password );

        JavascriptExecutor jse = (JavascriptExecutor) driver;
        String token = captchaService.solveRecaptchaV2( webClient, driver.getCurrentUrl(), driver.getPageSource() );
        jse.executeScript( String.format( "var input = $('[name=visible_captcha]'); input.val('%s'); $('#login_form button').removeClass('disabled');", token ) );

        WebElement loginButton = driver.findElement( By.xpath( "//button[@type='submit']" ) );
        loginButton.click();
        wait.until( stalenessOf( loginButton ) );

        if ( driver.getCurrentUrl().startsWith( "https://hotels.cloudbeds.com/auth/awaiting_user_verification" ) ) {
            WebElement scaCode = driver.findElement( By.name( "token" ) );
            scaCode.sendKeys( fetch2FACode() );
            loginButton = driver.findElement( By.xpath( "//button[contains(text(),'Submit')]" ) );
            loginButton.click();
            wait.until( stalenessOf( loginButton ) );
        }

        if ( false == driver.getCurrentUrl().startsWith( "https://hotels.cloudbeds.com/connect" ) ) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            FileUtils.copyFile( scrFile, new File( "logs/cloudbeds_login_failed.png" ) );
            throw new UnrecoverableFault( "2FA verification failed" );
        }

        // if we're actually logged in, we should get the hostel name identified here...
        wait.until( titleContains( "Dashboard" ) );
        LOGGER.info( "Logged in title? is: " + driver.getTitle() );

        // save credentials to disk so we don't need to do this again
        dao.setOption( "hbo_cloudbeds_cookies",
                driver.manage().getCookies().stream()
                        .map( c -> c.getName() + "=" + c.getValue() )
                        .collect( Collectors.joining( ";" ) ) );
        dao.setOption( "hbo_cloudbeds_useragent",
                jse.executeScript( "return navigator.userAgent;" ).toString() );
    }

    /**
     * First _blanks out_ the 2FA code from the DB and waits for it to be re-populated. This is done
     * outside this application.
     * 
     * @return non-null 2FA code
     * @throws MissingUserDataException on timeout (1 + 10 minutes)
     */
    private String fetch2FACode() throws MissingUserDataException {
        // now blank out the code and wait for it to appear
        LOGGER.info( "waiting for hbo_cloudbeds_2facode to be set..." );
        dao.setOption( "hbo_cloudbeds_2facode", "" );
        sleep( 60 );
        // force timeout after 10 minutes (60x10 seconds)
        for ( int i = 0 ; i < 60 ; i++ ) {
            String scaCode = dao.getOption( "hbo_cloudbeds_2facode" );
            if ( StringUtils.isNotBlank( scaCode ) ) {
                return scaCode;
            }
            LOGGER.info( "waiting for another 10 seconds..." );
            sleep( 10 );
        }
        throw new MissingUserDataException( "2FA code timeout waiting for Cloudbeds verification." );
    }

    private void sleep( int seconds ) {
        try {
            Thread.sleep( seconds * 1000 );
        }
        catch ( InterruptedException e ) {
            // nothing to do
        }
    }
}
