
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import com.macbackpackers.config.LittleHotelierConfig;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.scrapers.HostelworldScraper;

/**
 * Dumps all bookings scraped from the Hostelworld site for a particular arrival date.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DumpHostelworldBookingsByArrivalDateJob" )
public class DumpHostelworldBookingsByArrivalDateJob extends AbstractJob {

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
        hwScraper.dumpBookingsForArrivalDate( webClient, getCheckinDate() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    /**
     * Gets the checkin date to scrape the data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDate() throws ParseException {
        return LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

    /**
     * Sets the checkin date to scrape data for.
     * 
     * @param checkinDate the checkin date in YYYY-MM-DD format
     */
    public void setCheckinDate( Date checkinDate ) {
        setParameter( "checkin_date", LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
    }

}
