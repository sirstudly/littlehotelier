
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Job that updates the housekeeping tables for the given date
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.HousekeepingJob" )
public class HousekeepingJob extends AbstractJob {

    @Autowired
    @Transient
    private AllocationsPageScraper allocationScraper;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClientScriptingDisabled" )
    private WebClient webClient;

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    @Override
    public void processJob() throws Exception {
        if ( dao.isCloudbeds() ) {
            LocalDate selectedDate = getSelectedLocalDate();
            cloudbedsScraper.dumpAllocationsFrom(
                    getId(), selectedDate.minusDays( 1 ), selectedDate.plusDays( 1 ) );
        }
        else {
            // we just need to scrape new data for the given date (and previous day)
            // the PHP form will do the rest
            Date selectedDate = getSelectedDate();
            Calendar dayBefore = Calendar.getInstance();
            dayBefore.setTime( selectedDate );
            dayBefore.add( Calendar.DATE, -1 );
            allocationScraper.dumpAllocationsFrom( webClient, getId(), dayBefore.getTime() );
        }
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
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

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private LocalDate getSelectedLocalDate() {
        return LocalDate.parse( getParameter( "selected_date" ).substring( 0, 10 ) );
    }

}
