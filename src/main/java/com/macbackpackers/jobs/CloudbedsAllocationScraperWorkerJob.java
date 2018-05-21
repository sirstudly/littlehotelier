
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Worker job that scrapes the allocation data for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CloudbedsAllocationScraperWorkerJob" )
public class CloudbedsAllocationScraperWorkerJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    @Override
    public void processJob() throws Exception {
        cloudbedsScraper.dumpAllocationsFrom( getId(), getStartDate(), getEndDate() );
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public LocalDate getStartDate() throws ParseException {
        return LocalDate.parse( getParameter( "start_date" ) );
    }

    /**
     * Sets the date to start scraping the allocation data (inclusive).
     * 
     * @param startDate non-null date parameter
     */
    public void setStartDate( LocalDate startDate ) {
        setParameter( "start_date", startDate.toString() );
    }

    /**
     * Gets the end date to scrape the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public LocalDate getEndDate() throws ParseException {
        return LocalDate.parse( getParameter( "end_date" ) );
    }

    /**
     * Sets the end date to scrape the allocation data (inclusive).
     * 
     * @param endDate non-null date parameter
     */
    public void setEndDate( LocalDate endDate ) {
        setParameter( "end_date", endDate.toString() );
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
