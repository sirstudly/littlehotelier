package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.scrapers.AllocationsPageScraper;

/**
 * Job that updates the housekeeping tables for the given date
 *
 */
@Component
@Scope( "prototype" )
public class HousekeepingJob extends AbstractJob {
    
    @Autowired
    private AllocationsPageScraper allocationScraper;
    
    @Override
    public void processJob() throws Exception {
        // we just need to scrape new data for the given date (and previous day)
        // the PHP form will do the rest
        Date selectedDate = getSelectedDate();
        Calendar dayBefore = Calendar.getInstance();
        dayBefore.setTime( selectedDate );
        dayBefore.add( Calendar.DATE, -1 );
        allocationScraper.dumpAllocationsBetween( getId(), dayBefore.getTime(), selectedDate, false );
    }
    
    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getSelectedDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "selected_date" ) );
    }
    
}