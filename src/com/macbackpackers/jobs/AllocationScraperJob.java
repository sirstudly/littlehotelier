package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.AllocationsPageScraper;

/**
 * Job that scrapes the allocation data for a particular date range.
 *
 */
@Component
@Scope( "prototype" )
public class AllocationScraperJob extends AbstractJob {
    
    @Autowired
    private AllocationsPageScraper allocationScraper;
    
    @Autowired
    private WordPressDAO dao;
    
    @Override
    public void processJob() throws Exception {
        allocationScraper.dumpAllocationsBetween( getId(), getStartDate(), getEndDate(), isTestMode() );
        dao.runSplitRoomsReservationsReport( getId() );
    }
    
    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getStartDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }
    
    /**
     * Gets the date to stop scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getEndDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "end_date" ) );
    }
    
    /**
     * For testing, we may want to load from a previously serialised file (rather than scrape the page again
     * which takes about 10 minutes). Default value is false, but if the "test_mode" parameter is set
     * to true, then attempt to load from file first. If no file is found, then scrape the page again.
     * 
     * @return true if test mode, false otherwise
     */
    private boolean isTestMode() {
        return "true".equalsIgnoreCase( getParameter( "test_mode" ) );
    }

}