
package com.macbackpackers.services;

import java.util.Date;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.TestHarnessDAO;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.jobs.CreateConfirmDepositAmountsJob;
import com.macbackpackers.jobs.GroupBookingsReportJob;
import com.macbackpackers.jobs.ScrapeReservationsBookedOnJob;
import com.macbackpackers.jobs.SplitRoomReservationReportJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;
import com.macbackpackers.scrapers.BookingsPageScraper;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ProcessorServiceTest {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    ProcessorService processorService;

    @Autowired
    WordPressDAO dao;

    @Autowired
    TestHarnessDAO testDAO;

    @Before
    public void setUp() {
        LOGGER.info( "deleting test data" );
        testDAO.deleteAllTransactionalData();
    }

    @Test
    public void testAllocationScraperJob() throws Exception {

        // setup a job to scrape allocation info
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-06-15 00:00:00" );
        j.setParameter( "end_date", "2015-06-16 00:00:00" );
        j.setParameter( "test_mode", "true" );
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
        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.completed );
        dao.insertJob( j );

        // setup the job
        j = new CreateConfirmDepositAmountsJob();
        j.setStatus( JobStatus.submitted );
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

}
