package com.macbackpackers.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

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
    public void testUpdateGuestCommentsForReservations() throws Exception {
        List<GuestCommentReportEntry> comments = new ArrayList<>();
        comments.add( new GuestCommentReportEntry( 12345678, "Test Comment" ) );
        comments.add( new GuestCommentReportEntry( 12345679, "Test Comment 2" ) );
        comments.add( new GuestCommentReportEntry( 12345680, "Test Comment 3" ) );
        dao.updateGuestCommentsForReservations( comments );
    }

    @Test
    public void testGuestRequestReclassifiedOnlyWhenCommentsChange() throws Exception {
        int reservationId = 12345681;
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Bottom bunk please" ) ) );
        dao.updateGuestRequest( reservationId, "Bottom bunk please", "Bottom bunk please" );
        assertNotNull( dao.fetchGuestComments( reservationId ).getClassifiedDate(), "classified after extraction" );

        // same text: stays classified
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Bottom bunk please" ) ) );
        assertNotNull( dao.fetchGuestComments( reservationId ).getClassifiedDate(), "unchanged text keeps classification" );

        // changed text: pending re-extraction, previous request kept until then
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Top bunk please" ) ) );
        GuestCommentReportEntry changed = dao.fetchGuestComments( reservationId );
        assertEquals( null, changed.getClassifiedDate(), "changed text resets classification" );
        assertEquals( "Bottom bunk please", changed.getGuestRequest() );
    }

    @Test
    public void testUpdateGuestRequestResetsAcknowledgedOnlyWhenRequestChanges() throws Exception {
        int reservationId = 12345682;
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Bottom bunk please" ) ) );
        dao.updateGuestRequest( reservationId, "Bottom bunk please", "Bottom bunk please" );
        testDAO.runSQL( "UPDATE wp_lh_rpt_guest_comments SET acknowledged_date = NOW() WHERE reservation_id = " + reservationId );

        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Bottom bunk please!" ) ) );
        dao.updateGuestRequest( reservationId, "Bottom bunk please!", "Bottom bunk please" );
        assertNotNull( dao.fetchGuestComments( reservationId ).getAcknowledgedDate(), "same request stays acknowledged" );

        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Top bunk please" ) ) );
        dao.updateGuestRequest( reservationId, "Top bunk please", "Top bunk please" );
        assertEquals( null, dao.fetchGuestComments( reservationId ).getAcknowledgedDate(), "changed request is re-flagged" );
    }

    @Test
    public void testUpdateGuestRequestIgnoresStaleOrDuplicateExtraction() throws Exception {
        int reservationId = 12345683;
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Bottom bunk please" ) ) );

        // comments changed while the extraction was in flight
        dao.updateGuestCommentsForReservations( List.of( new GuestCommentReportEntry( reservationId, "Top bunk please" ) ) );
        dao.updateGuestRequest( reservationId, "Bottom bunk please", "Bottom bunk please" );
        GuestCommentReportEntry stale = dao.fetchGuestComments( reservationId );
        assertEquals( null, stale.getGuestRequest(), "stale extraction discarded" );
        assertEquals( null, stale.getClassifiedDate(), "still pending extraction" );

        dao.updateGuestRequest( reservationId, "Top bunk please", "Top bunk please" );
        testDAO.runSQL( "UPDATE wp_lh_rpt_guest_comments SET acknowledged_date = NOW() WHERE reservation_id = " + reservationId );

        // a second extraction of the same text doesn't replace the request or its acknowledgement
        dao.updateGuestRequest( reservationId, "Top bunk please", "Upper bunk requested" );
        GuestCommentReportEntry duplicate = dao.fetchGuestComments( reservationId );
        assertEquals( "Top bunk please", duplicate.getGuestRequest() );
        assertNotNull( duplicate.getAcknowledgedDate(), "duplicate extraction keeps acknowledgement" );
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
    public void testGetLastCompletedJobOfType() {
        HousekeepingJob job = new HousekeepingJob();
        job.setStatus( JobStatus.completed );
        int jobId = dao.insertJob( job );

        job = dao.getLastCompletedJobOfType( HousekeepingJob.class );
        assertEquals( jobId, job.getId(), "job id" );
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
    public void testGetProcessingJobCount() throws Exception {
        Job submitted = new AllocationScraperJob();
        submitted.setStatus( JobStatus.submitted );
        dao.insertJob( submitted );

        Job processing = new HousekeepingJob();
        processing.setStatus( JobStatus.processing );
        dao.insertJob( processing );

        Job completed = new HousekeepingJob();
        completed.setStatus( JobStatus.completed );
        dao.insertJob( completed );

        assertEquals( 1, dao.getProcessingJobCount(), "processing job count" );
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

    @Test
    public void testGetOption() {
        assertEquals( "Just another WordPress site", dao.getOption( "blogdescription" ) );
        assertEquals( null, dao.getOption( "non.existent.key" ) );
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
    public void testFetchReservationIdsMatchingBlacklist() {
        List<Long> reservationIds = dao.fetchReservationIdsMatchingBlacklist( sharedDao.fetchBlacklistEntries() );
        LOGGER.info( "Found {} records.", reservationIds.size() );
    }
}
