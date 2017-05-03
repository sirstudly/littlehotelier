
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Job that scrapes the booking data for a particular date range and updates the allocation records
 * from a previous AllocationScraperJob.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BookingScraperJob" )
public class BookingScraperJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingScraper;

    @Override
    public void processJob() throws Exception {
        bookingScraper.updateBookingsBetween(
                getAllocationScraperJobId(), getCheckinDate(), getCheckinDate(), isTestMode() );
    }

    @Override
    public void finalizeJob() {
        bookingScraper.closeAllWindows(); // cleans up JS threads
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }

    /**
     * Gets the checkin date to scrape the allocation data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    /**
     * Sets the checkin date to scrape the allocation data.
     * 
     * @param checkinDate non-null date
     */
    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }

    /**
     * For testing, we may want to load from a previously serialised file (rather than scrape the
     * page again which takes about 10 minutes). Default value is false, but if the "test_mode"
     * parameter is set to true, then attempt to load from file first. If no file is found, then
     * scrape the page again.
     * 
     * @return true if test mode, false otherwise
     */
    public boolean isTestMode() {
        return "true".equalsIgnoreCase( getParameter( "test_mode" ) );
    }

}
