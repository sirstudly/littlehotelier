package com.macbackpackers.dao;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.BedChange;
import com.macbackpackers.beans.BedSheetEntry;
import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

public class WordPressDAOTest {

    Logger logger = LogManager.getLogger( getClass() );
    WordPressDAO dao;

    static final String username = "root";
    static final String password = "system??";

    private static final SimpleDateFormat DATE_FORMAT_YYYY_MM_DD = new SimpleDateFormat( "yyyy-MM-dd" );

    @Before
    public void setUp() throws Exception {
        dao = getTestWordPressDAO();
        setupTestEntries();
    }

    /**
     * Creates and connects to the wordpress db.
     * 
     * @return initialised DAO
     */
    public static WordPressDAO getTestWordPressDAO() {
        WordPressDAO result = new WordPressDAO( "jdbc:mysql://", "localhost", "3306", "wordpress701" );
        result.connect( username, password );
        return result;
    }

    @After
    public void tearDown() throws Exception {
        QueryRunner runner = new QueryRunner( dao.getDataSource() );
        runner.update( "TRUNCATE TABLE wp_bedsheets" );
    }

    // this indirectly tests the insert methods
    private void setupTestEntries() throws Exception {
        dao.insertBedSheetEntry( createBedSheetEntry( 3, "forty five", "Dollar", 2014, 4, 18,
                BedChange.THREE_DAY_CHANGE ) );
        dao.insertBedSheetEntry( createBedSheetEntry( 3, "42", "Boxers", 2014, 4, 18, BedChange.YES ) );
        dao.insertBedSheetEntry( createBedSheetEntry( 3, "42", "Thong", 2014, 4, 18, BedChange.NO ) );
        dao.insertBedSheetEntry( createBedSheetEntry( 3, "20", "Laphroaig", 2014, 4, 18, BedChange.YES ) );
        dao.insertBedSheetEntry( createBedSheetEntry( 3, "20", "Talisker", 2014, 4, 17, BedChange.THREE_DAY_CHANGE ) );
        dao.insertBedSheetEntry( createBedSheetEntry( 4, "22", "Dunvegan", 2014, 4, 18, BedChange.YES ) );
    }

    private static Calendar createCalendarDate( int year, int month, int date ) {
        Calendar result = Calendar.getInstance();
        result.set( Calendar.YEAR, year );
        result.set( Calendar.DATE, date );
        result.set( Calendar.MONTH, month );
        return result;
    }

    private static BedSheetEntry createBedSheetEntry( int jobId, String room, String bedName, int year, int month,
            int date, BedChange changeStatus ) {
        BedSheetEntry entry = new BedSheetEntry();
        entry.setJobId( jobId );
        entry.setRoom( room );
        entry.setBedName( bedName );
        Calendar checkoutDate = Calendar.getInstance();
        checkoutDate.set( Calendar.YEAR, year );
        checkoutDate.set( Calendar.DATE, date );
        checkoutDate.set( Calendar.MONTH, month );
        entry.setCheckoutDate( checkoutDate.getTime() );
        entry.setStatus( changeStatus );
        return entry;
    }

    private static BedSheetEntry selectBedSheetEntryByRoomAndBed( String room, String bed, List<BedSheetEntry> sheets ) {
        for( BedSheetEntry sheet : sheets ) {
            if ( sheet.getBedName().equals( bed ) && sheet.getRoom().equals( room ) ) {
                return sheet;
            }
        }
        Assert.fail( "Unable to find bed sheet entry for room " + room + " and bed " + bed );
        return null; // never gets here
    }

    private void assertBedSheetEntry( BedSheetEntry observed, int jobId, int year, int month, int date, BedChange change ) {
        Assert.assertEquals( jobId, observed.getJobId() );
        Assert.assertEquals( change, observed.getStatus() );
        Calendar observedDate = Calendar.getInstance();
        observedDate.setTime( observed.getCheckoutDate() );
        Assert.assertEquals( year, observedDate.get( Calendar.YEAR ) );
        Assert.assertEquals( month, observedDate.get( Calendar.MONTH ) );
        Assert.assertEquals( date, observedDate.get( Calendar.DATE ) );
        Assert.assertEquals( true, observed.getId() > 0 );
    }

