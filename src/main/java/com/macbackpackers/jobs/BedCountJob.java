
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.AllocationsPageScraper;

/**
 * Job that updates the data required for the bedcount report for the given date
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BedCountJob" )
public class BedCountJob extends AbstractJob {

    @Autowired
    @Transient
    private AllocationsPageScraper allocationScraper;

    @Override
    public void processJob() throws Exception {
        // we just need to scrape the data including the given date
        // the PHP form will do the rest
        Date selectedDate = getSelectedDate();
        Calendar dayBefore = Calendar.getInstance();
        dayBefore.setTime( selectedDate );
        dayBefore.add( Calendar.DATE, -7 ); // go back a week
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
