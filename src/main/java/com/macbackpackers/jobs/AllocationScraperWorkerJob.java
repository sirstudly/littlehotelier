
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.AllocationsPageScraper;

/**
 * Worker job that scrapes the allocation data for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AllocationScraperWorkerJob" )
public class AllocationScraperWorkerJob extends AbstractJob {

    @Autowired
    @Transient
    private AllocationsPageScraper allocationScraper;

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    @Override
    public void finalizeJob() {
        allocationScraper.closeAllWindows(); // cleans up JS threads
    }

    @Override
    public void processJob() throws Exception {
        allocationScraper.dumpAllocationsFrom( getId(), getStartDate(), isTestMode() );
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getStartDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }

    /**
     * Sets the date to start scraping the allocation data (inclusive).
     * 
     * @param startDate non-null date parameter
     */
    public void setStartDate( Date startDate ) {
        setParameter( "start_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( startDate ) );
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

    /**
     * For testing, we may want to load from a previously serialised file (rather than scrape the
     * page again which takes about 10 minutes). Default value is false, but if the "test_mode"
     * parameter is set to true, then attempt to load from file first. If no file is found, then
     * scrape the page again.
     * 
     * @return true if test mode, false otherwise
     */
    private boolean isTestMode() {
        return "true".equalsIgnoreCase( getParameter( "test_mode" ) );
    }

}
