package com.macbackpackers.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.BookingByCheckinDate;
import com.macbackpackers.beans.GuestCommentReportEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.beans.RoomBedLookup;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.HousekeepingJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;

/**
 * This class currently errors out with: Could not resolve placeholder 'sm@db_username_crh' in value "${sm@db_username_crh}" <-- "${db.username}"
 * This is because we need to register AnyByteStringToStringConverter into the test framework before anything else.
 * Haven't figured out a way to cleanly fix this.
 */
@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
@ActiveProfiles( "crh" )
public class WordPressDAOTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    WordPressDAO dao;

    @Autowired
    TestHarnessDAO testDAO;

    @Autowired
    SharedDbDAO sharedDao;

    static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    @BeforeEach
    public void setUp() throws Exception {
        //testDAO.deleteAllTransactionalData(); // clear out data
    }

    @Test
    public void testInsertAllocation() throws Exception {

        Allocation alloc = createNewAllocation();

        dao.insertAllocation( alloc );
        assertTrue( alloc.getId() > 0, "ID not assigned" );

        Allocation allocView = dao.fetchAllocation( alloc.getId() );
        assertEquals( alloc.getEta(), allocView.getEta(), "ETA" );
        assertEquals( alloc.isViewed(), allocView.isViewed(), "viewed" );
    }

    @Test
    public void testInsertAllocationList() throws Exception {

        AllocationList al = new AllocationList();
        al.add( createNewAllocation() );
        Allocation alloc = createNewAllocation();
        alloc.setJobId( 2 );
        alloc.setRoom( "new room2" );
        al.add( alloc );

        dao.insertAllocations( al );
    }

    private Allocation createNewAllocation() throws ParseException {
        Allocation alloc = new Allocation();
        alloc.setJobId( 3 );
        alloc.setRoomId( "15" );
        alloc.setRoom( "the room" );
        alloc.setBedName( "the bed" );
        alloc.setReservationId( 92415 );
        alloc.setGuestName( "elizabeth" );
        alloc.setCheckinDate( LocalDate.parse( "2014-04-21" ) );
        alloc.setCheckoutDate( LocalDate.parse( "2014-05-03" ) );
        alloc.setPaymentTotal( "14.93" );
        alloc.setPaymentOutstanding( "3.99" );
        alloc.setRatePlanName( "super discounted" );
        alloc.setPaymentStatus( "deposit paid" );
        alloc.setNumberGuests( 4 );
        alloc.setDataHref( "http://www.google.com" );
        alloc.setStatus( "confirmed" );
        alloc.setBookingReference( "LH-853213853" );
        alloc.setBookingSource( "hostelworld" );
        alloc.setBookedDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-20" ) );
        alloc.setEta( "10:00" );
        alloc.setNotes( "Multi-line\nnotes" );
        alloc.setViewed( true );
        alloc.setCreatedDate( new Timestamp( System.currentTimeMillis() ) );
        return alloc;
    }

    @Test
    public void testUpdateAllocation() throws Exception {

        // first we need an allocation to load
        Allocation alloc = insertTestAllocation( 3, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), "HW" );

        // load it
        Allocation allocView = dao.fetchAllocation( alloc.getId() );

        // update it
        allocView.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-26" ) );
        allocView.setBedName( "Updated bed name" );
        allocView.setNotes( "Updated notes" );
        dao.updateAllocation( allocView );

        // view it again
        allocView = dao.fetchAllocation( alloc.getId() );
        assertEquals( "2014-04-26", DATE_FORMAT_YYYY_MM_DD.format( allocView.getCheckinDate() ), "checkin date" );
        assertEquals( "Updated bed name", allocView.getBedName(), "bed name" );
        assertEquals( "Updated notes", allocView.getNotes(), "notes" );
    }

    @Test
    public void testUpdateGuestCommentsForReservations() throws Exception {
        List<GuestCommentReportEntry> comments = new ArrayList<>();
        comments.add( new GuestCommentReportEntry( 12345678, "Test Comment" ) );
        comments.add( new GuestCommentReportEntry( 12345679, "Test Comment 2" ) );
        comments.add( new GuestCommentReportEntry( 12345680, "Test Comment 3" ) );
        dao.updateGuestCommentsForReservations( comments );
    }

    @Test
    public void testInsertJob() throws Exception {
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        int jobId = dao.insertJob( j );

        assertTrue( jobId > 0 );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        assertEquals( AllocationScraperJob.class, jobView.getClass() );
        assertEquals( jobId, jobView.getId() );
        assertEquals( j.getStatus(), jobView.getStatus() );
        assertNotNull( jobView.getCreatedDate(), "create date not found" );
        assertNotNull( jobView.getLastUpdatedDate(), "last updated date not null" );
        assertEquals( j.getParameter( "start_date" ), jobView.getParameter( "start_date" ), "start_date" );
        assertEquals( j.getParameter( "end_date" ), jobView.getParameter( "end_date" ), "end_date" );
    }

    @Test
    public void testCreateAllocationScraperJob() throws Exception {
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        int jobId = dao.insertJob( j );

        assertTrue( jobId > 0, "Job id not updated: " + jobId );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        assertEquals( AllocationScraperJob.class, jobView.getClass() );
        assertEquals( jobId, jobView.getId() );
        assertEquals( j.getStatus(), jobView.getStatus() );
        assertNotNull( jobView.getCreatedDate(), "create date not found" );
        assertNotNull( jobView.getLastUpdatedDate(), "last updated date not null" );
        assertEquals( j.getParameter( "start_date" ), jobView.getParameter( "start_date" ), "start_date" );
        assertEquals( j.getParameter( "end_date" ), jobView.getParameter( "end_date" ), "end_date" );
    }

    @Test
    public void testfetchJobById() throws Exception {
        Job j1 = new AllocationScraperJob();
        j1.setStatus( JobStatus.submitted );
        int jobId1 = dao.insertJob( j1 );
        assertTrue( jobId1 > 0 );

        Job jobView1 = dao.fetchJobById( jobId1 );
        assertEquals( AllocationScraperJob.class, jobView1.getClass() );
        assertEquals( jobId1, jobView1.getId() );

        // create an identical job to the first
        Job j2 = new AllocationScraperJob();
        j2.setStatus( JobStatus.submitted );
        int jobId2 = dao.insertJob( j2 );
        assertTrue( jobId2 > 0 );
        assertNotEquals( jobId1, jobId2 );

        Job jobView2 = dao.fetchJobById( jobId2 );
        assertEquals( AllocationScraperJob.class, jobView1.getClass() );
        assertEquals( jobId2, jobView2.getId() );

        // verify we actually have different objects
        assertNotEquals( jobView1.getId(), jobView2.getId() );
    }

    @Test
    public void testGetNextJobToProcess() throws Exception {

        Job j = new HousekeepingJob();
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        LOGGER.info( "created job " + jobId );
        assertTrue( jobId > 0 );

        // create a job that isn't complete
        Job j2 = new AllocationScraperJob();
        j2.setStatus( JobStatus.completed );
        int jobId2 = dao.insertJob( j2 );
        LOGGER.info( "created job " + jobId2 );

        // now verify the results
        // returns the first job created
        Job jobView = dao.getNextJobToProcess();

        assertEquals( HousekeepingJob.class, jobView.getClass() );
        assertEquals( jobId, jobView.getId() );
        assertEquals( JobStatus.processing, jobView.getStatus() );
        assertNotNull( jobView.getCreatedDate(), "create date not found" );
        assertNotNull( jobView.getLastUpdatedDate(), "last updated date not null" );
    }

    @Test
    public void testGetNextJobToProcess2() throws Exception {
        Job nextJob = dao.getNextJobToProcess();
        LOGGER.info( ToStringBuilder.reflectionToString( nextJob ) );
    }

    @Test
    public void testFetchAllRoomBeds() throws Exception {
        Map<RoomBedLookup, RoomBed> roomBeds = dao.fetchAllRoomBeds();
        roomBeds.forEach( ( x, y ) ->
                LOGGER.info( x + " -> " + y.getRoom() + ": " + y.getBedName() ) );
        LOGGER.info( "Listed " + roomBeds.size() + " entries." );
    }

    @Test
    public void testUpdateJobStatus() throws Exception {
        HousekeepingJob j = new HousekeepingJob();
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        // execute
        dao.updateJobStatus( jobId, JobStatus.processing, JobStatus.submitted );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        assertEquals( HousekeepingJob.class, jobView.getClass() );
        assertEquals( jobId, jobView.getId() );
        assertEquals( JobStatus.processing, jobView.getStatus() );
        assertNotNull( jobView.getCreatedDate(), "create date not found" );
        assertNotNull( jobView.getLastUpdatedDate(), "last updated date not found" );
    }

    @Test
    public void testGetHostelworldHostelBookersUnpaidDepositReservations() throws Exception {

        // setup
        insertTestAllocation( 3, 10, "Hostelworld", "12.01", "12.01" );
        insertTestAllocation( 2, 11, "Hostleworld", "12.01", "12.01" );
        insertTestAllocation( 3, 12, "Hostleworld", "12.01", "4.99" );
        insertTestAllocation( 1, 13, "Hostelbookers", "12.02", "12.02" );
        insertTestAllocation( 3, 14, "Hostelbookers", "10.00", "10.00" );
        insertTestAllocation( 3, 15, "Hostelbookers", "10.00", "5.00" );
        insertTestAllocation( 3, 16, "Expedia", "22.22", "22.22" );

        // execute
        List<BookingByCheckinDate> reservations = dao.getHostelworldHostelBookersUnpaidDepositReservations( 3 );
        reservations.stream().forEach( p ->
                LOGGER.info( ToStringBuilder.reflectionToString( p ) ) );

        assertEquals( 2, reservations.size(), "size" );
    }

    @Test
    public void testQueryAllocationsByJobIdAndReservationId() throws Exception {

        // setup
        insertTestAllocation( 3, 10, "guest A" );
        insertTestAllocation( 2, 11, "guest B" );
        insertTestAllocation( 3, 10, "guest C" );
        insertTestAllocation( 3, 14, "guest D" );
        insertTestAllocation( 3, 10, "guest E" );
        insertTestAllocation( 3, 16, "guest F" );

        // execute
        assertEquals( 0, dao.queryAllocationsByJobIdAndReservationId( 2, 10 ).size(), "empty list" );
        assertEquals( 0, dao.queryAllocationsByJobIdAndReservationId( 3, 19 ).size(), "empty list" );

        // execute returned list of size 1
        List<Allocation> allocs = dao.queryAllocationsByJobIdAndReservationId( 2, 11 );
        assertEquals( 1, allocs.size(), "size" );
        assertEquals( "guest B", allocs.get( 0 ).getGuestName(), "allocation name" );
        assertEquals( 11, allocs.get( 0 ).getReservationId(), "reservation ID" );
        assertEquals( 2, allocs.get( 0 ).getJobId(), "job ID" );

        // execute returned list of size 3
        allocs = dao.queryAllocationsByJobIdAndReservationId( 3, 10 );
        assertEquals( 3, allocs.size(), "size" );
        for ( Allocation alloc : allocs ) {
            assertEquals( 10, alloc.getReservationId(), "reservation ID" );
            assertEquals( 3, alloc.getJobId(), "job ID" );
        }
        assertEquals(
                new HashSet<String>( Arrays.asList( "guest A", "guest C", "guest E" ) ),
                new HashSet<String>( Arrays.asList(
                        allocs.get( 0 ).getGuestName(),
                        allocs.get( 1 ).getGuestName(),
                        allocs.get( 2 ).getGuestName() ) ), "allocation names" );
    }

    @Test
    public void testGetLastCompletedJobOfType() {
        HousekeepingJob job = new HousekeepingJob();
        job.setStatus( JobStatus.completed );
        int jobId = dao.insertJob( job );

        job = dao.getLastCompletedJobOfType( HousekeepingJob.class );
        assertEquals( jobId, job.getId(), "job id" );
    }

    @Test
    public void testGetCheckinDatesForAllocationScraperJobId() throws Exception {

        // first we some test allocations
        insertTestAllocation( 3, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), "HW" );
        insertTestAllocation( 2, DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-21" ), "HW" ); // different job id
        insertTestAllocation( 3, DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-20" ), "HW" );

        // execute and verify
        List<Date> dates = dao.getCheckinDatesForAllocationScraperJobId( 3 );
        assertEquals( 2, dates.size(), "number of dates" );
        assertEquals( "2014-04-20", DATE_FORMAT_YYYY_MM_DD.format( dates.get( 0 ) ), "first date" );
        assertEquals( "2014-05-03", DATE_FORMAT_YYYY_MM_DD.format( dates.get( 1 ) ), "second date" );
    }

    @Test
    public void testResetAllProcessingJobsToFailed() throws Exception {
        Job job1 = new AllocationScraperJob();
        job1.setStatus( JobStatus.submitted );
        dao.insertJob( job1 );

        Job job2 = new HousekeepingJob();
        job2.setStatus( JobStatus.processing );
        dao.insertJob( job2 );

        Job job3 = new HousekeepingJob();
        job3.setStatus( JobStatus.completed );
        dao.insertJob( job3 );

        // execute
        dao.resetAllProcessingJobsToFailed();
        assertEquals( JobStatus.submitted, dao.fetchJobById( job1.getId() ).getStatus(), "job1 status" );
        assertEquals( JobStatus.failed, dao.fetchJobById( job2.getId() ).getStatus(), "job2 status" );
        assertEquals( JobStatus.completed, dao.fetchJobById( job3.getId() ).getStatus(), "job3 status" );
    }

    @Test
    public void testPurgeRecordsOlderThan() throws Exception {
        Calendar now = Calendar.getInstance();
        now.add( Calendar.DATE, -30 ); // 30 days ago

        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.completed );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        j.setCreatedDate( new Timestamp( now.getTimeInMillis() ) );
        j.setLastUpdatedDate( new Timestamp( now.getTimeInMillis() ) );
        j.setJobStartDate( new Timestamp( now.getTimeInMillis() ) );
        j.setJobEndDate( new Timestamp( now.getTimeInMillis() ) );
        int jobId = dao.insertJob( j );

        insertTestAllocation( jobId, 10, "Hostelworld", "12.01", "12.01" );

        now.add( Calendar.DATE, 1 ); // move up a day
        dao.purgeRecordsOlderThan( now.getTime() );
    }

    @Test
    public void testGetRoomTypeIdForHostelworldLabel() throws Exception {
        assertEquals( Integer.valueOf( 2964 ), dao.getRoomTypeIdForHostelworldLabel( "Basic Double Bed Private (Shared Bathroom)" ) );
        assertEquals( Integer.valueOf( 2965 ), dao.getRoomTypeIdForHostelworldLabel( "Basic 3 Bed Private (Shared Bathroom)" ) );
        assertEquals( Integer.valueOf( 2966 ), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Private (Shared Bathroom)" ) );
        assertEquals( Integer.valueOf( 2973 ), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Mixed Dorm" ) );
        assertEquals( Integer.valueOf( 2974 ), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Female Dorm" ) );
        assertEquals( Integer.valueOf( 2972 ), dao.getRoomTypeIdForHostelworldLabel( "6 Bed Mixed Dorm" ) );
        assertEquals( Integer.valueOf( 2971 ), dao.getRoomTypeIdForHostelworldLabel( "8 Bed Mixed Dorm" ) );
        assertEquals( Integer.valueOf( 2970 ), dao.getRoomTypeIdForHostelworldLabel( "10 Bed Mixed Dorm" ) );
        assertEquals( Integer.valueOf( 2969 ), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Male Dorm" ) );
        assertEquals( Integer.valueOf( 2968 ), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Female Dorm" ) );
        assertEquals( Integer.valueOf( 2967 ), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Mixed Dormitory" ) );
        assertEquals( Integer.valueOf( 5152 ), dao.getRoomTypeIdForHostelworldLabel( "14 Bed Mixed Dorm" ) );
        assertEquals( Integer.valueOf( 5112 ), dao.getRoomTypeIdForHostelworldLabel( "16 Bed Mixed Dormitory" ) );
        assertEquals( null, dao.getRoomTypeIdForHostelworldLabel( "1 Bed Mixed Dormitory" ) );
    }

    @Test
    public void testGetRoomTypeIdForHostelworldLabelThrowsException() throws Exception {
        assertThrows( EmptyResultDataAccessException.class, () -> {
            dao.getRoomTypeIdForHostelworldLabel( "7 Bed Mixed Dorm" );
        } );
    }

    @Test
    public void testDeleteHostelworldBookingsWithArrivalDate() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.DATE, 20 );
        dao.deleteHostelworldBookingsWithArrivalDate( c.getTime() );
    }

    private Allocation createTestAllocation( int jobId, Date checkinDate, String bookingSource ) throws Exception {
        Allocation alloc = new Allocation();
        alloc.setJobId( jobId );
        alloc.setRoomId( "15" );
        alloc.setRoom( "the room" );
        alloc.setBedName( "the bed" );
        alloc.setReservationId( 4 );
        alloc.setCheckinDate( checkinDate );
        alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ) );
        alloc.setBookingSource( bookingSource );
        return alloc;
    }

    private Allocation insertTestAllocation( int jobId, Date checkinDate, String bookingSource ) throws Exception {
        Allocation alloc = createTestAllocation( jobId, checkinDate, bookingSource );
        dao.insertAllocation( alloc );
        assertTrue( alloc.getId() > 0, "ID not assigned" );
        return alloc;
    }

    private Allocation insertTestAllocation( int jobId, int reservationId, String guestName ) throws Exception {
        Allocation alloc = createTestAllocation( jobId, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), "HW" );
        alloc.setReservationId( reservationId );
        alloc.setGuestName( guestName );
        dao.insertAllocation( alloc );
        assertTrue( alloc.getId() > 0, "ID not assigned" );
        return alloc;
    }

    private Allocation insertTestAllocation( int jobId, int reservationId, String bookingSource, String paymentTotal, String paymentOutstanding ) throws Exception {
        Allocation alloc = createTestAllocation( jobId, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), bookingSource );
        alloc.setReservationId( reservationId );
        alloc.setPaymentTotal( paymentTotal );
        alloc.setPaymentOutstanding( paymentOutstanding );
        dao.insertAllocation( alloc );
        assertTrue( alloc.getId() > 0, "ID not assigned" );
        return alloc;
    }

    @Test
    public void testGetOption() {
        assertEquals( "Just another WordPress site", dao.getOption( "blogdescription" ) );
        assertEquals( null, dao.getOption( "non.existent.key" ) );
    }

    @Test
    public void testDeleteAllocations() {
        dao.deleteAllocations( 109 );
    }

    @Test
    public void testSetOption() {
        dao.setOption( "tmp_del_me", "balls" );
        assertEquals( "balls", dao.getOption( "tmp_del_me" ) );
        dao.setOption( "tmp_del_me", "sticks" );
        assertEquals( "sticks", dao.getOption( "tmp_del_me" ) );
    }

    @Test
    public void testFetchUnpaidDepositReport() {
        List<UnpaidDepositReportEntry> report = dao.fetchUnpaidDepositReport( 136564 );
        LOGGER.info( "records found: " + report.size() );
        for ( UnpaidDepositReportEntry row : report ) {
            LOGGER.info( ToStringBuilder.reflectionToString( row ) );
            LOGGER.info( "Reservation " + row.getReservationId() );
        }
    }

    @Test
    public void testFetchPrepaidBDCBookingsWithOutstandingBalance() {
        // find all BDC bookings with unpaid deposits that use a virtual CC
        // and create a job for each of them
        dao.fetchPrepaidBDCBookingsWithOutstandingBalance()
                .stream()
                // include bookings that are about to arrive (in case we couldn't charge them yet)
                .filter( p -> p.isChargeableDateInPast() || p.isCheckinDateTodayOrTomorrow() )
                .forEach( a -> {
                    LOGGER.info( "Booking " + a.getBookingReference()
                            + " is chargeable on " + a.getEarliestChargeDate() );
                    LOGGER.info( ToStringBuilder.reflectionToString( a ) );
                } );
    }

    @Test
    public void testFetchGuestComments() throws Exception {
        LOGGER.info( ToStringBuilder.reflectionToString( dao.fetchGuestComments( 10372722 ) ) );
    }

    @Test
    public void testRunGroupBookingsReport() {
        dao.runGroupBookingsReport( 137652 );
    }

    @Test
    public void testRunBedCountsReport() {
        dao.runBedCountsReport( 557130, LocalDate.of( 2023, 8, 26 ) );
    }

    @Test
    public void testDeleteHostelworldBookingsWithBookedDate() {
        Calendar c = Calendar.getInstance();
        dao.deleteHostelworldBookingsWithBookedDate( c.getTime() );
    }

    @Test
    public void testGetLastJobOfType() {
        UnpaidDepositReportJob j = dao.getLastJobOfType( UnpaidDepositReportJob.class );
        LOGGER.info( "Job " + j.getId() + " found with status " + j.getStatus() );
        LOGGER.info( "Allocation scraper job id: " + j.getAllocationScraperJobId() );
        LOGGER.info( ToStringBuilder.reflectionToString( j ) );
    }

    @Test
    public void testGetOutstandingJobCount() {
        LOGGER.info( dao.getOutstandingJobCount() + " outstanding jobs" );
    }

    @Test
    public void testFetchAgodaBookingsMissingNoChargeNote() {
        final AtomicInteger counter = new AtomicInteger( 0 );
        dao.fetchAgodaBookingsMissingNoChargeNote()
                .stream()
                .forEach( p -> {
                    LOGGER.info( p.getBookingReference() + " " + p.getGuestComment() );
                    counter.incrementAndGet();
                } );
        LOGGER.info( "Found " + counter + " records" );
    }

    @Test
    public void testFetchActiveJobSchedules() {
        dao.fetchActiveJobSchedules()
                .stream()
                .forEach( s -> {
                    LOGGER.info( ToStringBuilder.reflectionToString( s ) );
                    LOGGER.info( "is overdue? " + s.isOverdue() );
                    try {
                        int jobId = dao.insertJob( s.createNewJob() );
                        LOGGER.info( "Created job " + jobId );
                    }
                    catch ( ReflectiveOperationException e ) {
                        LOGGER.error( "whoops!", e );
                    }
                } );
    }

    @Test
    public void testIsJobCurrentlyPending() {
        LOGGER.info( "pending: " + dao.isJobCurrentlyPending( "com.macbackpackers.jobs.ConfirmDepositAmountsJob" ) );
    }

    @Test
    public void testInsertBookingLookupKey() {
        dao.insertBookingLookupKey( "12345678", "ABCDEFG", null );
        dao.insertBookingLookupKey( "12345678", "BCDEFGH", new BigDecimal( "2.3" ) );
        dao.insertBookingLookupKey( "12345678", "CDEFGHI", new BigDecimal( "12.34" ) );
    }

    @Test
    public void testGetReservationIdsForDepositChargeJobs() {
        List<String> ids = dao.getReservationIdsForDepositChargeJobs( 469807, 469929 );
        LOGGER.info( "Found " + ids.size() + " entries." );
        ids.forEach( id -> LOGGER.info( id ) );
    }

    @Test
    public void testFetchStripeRefund() throws Exception {
        StripeRefund refund = dao.fetchStripeRefund( 1 );
        LOGGER.info( ToStringBuilder.reflectionToString( refund ) );
    }

    @Test
    public void testFetchStripeTransaction() throws Exception {
        StripeTransaction txn = dao.fetchStripeTransaction( "CRH-318007765345-4GYB" );
        LOGGER.info( ToStringBuilder.reflectionToString( txn ) );
    }

    @Test
    public void fetchStripeRefundsAtStatus() throws Exception {
        List<StripeRefund> refunds = dao.fetchStripeRefundsAtStatus( "pending" );
        LOGGER.info( "Found " + refunds.size() + " records" );
    }

    @Test
    public void testFetchBookingsMatchingBlacklist() {
        List<Allocation> allocations = dao.fetchBookingsMatchingBlacklist( 453596, sharedDao.fetchBlacklistEntries() );
        LOGGER.info( "Found {} records.", allocations.size() );
    }
}
