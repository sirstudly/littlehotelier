
package com.macbackpackers.scrapers;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlunit.WebClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AllocationScraperJob;
import com.macbackpackers.jobs.HousekeepingJob;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class AllocationsPageScraperTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    AllocationsPageScraper scraper;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient webClient;

    @Autowired
    WordPressDAO dao;

    @Test
    public void testInsertAndRunHousekeepingJob() throws Exception {
        Job j = new HousekeepingJob();
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );

        // this should be the one we just added
        j = dao.getNextJobToProcess();
        Assert.assertEquals( HousekeepingJob.class, j.getClass() );
        Assert.assertEquals( JobStatus.processing, j.getStatus() );
        Assert.assertNotNull( "created date not initialised", j.getCreatedDate() );

        LOGGER.info( "Running bedsheet job for " + j.getCreatedDate() );

        // FIXME?
        // update our allocations for this job
        //        scraper.dumpPageForJob( j, j.getCreatedDate() );

        // we've done all the hard work
        dao.updateJobStatus( j.getId(), JobStatus.completed, JobStatus.processing );
    }

    @Test
    public void testDumpCalendarPage() throws Exception {
        Date july27 = AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( "2015-07-27" );

        Job j = new AllocationScraperJob();
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        scraper.dumpAllocationsFrom( webClient, jobId, july27 );
        dao.updateJobStatus( jobId, JobStatus.completed, JobStatus.submitted );
    }

    @Test
    public void testRandom() throws Exception {
        String style = "width:422px; left: -336px";
        Pattern left = Pattern.compile( "; left: ([\\-0-9]*)px;" );
        Matcher m = left.matcher( style );
        if ( m.find() ) {
            String leftOffset = m.group( 1 );
            LOGGER.info( "offset is " + leftOffset );
            if ( Integer.parseInt( leftOffset ) < 0 ) {
                LOGGER.warn( "offset off screen, skipping " );
            }
        }
        else {
            LOGGER.warn( "pattern not found" );
        }
    }

    @Test
    public void testPatternMatching() throws Exception {
        String value = "12-06 Touchy-Feely";

        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( value );
        String room = null, bed = null;
        if ( m.find() == false ) {
            LOGGER.warn( "Couldn't determine bed name from '" + value + "'. Is it a private?" );
            room = value;
        }
        else {
            room = m.group( 1 );
            bed = m.group( 2 );
        }
        Assert.assertEquals( "12", room );
        Assert.assertEquals( "06 Touchy-Feely", bed );
    }

}
