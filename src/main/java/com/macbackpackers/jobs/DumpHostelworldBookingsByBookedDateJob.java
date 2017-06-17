
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.HostelworldScraper;

/**
 * Dumps all bookings scraped from the Hostelworld site booked on a particular date.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DumpHostelworldBookingsByBookedDateJob" )
public class DumpHostelworldBookingsByBookedDateJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient webClient;

    @Autowired
    @Transient
    private HostelworldScraper hwScraper;

    @Override
//    @Transactional
    public void processJob() throws Exception {

        // we just need to scrape new data for the given date
        hwScraper.dumpBookingsForBookedDate( webClient, getBookedDate() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    /**
     * Gets the booked date to scrape the data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getBookedDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "booked_date" ) );
    }

    /**
     * Sets the booked date to scrape data for.
     * 
     * @param bookedDate the booked date in YYYY-MM-DD format
     */
    public void setBookedDate( Date bookedDate ) {
        setParameter( "booked_date", AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( bookedDate ) );
    }

}
