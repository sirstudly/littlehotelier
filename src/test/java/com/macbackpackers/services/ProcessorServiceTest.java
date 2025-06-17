package com.macbackpackers.services;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.ArchiveAllTransactionNotesJob;
import com.macbackpackers.jobs.BedCountReportJob;
import com.macbackpackers.jobs.CreatePrepaidRefundJob;
import com.macbackpackers.jobs.PrepaidRefundJob;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob;
import com.macbackpackers.jobs.BedCountJob;
import com.macbackpackers.jobs.BookingReportJob;
import com.macbackpackers.jobs.CancelBookingJob;
import com.macbackpackers.jobs.ChargeNonRefundableBookingJob;
import com.macbackpackers.jobs.CloudbedsAllocationScraperWorkerJob;
import com.macbackpackers.jobs.CopyCardDetailsToCloudbedsJob;
import com.macbackpackers.jobs.CreateChargeNonRefundableBookingJob;
import com.macbackpackers.jobs.CreateCopyCardDetailsToCloudbedsJob;
import com.macbackpackers.jobs.CreateDepositChargeJob;
import com.macbackpackers.jobs.CreateFixedRateReservationJob;
import com.macbackpackers.jobs.CreatePrepaidChargeJob;
import com.macbackpackers.jobs.CreateTestGuestCheckoutEmailJob;
import com.macbackpackers.jobs.DbPurgeJob;
import com.macbackpackers.jobs.DepositChargeJob;
import com.macbackpackers.jobs.DumpHostelworldBookingsByArrivalDateJob;
import com.macbackpackers.jobs.DumpHostelworldBookingsByBookedDateJob;
import com.macbackpackers.jobs.GroupBookingsReportJob;
import com.macbackpackers.jobs.PrepaidChargeJob;
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
import com.macbackpackers.scrapers.CloudbedsScraper;

import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_PAYMENT_DECLINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings( "deprecation" )
@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ProcessorServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    ProcessorService processorService;

    @Autowired
    WordPressDAO dao;

    @Autowired
    AutowireCapableBeanFactory autowireBeanFactory;
    
    @BeforeEach
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
        assertEquals( JobStatus.completed, jobVerify.getStatus() );
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
        assertEquals( JobStatus.completed, jobVerify.getStatus() );
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
        assertEquals( JobStatus.completed, jobVerify.getStatus() );
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
        assertEquals( JobStatus.completed, jobVerify.getStatus() );
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
        assertEquals( JobStatus.completed, jobVerify.getStatus() );

        assertThrows( EmptyResultDataAccessException.class, () -> {
            dao.fetchJobById( oldJobId ); // this should've been deleted
        } );
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
    public void testDumpHostelworldBookingsByArrivalDateJob() throws Exception {
        Calendar checkinDate = Calendar.getInstance();
        checkinDate.add( Calendar.DATE, 12 );
        for ( int i = 0; i < 120; i++ ) {
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
        j.setReason( "Waived fees" );
        j.setAmount( new BigDecimal( "91.08" ) );
        dao.insertJob( j );
    }

    @Test
    public void testCreateUpdateLittleHotelierSettingsJob() throws Exception {
        UpdateLittleHotelierSettingsJob j = new UpdateLittleHotelierSettingsJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
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
        LocalDate currentDate = LocalDate.parse( "2018-05-25" );
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
        LocalDate currentDate = LocalDate.parse( "2018-05-28" );
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
        assertEquals( "12.09", j.getReplacementMap().get( "\\[charge_amount\\]" ) );
        assertEquals( "https://pay.here.now/CLKF285", j.getReplacementMap().get( "\\[payment URL\\]" ) );
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
        j.setEmailTemplate( TEMPLATE_PAYMENT_DECLINED );
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
        j.setCheckinDate( LocalDate.parse( "2021-01-11" ) );
        j.setCheckoutDate( LocalDate.parse( "2021-01-18" ) );
        j.setRatePerDay( new BigDecimal( 10 ) );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();

        // verify that the job completed successfully
        Job jobVerify = dao.fetchJobById( jobId );
        assertEquals( JobStatus.completed, jobVerify.getStatus() );
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

    @Test
    public void testRunJob() throws Exception {
        AbstractJob jobToRun = dao.fetchJobById( 23336 );
        autowireBeanFactory.autowireBean( jobToRun );
        jobToRun.processJob();
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
}
