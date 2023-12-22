
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.macbackpackers.jobs.ArchiveAllTransactionNotesJob;
import com.macbackpackers.jobs.BedCountReportJob;
import com.macbackpackers.jobs.CreatePrepaidRefundJob;
import com.macbackpackers.jobs.PrepaidRefundJob;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.TestHarnessDAO;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AgodaChargeJob;
import com.macbackpackers.jobs.AgodaNoChargeNoteJob;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob;
import com.macbackpackers.jobs.BedCountJob;
import com.macbackpackers.jobs.BookingReportJob;
import com.macbackpackers.jobs.CancelBookingJob;
import com.macbackpackers.jobs.ChargeNonRefundableBookingJob;
import com.macbackpackers.jobs.CloudbedsAllocationScraperWorkerJob;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.jobs.CopyCardDetailsFromLHJob;
import com.macbackpackers.jobs.CopyCardDetailsToCloudbedsJob;
import com.macbackpackers.jobs.CreateAgodaNoChargeNoteJob;
import com.macbackpackers.jobs.CreateChargeNonRefundableBookingJob;
import com.macbackpackers.jobs.CreateConfirmDepositAmountsJob;
import com.macbackpackers.jobs.CreateCopyCardDetailsToCloudbedsJob;
import com.macbackpackers.jobs.CreateDepositChargeJob;
import com.macbackpackers.jobs.CreateFixedRateReservationJob;
import com.macbackpackers.jobs.CreatePrepaidChargeJob;
import com.macbackpackers.jobs.CreateScrapeCancelledBookingsJob;
import com.macbackpackers.jobs.CreateSendGuestCheckoutEmailJob;
import com.macbackpackers.jobs.CreateTestGuestCheckoutEmailJob;
import com.macbackpackers.jobs.DbPurgeJob;
import com.macbackpackers.jobs.DepositChargeJob;
import com.macbackpackers.jobs.DumpHostelworldBookingsByArrivalDateJob;
import com.macbackpackers.jobs.DumpHostelworldBookingsByBookedDateJob;
import com.macbackpackers.jobs.GroupBookingsReportJob;
import com.macbackpackers.jobs.ManualChargeJob;
import com.macbackpackers.jobs.PrepaidChargeJob;
import com.macbackpackers.jobs.ProcessSagepayTransactionJob;
import com.macbackpackers.jobs.ScrapeReservationsBookedOnJob;
import com.macbackpackers.jobs.SendAllUnsentEmailJob;
import com.macbackpackers.jobs.SendCovidPrestayEmailJob;
import com.macbackpackers.jobs.SendDepositChargeDeclinedEmailJob;
import com.macbackpackers.jobs.SendDepositChargeSuccessfulEmailJob;
import com.macbackpackers.jobs.SendHostelworldLateCancellationEmailJob;
import com.macbackpackers.jobs.SendNonRefundableDeclinedEmailJob;
import com.macbackpackers.jobs.SendNonRefundableSuccessfulEmailJob;
import com.macbackpackers.jobs.SendPaymentLinkEmailJob;
import com.macbackpackers.jobs.SendTemplatedEmailJob;
import com.macbackpackers.jobs.SplitRoomReservationReportJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;
import com.macbackpackers.jobs.UpdateLittleHotelierSettingsJob;
import com.macbackpackers.jobs.VerifyAlexaLoggedInJob;
import com.macbackpackers.jobs.VerifyGoogleAssistantLoggedInJob;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;

