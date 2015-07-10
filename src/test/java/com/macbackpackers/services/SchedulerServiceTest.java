
package com.macbackpackers.services;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.NameValuePair;
import com.macbackpackers.beans.ScheduledJob;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.TestHarnessDAO;
import com.macbackpackers.jobs.SplitRoomReservationReportJob;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class SchedulerServiceTest {

    @Autowired
    SchedulerService service;

    @Autowired
    TestHarnessDAO testDAO;

    @Autowired
    Scheduler scheduler;

    @Before
    public void setUp() {
        // clear out test data
        testDAO.runSQL( "DELETE FROM wp_lh_scheduled_job_param" );
        testDAO.runSQL( "DELETE FROM wp_lh_scheduled_jobs" );
    }

    @Test
    public void testReloadScheduledJobs() throws Exception {
        long currentTime = System.currentTimeMillis();
        insertScheduledJob();
        service.reloadScheduledJobs();
        Thread.sleep( 65000 );
        scheduler.shutdown(); // otherwise, this test would never end

        // find the number of actual job that were created during that time...
        List<SplitRoomReservationReportJob> createdJobs =
                testDAO.list( "FROM SplitRoomReservationReportJob WHERE lastUpdatedDate >= :lastUpdatedDate",
                        Arrays.asList( new SimpleNameValuePair( "lastUpdatedDate", new Timestamp( currentTime ) ) ),
                        SplitRoomReservationReportJob.class );

        Assert.assertEquals( "size", 4, createdJobs.size() );
        for ( SplitRoomReservationReportJob j : createdJobs ) {
            // check job parameters were copied over correctly
            Assert.assertEquals( "allocation_scraper_job_id", 1, j.getAllocationScraperJobId() );
            Assert.assertEquals( "test_mode", "true", j.getParameter( "test_mode" ) );
        }
    }

    private void insertScheduledJob() {
        ScheduledJob job = new ScheduledJob();
        job.setActive( true );
        job.setClassname( "com.macbackpackers.jobs.SplitRoomReservationReportJob" );
        job.setCronSchedule( "*/15 * * * * ?" );
        job.setParameter( "allocation_scraper_job_id", "1" );
        job.setParameter( "test_mode", "true" );
        testDAO.save( job );
    }

    class SimpleNameValuePair implements NameValuePair {

        String name;
        Object value;

        public SimpleNameValuePair( String name, Object value ) {
            setName( name );
            setValue( value );
        }

        public void setName( String name ) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public void setValue( Object value ) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }

    }
}
