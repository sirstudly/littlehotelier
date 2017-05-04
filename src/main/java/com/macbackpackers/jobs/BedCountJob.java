
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
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

    @Autowired
    @Transient
    @Qualifier( "webClientScriptingDisabled" )
    private WebClient webClient;

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    @Override
    public void processJob() throws Exception {
        // we just need to scrape the data including the given date
        // the PHP form will do the rest
        Date selectedDate = getSelectedDate();
        Calendar dayBefore = Calendar.getInstance();
        dayBefore.setTime( selectedDate );
        dayBefore.add( Calendar.DATE, -7 ); // go back a week
        allocationScraper.dumpAllocationsFrom( webClient, getId(), dayBefore.getTime() );
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