@SuppressWarnings( "deprecation" )
@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ProcessorServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ProcessorService processorService;

    @Autowired
    WordPressDAO dao;

    @Autowired
    TestHarnessDAO testDAO;

    @Autowired
    AutowireCapableBeanFactory autowireBeanFactory;
    
    @Before
    public void setUp() {
//        LOGGER.info( "deleting test data" );
//        testDAO.deleteAllTransactionalData();
    }

    @Test
    public void testAllocationScraperJob() throws Exception {

        // setup a job to scrape allocation info
        AllocationScraperJob j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setStartDate( Calendar.getInstance().getTime() );
        j.setDaysAhead( 27 );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testUnpaidDepositReportJob() throws Exception {

        // setup the dependent job (no data)
        Job allocScraperJob = new AllocationScraperJob();
        allocScraperJob.setStatus( JobStatus.completed );
        dao.insertJob( allocScraperJob );

        // setup a job to scrape allocation info
        Job j = new UnpaidDepositReportJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "allocation_scraper_job_id", String.valueOf( allocScraperJob.getId() ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testConfirmDepositAmountsJob() throws Exception {

        // setup the job
        ConfirmDepositAmountsJob j = new ConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingRef( "HWL-551-340153819" );
        j.setCheckinDate( new Date() );
        j.setReservationId( 7591369 );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testCreateConfirmDepositAmountsJob() throws Exception {

        // setup the dependent job (no data)
        Job asj = new AllocationScraperJob();
        asj.setStatus( JobStatus.completed );
        dao.insertJob( asj );

        // setup the job
        Job j = new CreateConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        j.getDependentJobs().add( asj );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testScrapeReservationsBookedOnJob() throws Exception {

        // setup a job to find reservations booked today and
        // create the confirm deposit job for each unread entry
        Job j = new ScrapeReservationsBookedOnJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "booked_on_date", BookingsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( new Date() ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testGroupBookingsReportJob() throws Exception {

        // setup the dependent job (no data)
        Job allocJob = new AllocationScraperJob();
        allocJob.setStatus( JobStatus.completed );
        dao.insertJob( allocJob );

        // setup a job to find reservations with more than X guests
        Job j = new GroupBookingsReportJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "allocation_scraper_job_id", String.valueOf( allocJob.getId() ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testSplitRoomReservationReportJob() throws Exception {

        // setup the dependent job (no data)
        Job allocJob = new AllocationScraperJob();
        allocJob.setStatus( JobStatus.completed );
        dao.insertJob( allocJob );

        // setup a job to find all bookings spanning different rooms
        Job j = new SplitRoomReservationReportJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "allocation_scraper_job_id", String.valueOf( allocJob.getId() ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testDbPurgeJob() throws Exception {
        Calendar now = Calendar.getInstance();
        now.add( Calendar.DATE, -30 ); // 30 days ago

        // insert old job to be deleted
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.completed );
        j.setParameter( "start_date", "2015-05-29 00:00:00" );
        j.setParameter( "end_date", "2015-06-14 00:00:00" );
        j.setCreatedDate( new Timestamp( now.getTimeInMillis() ) );
        j.setLastUpdatedDate( new Timestamp( now.getTimeInMillis() ) );
        j.setJobStartDate( new Timestamp( now.getTimeInMillis() ) );
        j.setJobEndDate( new Timestamp( now.getTimeInMillis() ) );
        int oldJobId = dao.insertJob( j );

        // setup job under test
        DbPurgeJob dbJob = new DbPurgeJob();
        dbJob.setStatus( JobStatus.submitted );
        dbJob.setDaysToKeep( 20 );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );

        try {
            dao.fetchJobById( oldJobId ); // this should've been deleted
            Assert.fail( "exception expected" );
        }
        catch ( EmptyResultDataAccessException ex ) {
            // expected exception
        }
    }
    
    @Test
    public void testCreateDepositChargeJob() throws Exception {

        CreateDepositChargeJob j = new CreateDepositChargeJob();
        j.setStatus( JobStatus.submitted );
        j.setDaysBack( 3 );
        dao.insertJob( j );
        
        // this should now run the job
        processorService.processJobs();
    }

    @Test
    public void testDepositChargeJob() throws Exception {
        DepositChargeJob j = new DepositChargeJob();

        j.setStatus( JobStatus.submitted );
        Calendar bookingDate = Calendar.getInstance();
        bookingDate.set( Calendar.DATE, 5 );
        bookingDate.set( Calendar.MONTH, Calendar.JUNE );
        bookingDate.set( Calendar.YEAR, 2017 );
        j.setBookingDate( bookingDate.getTime() );
        j.setBookingRef( "BDC-1722452541" );
        dao.insertJob( j );
        
        // this should now run the job
//        processorService.processJobs();
    }
    
    @Test
    public void testCreateSendGuestCheckoutEmailJob() throws Exception {
        CreateSendGuestCheckoutEmailJob j = new CreateSendGuestCheckoutEmailJob();
        Calendar checkoutDate = Calendar.getInstance();
        checkoutDate.set( Calendar.DATE, 28 );
        checkoutDate.set( Calendar.MONTH, Calendar.JANUARY );
        checkoutDate.set( Calendar.YEAR, 2017 );
        j.setCheckoutDate( checkoutDate.getTime() );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
       
        // this should now run the job
        processorService.processJobs();
    }

    @Test
    public void testSendAllUnsentEmailJob() throws Exception {
        CreateTestGuestCheckoutEmailJob testJob = new CreateTestGuestCheckoutEmailJob();
        testJob.setStatus( JobStatus.submitted );
        testJob.setRecipientEmail( "ronchan@techie.com" );
        testJob.setFirstName( "Ron" );
        testJob.setLastName( "Chan" );
        dao.insertJob( testJob );
        
        SendAllUnsentEmailJob j = new SendAllUnsentEmailJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
       
        // this should now run the job
        processorService.processJobs();
    }
    
    @Test
    @Transactional
    public void testRetrieveDependentJobs() throws Exception {
        CreateConfirmDepositAmountsJob j = dao.getLastJobOfType( CreateConfirmDepositAmountsJob.class );
        LOGGER.info( "Found " + j.getDependentJobs().size() + " dependent jobs" );
        Assert.assertThat( j.getDependentJobs().size(), Matchers.is( 1 ) );
        Job dj = j.getDependentJobs().iterator().next();
        Assert.assertEquals( dj.getClass(), AllocationScraperJob.class );
        Assert.assertEquals( dj.getId(), j.getId() - 1 );
    }
    
    @Test
    public void testDumpHostelworldBookingsByArrivalDateJob() throws Exception {
        Calendar checkinDate = Calendar.getInstance();
        checkinDate.add( Calendar.DATE, 12 );
        for(int i = 0; i < 120; i++) {
            DumpHostelworldBookingsByArrivalDateJob j = new DumpHostelworldBookingsByArrivalDateJob();
            j.setCheckinDate( checkinDate.getTime() );
            j.setStatus( JobStatus.submitted );
            dao.insertJob( j );
            checkinDate.add( Calendar.DATE, 1 );
        }
    }

    @Test
    public void testDumpHostelworldBookingsByBookedDateJob() throws Exception {
        Calendar bookedDate = Calendar.getInstance();
        bookedDate.add( Calendar.DATE, -1 );
        
        DumpHostelworldBookingsByBookedDateJob j = new DumpHostelworldBookingsByBookedDateJob();
        j.setBookedDate( bookedDate.getTime() );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
        
        bookedDate.add( Calendar.DATE, -1 );
        j = new DumpHostelworldBookingsByBookedDateJob();
        j.setBookedDate( bookedDate.getTime() );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
        
        bookedDate.add( Calendar.DATE, -1 );
        j = new DumpHostelworldBookingsByBookedDateJob();
        j.setBookedDate( bookedDate.getTime() );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
        
    }

    @Test
    public void testScrapeCancelledBookingsJob() throws Exception {
        CreateScrapeCancelledBookingsJob j = new CreateScrapeCancelledBookingsJob();
        j.setAllocationScraperJobId( 186689 );
        j.setDaysAhead( 130 );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testCreatePrepaidChargeJob() throws Exception {
        CreatePrepaidChargeJob j = new CreatePrepaidChargeJob();
        autowireBeanFactory.autowireBean( j );
        j.processJob();
    }

    @Test
    public void testRunCreatePrepaidRefundJob() throws Exception {
        CreatePrepaidRefundJob j = new CreatePrepaidRefundJob();
        autowireBeanFactory.autowireBean( j );
        j.processJob();
    }

    @Test
    public void testCreatePrepaidRefundJob() throws Exception {
        CreatePrepaidRefundJob j = new CreatePrepaidRefundJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testBedCountJob() throws Exception {
        BedCountJob j = new BedCountJob();
        j.setId( 496768 );
        j.setParameter( "selected_date", "2021-08-21" );
        autowireBeanFactory.autowireBean( j );
        j.resetJob();
        j.processJob();
    }

    @Test
    public void testBedCountReportJob() throws Exception {
        BedCountReportJob j = new BedCountReportJob();
        j.setStatus( JobStatus.submitted );
        j.setBedCountJobId( 557130 );
        j.setSelectedDate( LocalDate.of( 2023, 8, 26 ) );
        dao.insertJob( j );
    }

    @Test
    public void testPrepaidChargeJob() throws Exception {
        PrepaidChargeJob j = new PrepaidChargeJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "23486284" );
        dao.insertJob( j );
    }

    @Test
    public void testPrepaidRefundJob() throws Exception {
        PrepaidRefundJob j = new PrepaidRefundJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "70953983" );
        j.setReason("Waived fees");
        j.setAmount(new BigDecimal("91.08"));
        dao.insertJob( j );
    }

    @Test
    public void testCreateNoShowChargeJob() throws Exception {
        ManualChargeJob j = new ManualChargeJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingRef( "HWL-551-299878913" );
        j.setAmount( new BigDecimal( "17.16" ) );
        j.setMessage( "No-show charge." );
        dao.insertJob( j );

        j = new ManualChargeJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingRef( "HWL-551-310580371" );
        j.setAmount( new BigDecimal( "13.2" ) );
        j.setMessage( "No-show charge." );
        dao.insertJob( j );
    }

    @Test
    public void testAgodaChargeJob() throws Exception {
        AgodaChargeJob j = new AgodaChargeJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingRef( "AGO-236618912" );
        Calendar checkinDate = Calendar.getInstance();
        checkinDate.set( Calendar.DATE, 29 );
        checkinDate.set( Calendar.MONTH, Calendar.OCTOBER );
        checkinDate.set( Calendar.YEAR, 2017 );
        j.setCheckinDate( checkinDate.getTime() );
        dao.insertJob( j );
    }

    @Test
    public void testAgodaNoChargeNoteJob() throws Exception {
        AgodaNoChargeNoteJob j = new AgodaNoChargeNoteJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingRef( "AGO-205348018" );
        Calendar bookedDate = Calendar.getInstance();
        bookedDate.set( Calendar.DATE, 25 );
        bookedDate.set( Calendar.MONTH, Calendar.SEPTEMBER );
        bookedDate.set( Calendar.YEAR, 2017 );
        j.setBookedDate( bookedDate.getTime() );
        dao.insertJob( j );
    }

    @Test
    public void testCreateAgodaNoChargeNoteJob() throws Exception {
        CreateAgodaNoChargeNoteJob j = new CreateAgodaNoChargeNoteJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testCreateCopyCardDetailsFromLHtoCBJob() throws Exception {
        createCopyCardDetailsFromLHtoCBJob( "HWL-552-365677363", LocalDate.parse( "2018-06-12" ));
    }

    private void createCopyCardDetailsFromLHtoCBJob( String bookingRef, LocalDate checkinDate ) {
        CopyCardDetailsFromLHJob j = new CopyCardDetailsFromLHJob();
        j.setBookingRef( bookingRef );
        j.setCheckinDate( Date.from( checkinDate.atStartOfDay( ZoneId.systemDefault() ).toInstant() ) );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testCreateCopyCardDetailsFromLHtoCBJobs() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.MONTH, Calendar.MAY );
        c.set( Calendar.DATE, 25 );
        createCopyCardDetailsFromLHtoCBJobs( c.getTime(), 135 );
    }
    
    @Test
    public void testCreateUpdateLittleHotelierSettingsJob() throws Exception {
        UpdateLittleHotelierSettingsJob j = new UpdateLittleHotelierSettingsJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }
    
    private void createCopyCardDetailsFromLHtoCBJobs( Date startDate, int daysAhead ) {
        Calendar c = Calendar.getInstance();
        c.setTime( startDate );
        for( int i = 0; i < daysAhead; i++ ) {
            for ( String br : dao.fetchDistinctBookingsByCheckinDate( 1, c.getTime() ) ) {
                if ( br.startsWith( "LH" ) ) {
                    LOGGER.info( "Creating job for " + br );
                    CopyCardDetailsFromLHJob j = new CopyCardDetailsFromLHJob();
                    j.setBookingRef( br );
                    j.setCheckinDate( c.getTime() );
                    j.setStatus( JobStatus.aborted );
                    dao.insertJob( j );
                }
            }
            c.add( Calendar.DATE, 1 );
        }
    }

    @Test
    public void testCreateCopyCardDetailsToCloudbedsJob() throws Exception {
        CreateCopyCardDetailsToCloudbedsJob j = new CreateCopyCardDetailsToCloudbedsJob();
        j.setStatus( JobStatus.submitted );
        j.setBookingDate( LocalDate.now().withDayOfMonth( 11 ) );
        j.setDaysAhead( 4 );
        dao.insertJob( j );
    }

    @Test
    public void testCopyCardDetailsToCloudbedsJob() throws Exception {
        CopyCardDetailsToCloudbedsJob j = new CopyCardDetailsToCloudbedsJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "27500065" );
        dao.insertJob( j );
    }

    @Test
    public void testChargeNonRefundableBookingJob() throws Exception {
        ChargeNonRefundableBookingJob j = new ChargeNonRefundableBookingJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "380317840" );
        dao.insertJob( j );
    }

    @Test
    public void testCreateChargeNonRefundableBookingJob() throws Exception {
        CreateChargeNonRefundableBookingJob j = new CreateChargeNonRefundableBookingJob();
        autowireBeanFactory.autowireBean( j );
        j.setStatus( JobStatus.submitted );
        j.setBookingDate( LocalDate.now().minusDays( 3 ) );
        j.setDaysAhead( 4 );
        j.processJob();
    }
    
    @Test
    public void testCreateAllocationScraperWorkerJob() throws Exception {
        // dumps all bookings between date range
        LocalDate currentDate = LocalDate.parse("2018-05-25");
        LocalDate endDate = LocalDate.parse( "2018-10-06" );
        while ( currentDate.isBefore( endDate ) ) {
            CloudbedsAllocationScraperWorkerJob workerJob = new CloudbedsAllocationScraperWorkerJob();
            workerJob.setStatus( JobStatus.submitted );
            workerJob.setAllocationScraperJobId( 440052 ); // dbpurge job (no recs)
            workerJob.setStartDate( currentDate );
            workerJob.setEndDate( currentDate.plusDays( 7 ) );
            dao.insertJob( workerJob );
            currentDate = currentDate.plusDays( 7 ); // calendar page shows 2 weeks at a time
        }
    }

    @Test
    public void testCreateBookingReportJob() throws Exception {
        // dumps all bookings between date range
        LocalDate currentDate = LocalDate.parse("2018-05-28");
        LocalDate endDate = LocalDate.parse( "2018-10-23" );
        while ( currentDate.isBefore( endDate ) ) {
            BookingReportJob workerJob = new BookingReportJob();
            workerJob.setStatus( JobStatus.submitted );
            workerJob.setStartDate( currentDate );
            workerJob.setEndDate( currentDate.plusDays( 4 ) );
            dao.insertJob( workerJob );
            currentDate = currentDate.plusDays( 5 ); // dates are inclusive, +1
        }
    }

    @Test
    public void testCreateBDCMarkCreditCardInvalidJob() throws Exception {
        BDCMarkCreditCardInvalidJob j = new BDCMarkCreditCardInvalidJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "23996337" );
        dao.insertJob( j );
    }

    @Test
    public void testProcessSagepayTransactionJob() throws Exception {
        ProcessSagepayTransactionJob j = new ProcessSagepayTransactionJob();
        j.setStatus( JobStatus.submitted );
        j.setTxnId( 252 );
        dao.insertJob( j );
    }

    @Test
    public void testSendEmailJobs() throws Exception {
        SendDepositChargeDeclinedEmailJob j1 = new SendDepositChargeDeclinedEmailJob();
        j1.setReservationId( "10569063" );
        j1.setAmount( BigDecimal.ONE );
        j1.setPaymentURL( "https://pay.macbackpackers.com/booking/CRH/FFF1234" );
        j1.setStatus( JobStatus.submitted );
        dao.insertJob( j1 );

        SendDepositChargeSuccessfulEmailJob j2 = new SendDepositChargeSuccessfulEmailJob();
        j2.setReservationId( "10569063" );
        j2.setAmount( new BigDecimal( "2" ) );
        j2.setStatus( JobStatus.submitted );
        dao.insertJob( j2 );

        SendHostelworldLateCancellationEmailJob j3 = new SendHostelworldLateCancellationEmailJob();
        j3.setReservationId( "10569063" );
        j3.setAmount( new BigDecimal( "3" ) );
        j3.setStatus( JobStatus.submitted );
        dao.insertJob( j3 );

        SendNonRefundableDeclinedEmailJob j4 = new SendNonRefundableDeclinedEmailJob();
        j4.setReservationId( "10569063" );
        j4.setAmount( new BigDecimal( "4" ) );
        j4.setPaymentURL( "https://pay.macbackpackers.com/booking/CRH/F001234" );
        j4.setStatus( JobStatus.submitted );
        dao.insertJob( j4 );

        SendNonRefundableSuccessfulEmailJob j5 = new SendNonRefundableSuccessfulEmailJob();
        j5.setReservationId( "10569063" );
        j5.setAmount( new BigDecimal( "5" ) );
        j5.setStatus( JobStatus.submitted );
        dao.insertJob( j5 );
    }

    @Test
    public void testSendTemplatedEmailJob() {
        SendTemplatedEmailJob j = new SendTemplatedEmailJob();
        autowireBeanFactory.autowireBean( j );
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put( "\\[charge_amount\\]", "12.09" );
        replaceMap.put( "\\[payment URL\\]", "https://pay.here.now/CLKF285" );
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "10568885" );
        j.setEmailTemplate( CloudbedsScraper.TEMPLATE_COVID19_CLOSING );
        j.setReplacementMap( replaceMap );
        Assert.assertEquals( "12.09", j.getReplacementMap().get( "\\[charge_amount\\]" ) );
        Assert.assertEquals( "https://pay.here.now/CLKF285", j.getReplacementMap().get( "\\[payment URL\\]" ) );
        dao.insertJob( j );
    }

    @Test
    public void createCovid19CancelBookingJobs() throws Exception {
        String rawstring = IOUtils.toString( getClass().getClassLoader().getResourceAsStream( "covid19-cancellations.txt" ),
                Charset.defaultCharset() );
        Arrays.asList( rawstring.split( "\n" ) ).stream()
                .filter( line -> false == line.startsWith( "#" ) )
                .forEach( res -> { 
                    LOGGER.info( "Creating job for reservation " + res ); 
                    CancelBookingJob j = new CancelBookingJob();
                    j.setStatus( JobStatus.aborted ); // enable manually
                    j.setReservationId( res );
                    j.setNote( "Covid-19 - forced cancellation." );
                    dao.insertJob( j );
                } );
    }
    
    @Test
    public void testCreateSendTemplatedEmailJob() throws Exception {
        SendTemplatedEmailJob j = new SendTemplatedEmailJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "34802644" );
        j.setNoteArchived( true );
        j.setEmailTemplate( "COVID-19 Guidance Update" );
        dao.insertJob( j );
    }

    @Test
    public void testCreateSendPaymentSuccessfulTemplatedEmailJob() throws Exception {
        SendTemplatedEmailJob j = new SendTemplatedEmailJob();
        autowireBeanFactory.autowireBean( j );
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put( "\\[charge_amount\\]", "88.00" );
        replaceMap.put( "\\[last four digits\\]", "5775" );
        j.setEmailTemplate( "Payment Successful" );
        j.setReservationId( "65929389" );
        j.setReplacementMap( replaceMap );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testCreateSendPaymentDeclinedTemplatedEmailJob() throws Exception {
        SendTemplatedEmailJob j = new SendTemplatedEmailJob();
        autowireBeanFactory.autowireBean( j );
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put( "\\[charge amount\\]", "99.00" );
        replaceMap.put( "\\[payment URL\\]", "https://pay.macbackpackers.com/booking/CRH/WH6QLN2" );
        j.setEmailTemplate( "Payment Declined" );
        j.setReservationId( "67665909" );
        j.setReplacementMap( replaceMap );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
    }

    @Test
    public void testSendCovidPrestayEmailJob() throws Exception {
        SendCovidPrestayEmailJob j = new SendCovidPrestayEmailJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "10568950" );
        dao.insertJob( j );
    }

    @Test
    public void testSendPaymentLinkEmailJob() throws Exception {
        SendPaymentLinkEmailJob j = new SendPaymentLinkEmailJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "11968561" );
        dao.insertJob( j );
    }

    @Test
    public void testCreateFixedRateReservationJob() throws Exception {

        // setup the job
        CreateFixedRateReservationJob j = new CreateFixedRateReservationJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "38059779" );
        j.setCheckinDate( LocalDate.parse("2021-01-11") );
        j.setCheckoutDate( LocalDate.parse("2021-01-18") );
        j.setRatePerDay( new BigDecimal( 10 ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testVerifyGoogleAssistantLoggedInJob() throws Exception {
        VerifyGoogleAssistantLoggedInJob job = new VerifyGoogleAssistantLoggedInJob();
        autowireBeanFactory.autowireBean( job );
        job.processJob();
    }

    @Test
    public void testVerifyAlexaLoggedInJob() throws Exception {
        VerifyAlexaLoggedInJob job = new VerifyAlexaLoggedInJob();
        autowireBeanFactory.autowireBean( job );
        job.processJob();
    }

    @Test
    public void testAddArchiveAllTransactionNotesJob() {
        ArchiveAllTransactionNotesJob j = new ArchiveAllTransactionNotesJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( "87742801" );
        dao.insertJob( j );
    }

//    @Test
//    public void testCreateConnectToCalendarWSSJob() throws Exception {
//        ConnectToCalendarWSSJob j = new ConnectToCalendarWSSJob();
//        j.setStatus( JobStatus.submitted );
//        dao.insertJob( j );
//    }
//
//    @Test
//    public void testLoadCloudbedsCalendarJob() throws Exception {
//        LoadCloudbedsCalendarJob j = new LoadCloudbedsCalendarJob();
//        j.setStatus( JobStatus.submitted );
//        dao.insertJob( j );
//    }
//
}
