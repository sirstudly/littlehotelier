
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that scrapes the reservation data for a particular date range.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BookingReportJob" )
public class BookingReportJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Override
    public void resetJob() throws Exception {
        dao.deleteBookingReport( getId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    @Override
    public void processJob() throws Exception {
        cloudbedsService.dumpBookingReportFrom( webClient, getId(), getStartDate(), getEndDate() );
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

}
