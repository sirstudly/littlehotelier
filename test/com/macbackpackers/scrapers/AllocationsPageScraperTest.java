package com.macbackpackers.scrapers;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.dao.WordPressDAOTest;

public class AllocationsPageScraperTest {

	Logger logger = LogManager.getLogger(getClass());
	AllocationsPageScraper scraper = new AllocationsPageScraper();
	
	static WordPressDAO dao;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
	    dao = WordPressDAOTest.getTestWordPressDAO(); // only needs to be done once
	}
	
	@Before
	public void setUp() throws Exception {
	    scraper.setWordPressDAO( dao );
	}

    @Test
    public void testDoLogin() throws Exception {
        scraper.doLogin();
    }

    @Test
    public void testGoToCalendarPage() throws Exception {
        scraper.doLogin();
        Calendar july2 = Calendar.getInstance();
        july2.set( Calendar.DATE, 2 );
        july2.set( Calendar.MONTH, 6 );
        july2.set( Calendar.YEAR, 2014 );
        logger.info( july2.getTime() );
        scraper.goToCalendarPage( july2.getTime() );
    }

    @Test
    public void testInsertAndRunHousekeepingJob() throws Exception {
        Job j = new Job();
        j.setName( "bedsheets" );
        j.setStatus( JobStatus.submitted );
        dao.insertJob( j );
        
        // this should be the one we just added
        j = dao.getNextJobToProcess();
        Assert.assertEquals( "bedsheets", j.getName() );
        Assert.assertEquals( "submitted", j.getStatus() );
        Assert.assertNotNull( "created date not initialised", j.getCreatedDate() );
        
        scraper.doLogin();
        logger.info( "Running bedsheet job for " + j.getCreatedDate() );
        HtmlPage calendarPage = scraper.goToCalendarPage( j.getCreatedDate() );
        
        // update our allocations for this job
        scraper.dumpPageForJob( j, calendarPage );
        
        // we've done all the hard work
        dao.updateJobStatus( j.getId(), JobStatus.completed, JobStatus.processing );
    }

    @Test
	public void testDumpCalendarPage() throws Exception {
        Job job = dao.getNextJobToProcess();
		scraper.dumpPageForJob( job, null ); // use serialised file
        dao.updateJobStatus( job.getId(), JobStatus.completed, JobStatus.processing );
	}

	@Test
	public void testRandom() throws Exception {
        String style = "width:422px; left: -336px";
        Pattern left = Pattern.compile( "; left: ([\\-0-9]*)px;");
        Matcher m = left.matcher( style );
        if( m.find() ) {
            String leftOffset = m.group(1);
            logger.info( "offset is " + leftOffset );
            if( Integer.parseInt( leftOffset ) < 0 ) {
                logger.warn( "offset off screen, skipping " );
            }
        }
        else {
            logger.warn( "pattern not found" );
        }
	}
	
	@Test
	public void testPatternMatching() throws Exception {
	    String value = "12-06 Touchy-Feely";
	    
        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( value );
        String room = null, bed = null;
        if ( m.find() == false ) {
            logger.warn( "Couldn't determine bed name from '" + value + "'. Is it a private?" );
            room = value;
        } else {
            room = m.group( 1 );
            bed = m.group( 2 );
        }
        Assert.assertEquals( "12", room );
        Assert.assertEquals( "06 Touchy-Feely", bed );
	}

}