
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Inserts all cancelled bookings in LH for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ScrapeCancelledBookingsJob" )
public class ScrapeCancelledBookingsJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    @Transient
    private BookingsPageScraper bookingScraper;

    @Autowired
    @Transient
    private WordPressDAO wordpressDAO;

    @Override
    public void resetJob() throws Exception {
        wordpressDAO.deleteCancelledAllocations( getAllocationScraperJobId(), getCheckinDateStart(), getCheckinDateEnd() );
    }

    @Override
//    @Transactional
    public void processJob() throws Exception {
        // we just need to scrape new data for the given date range
        bookingScraper.insertCancelledBookings( webClient, getAllocationScraperJobId(), 
                getCheckinDateStart(), getCheckinDateEnd() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    /**
     * Gets the (start) checkin date to scrape the data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDateStart() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date_start" ) );
    }

    /**
     * Sets the (start) checkin date to scrape data for.
     * 
     * @param checkinDateFrom the checkin date (inclusive) in YYYY-MM-DD format
     */
    public void setCheckinDateStart( Date checkinDateStart ) {
        setParameter( "checkin_date_start", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDateStart ) );
    }

    /**
     * Gets the (end) checkin date to scrape the data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDateEnd() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date_end" ) );
    }

    /**
     * Sets the (end) checkin date to scrape data for.
     * 
     * @param checkinDateEnd the checkin date (inclusive) in YYYY-MM-DD format
     */
    public void setCheckinDateEnd( Date checkinDateEnd ) {
        setParameter( "checkin_date_end", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDateEnd ) );
    }

    /**
     * Returns the parent AllocationScraperJob that created this one.
     * 
     * @return id of AllocationScraperJob
     */
    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }
    
    /**
     * Sets the parent AllocationScraperJob that created this one.
     * 
     * @param jobId id of AllocationScraperJob
     */
    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

}
