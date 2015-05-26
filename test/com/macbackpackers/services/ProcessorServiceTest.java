package com.macbackpackers.services;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
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
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.ConfirmDepositAmountsJob;
import com.macbackpackers.jobs.UnpaidDepositReportJob;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class ProcessorServiceTest {
    
    private final Logger LOGGER = LogManager.getLogger(getClass());
    
    @Autowired
    ProcessorService processorService;

    @Autowired
    WordPressDAO dao;
    
    @Before
    public void setUp() {
//        LOGGER.info( "deleting test data" );
//        dao.deleteAllTransactionalData();
    }
    
    @Test
    public void testProcess() throws Exception {
        
        // setup a job to scrape allocation info
        Job j = new Job();
        j.setClassName( AllocationScraperJob.class.getName() );
        j.setStatus( JobStatus.submitted );
        j.setParameter( "start_date", "2015-05-23 00:00:00" );
        j.setParameter( "end_date", "2015-06-04 00:00:00" );
        j.setParameter( "test_mode", "true" );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();
        
        // verify that the job completed successfully
        Job jobVerify = dao.getJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }
    
    @Test
    public void testUnpaidDepositReportJob() throws Exception {
        
        // setup a job to scrape allocation info
        Job j = new Job();
        j.setClassName( UnpaidDepositReportJob.class.getName() );
        j.setStatus( JobStatus.submitted );
        j.setParameter( "allocation_scraper_job_id", "21" );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();
        
        // verify that the job completed successfully
        Job jobVerify = dao.getJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

    @Test
    public void testConfirmDepositAmountsJob() throws Exception {
        
        // setup a job to scrape allocation info
        Job j = new Job();
        j.setClassName( ConfirmDepositAmountsJob.class.getName() );
        j.setStatus( JobStatus.submitted );
        j.setParameter( "reservation_id", "1074445" );
        int jobId = dao.insertJob( j );

        // this should now run the job
        processorService.processJobs();
        
        // verify that the job completed successfully
        Job jobVerify = dao.getJobById( jobId );
        Assert.assertEquals( JobStatus.completed, jobVerify.getStatus() );
    }

}