    @Test
    public void testGetAllBedSheetEntriesForDate() throws Exception {

        // verify
        List<BedSheetEntry> changes = dao.getAllBedSheetEntriesForDate( 3, createCalendarDate( 2014, 4, 18 ).getTime() );

        Assert.assertEquals( 4, changes.size() );

        BedSheetEntry observed = selectBedSheetEntryByRoomAndBed( "forty five", "Dollar", changes );
        assertBedSheetEntry( observed, 3, 2014, 4, 18, BedChange.THREE_DAY_CHANGE );

        observed = selectBedSheetEntryByRoomAndBed( "42", "Boxers", changes );
        assertBedSheetEntry( observed, 3, 2014, 4, 18, BedChange.YES );

        observed = selectBedSheetEntryByRoomAndBed( "42", "Thong", changes );
        assertBedSheetEntry( observed, 3, 2014, 4, 18, BedChange.NO );

        observed = selectBedSheetEntryByRoomAndBed( "20", "Laphroaig", changes );
        assertBedSheetEntry( observed, 3, 2014, 4, 18, BedChange.YES );
    }

    @Test
    public void testDeleteAllBedSheetEntriesForJobId() throws Exception {
        dao.deleteAllBedSheetEntriesForJobId( 3 );
        List<BedSheetEntry> changes = dao.getAllBedSheetEntriesForDate( 4, createCalendarDate( 2014, 4, 18 ).getTime() );
        Assert.assertEquals( 1, changes.size() );

        // check entries are no longer present
        changes = dao.getAllBedSheetEntriesForDate( 3, createCalendarDate( 2014, 4, 18 ).getTime() );
        Assert.assertEquals( 0, changes.size() );

        dao.deleteAllBedSheetEntriesForJobId( 4 );
        changes = dao.getAllBedSheetEntriesForDate( 4, createCalendarDate( 2014, 4, 18 ).getTime() );
        Assert.assertEquals( 0, changes.size() );
    }

    @Test
    public void testInsertAllocation() throws Exception {

        Allocation alloc = new Allocation();
        alloc.setJobId( 3 );
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

        dao.insertAllocation( alloc );
    }

    @Test
    public void testInsertJob() throws Exception {
        Job j = new Job();
        j.setName( "my name" );
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        Assert.assertEquals( true, jobId > 0 );

        // now verify the results
        Job jobView = dao.getJobById( jobId );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getName(), jobView.getName() );
        Assert.assertEquals( j.getStatus(), jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNull( "last updated date not null", jobView.getLastUpdatedDate() );
    }

    @Test
    public void testGetNextJobToProcess() throws Exception {

        dao.deleteAllJobData(); // clear out data

        Job j = new Job();
        j.setName( "my name" );
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        logger.info( "created job " + jobId );
        Assert.assertEquals( true, jobId > 0 );

        // create a job that isn't complete
        Job j2 = new Job();
        j2.setName( "completed job" );
        j2.setStatus( JobStatus.completed );
        int jobId2 = dao.insertJob( j2 );
        logger.info( "created job " + jobId2 );

        // now verify the results
        // returns the first job created
        Job jobView = dao.getNextJobToProcess();
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getName(), jobView.getName() );
        Assert.assertEquals( j.getStatus(), jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNull( "last updated date not null", jobView.getLastUpdatedDate() );
    }

    @Test
    public void testUpdateJobStatus() throws Exception {
        Job j = new Job();
        j.setName( "my name" );
        j.setStatus( JobStatus.submitted );
        int jobId = dao.insertJob( j );

        // execute
        dao.updateJobStatus( jobId, JobStatus.processing, JobStatus.submitted );

        // now verify the results
        Job jobView = dao.getJobById( jobId );
        Assert.assertEquals( jobId, jobView.getId() );
        Assert.assertEquals( j.getName(), jobView.getName() );
        Assert.assertEquals( "processing", jobView.getStatus() );
        Assert.assertNotNull( "create date not found", jobView.getCreatedDate() );
        Assert.assertNotNull( "last updated date not found", jobView.getLastUpdatedDate() );
    }
}