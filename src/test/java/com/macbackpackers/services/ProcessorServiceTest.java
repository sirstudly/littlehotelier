
package com.macbackpackers.services;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.Scheduler;
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
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.jobs.CreateConfirmDepositAmountsJob;
import com.macbackpackers.jobs.CreateDepositChargeJob;
import com.macbackpackers.jobs.CreateSendGuestCheckoutEmailJob;
import com.macbackpackers.jobs.CreateTestGuestCheckoutEmailJob;
import com.macbackpackers.jobs.DbPurgeJob;
import com.macbackpackers.jobs.DepositChargeJob;
import com.macbackpackers.jobs.GroupBookingsReportJob;
import com.macbackpackers.jobs.ScrapeReservationsBookedOnJob;
import com.macbackpackers.jobs.SendAllUnsentEmailJob;
import com.macbackpackers.jobs.SplitRoomReservationReportJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;
import com.macbackpackers.scrapers.BookingsPageScraper;

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
        Job j = new ConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "reservation_id", "1074445" );
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
        bookingDate.set( Calendar.DATE, 27 );
        bookingDate.set( Calendar.MONTH, Calendar.APRIL );
        bookingDate.set( Calendar.YEAR, 2017 );
        j.setBookingDate( bookingDate.getTime() );
        j.setBookingRef( "EXP-832394371" );
        dao.insertJob( j );
        
        // this should now run the job
        processorService.processJobs();
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
        Assert.assertThat( dj.getClass(), Matchers.is( AllocationScraperJob.class ) );
        Assert.assertThat( dj.getId(), Matchers.is( j.getId() - 1 ) );
    }
}
