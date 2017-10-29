
package com.macbackpackers.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.MissingGuestComment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.UnpaidDepositReportEntry;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.BookingScraperJob;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.jobs.HousekeepingJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class WordPressDAOTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    WordPressDAO dao;

    @Autowired
    TestHarnessDAO testDAO;

    static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    @Before
    public void setUp() throws Exception {
        //testDAO.deleteAllTransactionalData(); // clear out data
    }

    @Test
    public void testInsertAllocation() throws Exception {

        Allocation alloc = new Allocation();
        alloc.setJobId( 3 );
        alloc.setRoomId( 15 );
        alloc.setRoom( "the room" );
        alloc.setBedName( "the bed" );
        alloc.setReservationId( 92415 );
        alloc.setGuestName( "elizabeth" );
        alloc.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-21" ) );
        alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ) );
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

        dao.insertAllocation( alloc );
        Assert.assertTrue( "ID not assigned", alloc.getId() > 0 );

        Allocation allocView = dao.fetchAllocation( alloc.getId() );
        Assert.assertEquals( "ETA", alloc.getEta(), allocView.getEta() );
        Assert.assertEquals( "viewed", alloc.isViewed(), allocView.isViewed() );
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
        Assert.assertEquals( "checkin date", "2014-04-26", DATE_FORMAT_YYYY_MM_DD.format( allocView.getCheckinDate() ) );
        Assert.assertEquals( "bed name", "Updated bed name", allocView.getBedName() );
        Assert.assertEquals( "notes", "Updated notes", allocView.getNotes() );
    }

    @Test
    public void testInsertJob() throws Exception {
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        int jobId = dao.insertJob( j );

        Assert.assertEquals( true, jobId > 0 );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        Assert.assertEquals( AllocationScraperJob.class, jobView.getClass() );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getStatus(), jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not null", jobView.getLastUpdatedDate() );
        Assert.assertEquals( "start_date", j.getParameter( "start_date" ), jobView.getParameter( "start_date" ) );
        Assert.assertEquals( "end_date", j.getParameter( "end_date" ), jobView.getParameter( "end_date" ) );
    }

    @Test
    public void testCreateAllocationScraperJob() throws Exception {
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        int jobId = dao.insertJob( j );

        Assert.assertEquals( "Job id not updated: " + jobId, true, jobId > 0 );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        Assert.assertEquals( AllocationScraperJob.class, jobView.getClass() );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getStatus(), jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not null", jobView.getLastUpdatedDate() );
        Assert.assertEquals( "start_date", j.getParameter( "start_date" ), jobView.getParameter( "start_date" ) );
        Assert.assertEquals( "end_date", j.getParameter( "end_date" ), jobView.getParameter( "end_date" ) );
    }

    @Test
    public void testCreateBookingScraperJob() throws Exception {
        Job j = new BookingScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "checkin_date", "2015-05-29 00:00:00" );
        j.setParameter( "allocation_scraper_job_id", "12" );
        int jobId = dao.insertJob( j );

        Assert.assertEquals( "Job id not updated: " + jobId, true, jobId > 0 );

        // now verify the results
        Job jobView = dao.fetchJobById( jobId );
        Assert.assertEquals( BookingScraperJob.class, jobView.getClass() );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getStatus(), jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not null", jobView.getLastUpdatedDate() );
        Assert.assertEquals( "checkin_date", j.getParameter( "checkin_date" ), jobView.getParameter( "checkin_date" ) );
        Assert.assertEquals( "allocation_scraper_job_id", j.getParameter( "allocation_scraper_job_id" ), jobView.getParameter( "allocation_scraper_job_id" ) );
    }

    @Test
    public void testfetchJobById() throws Exception {
        Job j1 = new AllocationScraperJob();
        j1.setStatus( JobStatus.submitted );
        int jobId1 = dao.insertJob( j1 );
        Assert.assertEquals( true, jobId1 > 0 );

        Job jobView1 = dao.fetchJobById( jobId1 );
        Assert.assertEquals( AllocationScraperJob.class, jobView1.getClass() );
        Assert.assertEquals( jobId1, jobView1.getId() );

        // create an identical job to the first
        Job j2 = new AllocationScraperJob();
        j2.setStatus( JobStatus.submitted );
        int jobId2 = dao.insertJob( j2 );
        Assert.assertEquals( true, jobId2 > 0 );
        Assert.assertNotEquals( jobId1, jobId2 );

        Job jobView2 = dao.fetchJobById( jobId2 );
        Assert.assertEquals( AllocationScraperJob.class, jobView1.getClass() );
        Assert.assertEquals( jobId2, jobView2.getId() );

        // verify we actually have different objects
        Assert.assertNotEquals( jobView1.getId(), jobView2.getId() );
    }

    @Test
    public void testGetNextJobToProcess() throws Exception {

        Job j = new HousekeepingJob();
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        LOGGER.info( "created job " + jobId );
        Assert.assertEquals( true, jobId > 0 );

        // create a job that isn't complete
        Job j2 = new AllocationScraperJob();
        j2.setStatus( JobStatus.completed );
        int jobId2 = dao.insertJob( j2 );
        LOGGER.info( "created job " + jobId2 );

        // now verify the results
        // returns the first job created
        Job jobView = dao.getNextJobToProcess();

        Assert.assertEquals( HousekeepingJob.class, jobView.getClass() );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( JobStatus.processing, jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not null", jobView.getLastUpdatedDate() );
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
        Assert.assertEquals( HousekeepingJob.class, jobView.getClass() );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( JobStatus.processing, jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not found", jobView.getLastUpdatedDate() );
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
        List<Integer> reservationIds = dao.getHostelworldHostelBookersUnpaidDepositReservations( 3 );
        Assert.assertEquals( "size", 2, reservationIds.size() );
        Assert.assertEquals( "reservation ids",
                new HashSet<Integer>( Arrays.asList( 10, 14 ) ),
                new HashSet<Integer>( reservationIds ) );
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
        Assert.assertEquals( "empty list", 0, dao.queryAllocationsByJobIdAndReservationId( 2, 10 ).size() );
        Assert.assertEquals( "empty list", 0, dao.queryAllocationsByJobIdAndReservationId( 3, 19 ).size() );

        // execute returned list of size 1
        List<Allocation> allocs = dao.queryAllocationsByJobIdAndReservationId( 2, 11 );
        Assert.assertEquals( "size", 1, allocs.size() );
        Assert.assertEquals( "allocation name", "guest B", allocs.get( 0 ).getGuestName() );
        Assert.assertEquals( "reservation ID", 11, allocs.get( 0 ).getReservationId() );
        Assert.assertEquals( "job ID", 2, allocs.get( 0 ).getJobId() );

        // execute returned list of size 3
        allocs = dao.queryAllocationsByJobIdAndReservationId( 3, 10 );
        Assert.assertEquals( "size", 3, allocs.size() );
        for ( Allocation alloc : allocs ) {
            Assert.assertEquals( "reservation ID", 10, alloc.getReservationId() );
            Assert.assertEquals( "job ID", 3, alloc.getJobId() );
        }
        Assert.assertEquals( "allocation names",
                new HashSet<String>( Arrays.asList( "guest A", "guest C", "guest E" ) ),
                new HashSet<String>( Arrays.asList(
                        allocs.get( 0 ).getGuestName(),
                        allocs.get( 1 ).getGuestName(),
                        allocs.get( 2 ).getGuestName() ) ) );
    }

    @Test
    public void testGetLastCompletedJobOfType() {
        HousekeepingJob job = new HousekeepingJob();
        job.setStatus( JobStatus.completed );
        int jobId = dao.insertJob( job );

        job = dao.getLastCompletedJobOfType( HousekeepingJob.class );
        Assert.assertEquals( "job id", jobId, job.getId() );
    }

    @Test
    public void testGetCheckinDatesForAllocationScraperJobId() throws Exception {

        // first we some test allocations
        insertTestAllocation( 3, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), "HW" );
        insertTestAllocation( 2, DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-21" ), "HW" ); // different job id
        insertTestAllocation( 3, DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-20" ), "HW" );

        // execute and verify
        List<Date> dates = dao.getCheckinDatesForAllocationScraperJobId( 3 );
        Assert.assertEquals( "number of dates", 2, dates.size() );
        Assert.assertEquals( "first date", "2014-04-20", DATE_FORMAT_YYYY_MM_DD.format( dates.get( 0 ) ) );
        Assert.assertEquals( "second date", "2014-05-03", DATE_FORMAT_YYYY_MM_DD.format( dates.get( 1 ) ) );
    }

    @Test
    public void testResetAllProcessingJobsToFailed() throws Exception {
        Job job1 = new AllocationScraperJob();
        job1.setStatus( JobStatus.submitted );
        dao.insertJob( job1 );

        Job job2 = new HousekeepingJob();
        job2.setStatus( JobStatus.processing );
        dao.insertJob( job2 );

        Job job3 = new ConfirmDepositAmountsJob();
        job3.setStatus( JobStatus.completed );
        dao.insertJob( job3 );

        // execute
        dao.resetAllProcessingJobsToFailed();
        Assert.assertEquals( "job1 status", JobStatus.submitted, dao.fetchJobById( job1.getId() ).getStatus() );
        Assert.assertEquals( "job2 status", JobStatus.failed, dao.fetchJobById( job2.getId() ).getStatus() );
        Assert.assertEquals( "job3 status", JobStatus.completed, dao.fetchJobById( job3.getId() ).getStatus() );
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
        Assert.assertEquals( new Integer(2964), dao.getRoomTypeIdForHostelworldLabel( "Basic Double Bed Private (Shared Bathroom)" ) );
        Assert.assertEquals( new Integer(2965), dao.getRoomTypeIdForHostelworldLabel( "Basic 3 Bed Private (Shared Bathroom)" ) );
        Assert.assertEquals( new Integer(2966), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Private (Shared Bathroom)" ) );
        Assert.assertEquals( new Integer(2973), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Mixed Dorm" ) );
        Assert.assertEquals( new Integer(2974), dao.getRoomTypeIdForHostelworldLabel( "4 Bed Female Dorm" ) );
        Assert.assertEquals( new Integer(2972), dao.getRoomTypeIdForHostelworldLabel( "6 Bed Mixed Dorm" ) );
        Assert.assertEquals( new Integer(2971), dao.getRoomTypeIdForHostelworldLabel( "8 Bed Mixed Dorm" ) );
        Assert.assertEquals( new Integer(2970), dao.getRoomTypeIdForHostelworldLabel( "10 Bed Mixed Dorm" ) );
        Assert.assertEquals( new Integer(2969), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Male Dorm" ) );
        Assert.assertEquals( new Integer(2968), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Female Dorm" ) );
        Assert.assertEquals( new Integer(2967), dao.getRoomTypeIdForHostelworldLabel( "12 Bed Mixed Dormitory" ) );
        Assert.assertEquals( new Integer(5152), dao.getRoomTypeIdForHostelworldLabel( "14 Bed Mixed Dorm" ) );
        Assert.assertEquals( new Integer(5112), dao.getRoomTypeIdForHostelworldLabel( "16 Bed Mixed Dormitory" ) );
        Assert.assertEquals( null, dao.getRoomTypeIdForHostelworldLabel( "1 Bed Mixed Dormitory" ) );
    }

    @Test( expected = EmptyResultDataAccessException.class )
    public void testGetRoomTypeIdForHostelworldLabelThrowsException() throws Exception {
        dao.getRoomTypeIdForHostelworldLabel( "7 Bed Mixed Dorm" );
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
        alloc.setRoomId( 15 );
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
        Assert.assertTrue( "ID not assigned", alloc.getId() > 0 );
        return alloc;
    }

    private Allocation insertTestAllocation( int jobId, int reservationId, String guestName ) throws Exception {
        Allocation alloc = createTestAllocation( jobId, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), "HW" );
        alloc.setReservationId( reservationId );
        alloc.setGuestName( guestName );
        dao.insertAllocation( alloc );
        Assert.assertTrue( "ID not assigned", alloc.getId() > 0 );
        return alloc;
    }

    private Allocation insertTestAllocation( int jobId, int reservationId, String bookingSource, String paymentTotal, String paymentOutstanding ) throws Exception {
        Allocation alloc = createTestAllocation( jobId, DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ), bookingSource );
        alloc.setReservationId( reservationId );
        alloc.setPaymentTotal( paymentTotal );
        alloc.setPaymentOutstanding( paymentOutstanding );
        dao.insertAllocation( alloc );
        Assert.assertTrue( "ID not assigned", alloc.getId() > 0 );
        return alloc;
    }

    @Test
    public void testGetOption() {
        Assert.assertEquals( "Just another WordPress site", dao.getOption( "blogdescription" ) );
        Assert.assertEquals( null, dao.getOption( "non.existent.key" ) );
    }
    
    @Test
    public void testGetAllocations() {
        List<MissingGuestComment> allocations = dao.getAllocationsWithoutEntryInGuestCommentsReport( 472 );
        for(MissingGuestComment a : allocations) {
            LOGGER.info( "Reservation: " + a.getReservationId() );
            LOGGER.info( "Booking Ref: " + a.getBookingReference() );
        }
    }
    
    @Test
    public void testDeleteAllocations() {
        dao.deleteAllocations( 109 );
    }

    @Test
    public void testSetOption() {
        dao.setOption( "tmp_del_me", "balls" );
        Assert.assertEquals( dao.getOption( "tmp_del_me" ), "balls" );
        dao.setOption( "tmp_del_me", "sticks" );
        Assert.assertEquals( dao.getOption( "tmp_del_me" ), "sticks" );
    }
    
    @Test
    public void testFetchUnpaidDepositReport() {
        List<UnpaidDepositReportEntry> report = dao.fetchUnpaidDepositReport(136564);
        LOGGER.info( "records found: " + report.size() );
        for(UnpaidDepositReportEntry row : report) {
            LOGGER.info( ToStringBuilder.reflectionToString( row ));
            LOGGER.info( "Reservation " + row.getReservationId() );
        }
    }
    
    @Test
    public void testFetchPrepaidBDCBookingsWithUnpaidDeposits() {
        // find all BDC bookings with unpaid deposits that use a virtual CC
        // and create a job for each of them
        dao.fetchPrepaidBDCBookingsWithUnpaidDeposits()
                .stream()
                .filter( p -> p.isChargeableDateInPast() )
                .forEach( a -> {
                    LOGGER.info( "Booking " + a.getBookingReference() 
                            + " is chargeable on " + a.getEarliestChargeDate() );
                    LOGGER.info( ToStringBuilder.reflectionToString( a ) );
                } );
    }
    
    @Test
    public void testRunGroupBookingsReport() {
        dao.runGroupBookingsReport( 137652 );   
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
    public void testGetPreviousNumberOfFailedTxns() {
        Assert.assertEquals( 1, dao.getPreviousNumberOfFailedTxns( "BDC-12345680", "123456........89" ));
        Assert.assertEquals( 0, dao.getPreviousNumberOfFailedTxns( "BDC-12345680", "123456........99" ));
        Assert.assertEquals( 0, dao.getPreviousNumberOfFailedTxns( "BDC-12345681", "123456........89" ));
        Assert.assertEquals( 0, dao.getPreviousNumberOfFailedTxns( "BDC-12345679", "123456........89" ));
    }
    
    @Test
    public void testPxPost() {
        String bookingRef = "BDC-12345680";
        PxPostTransaction txn = dao.getLastPxPost( bookingRef );
        Assert.assertEquals( null, txn );

        int txnId = dao.insertNewPxPostTransaction( 0, bookingRef, new BigDecimal( "14.22" ) );
        Assert.assertEquals( true, txnId > 0 );
        
        txn = dao.getLastPxPost( bookingRef );
        Assert.assertEquals( bookingRef, txn.getBookingReference() );
        Assert.assertEquals( new BigDecimal( "14.22" ), txn.getPaymentAmount() );
        Assert.assertEquals( true, txn.getCreatedDate() != null );
        Assert.assertEquals( txnId, txn.getId() );
        
        // update the record
        dao.updatePxPostTransaction( txnId, "123456........89", 
                "<Request>Test</Request>", 200, "<Response>Answered</Response>", 
                true, "Help me!" );
        
        txn = dao.fetchPxPostTransaction( txnId );
        Assert.assertEquals( txnId, txn.getId() );
        Assert.assertEquals( bookingRef, txn.getBookingReference() );
        Assert.assertEquals( "123456........89", txn.getMaskedCardNumber() );
        Assert.assertEquals( new BigDecimal( "14.22" ), txn.getPaymentAmount() );
        Assert.assertEquals( "<Request>Test</Request>", txn.getPaymentRequestXml() );
        Assert.assertEquals( new Integer( 200 ), txn.getPaymentResponseHttpCode() );
        Assert.assertEquals( "<Response>Answered</Response>", txn.getPaymentResponseXml() );
        Assert.assertEquals( true, txn.getSuccessful() );
        Assert.assertEquals( true, txn.getPostDate() != null );
        Assert.assertEquals( "Help me!", txn.getHelpText() );
        
        // update using status
        dao.updatePxPostStatus(txnId, null, false, "<Status>");
        txn = dao.fetchPxPostTransaction( txnId );
        Assert.assertEquals( txnId, txn.getId() );
        Assert.assertEquals( bookingRef, txn.getBookingReference() );
        Assert.assertEquals( "<Status>", txn.getPaymentStatusResponseXml() );
        Assert.assertEquals( false, txn.getSuccessful() );
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
}
