
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.HostelworldScraper;

/**
 * Do the arrivals for the different booking sites correspond with what is in Little Hotelier? This
 * job will login to each of the various booking engine sites and dump their data and compare with
 * what is in Little Hotelier. Any discrepancies will be identified in a report.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DiffBookingEnginesJob" )
public class DiffBookingEnginesJob extends AbstractJob {

    @Autowired
    @Transient
    private AllocationsPageScraper allocationScraper;

    @Autowired
    @Transient
    private HostelworldScraper hwScraper;

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Override
    @Transactional
    public void processJob() throws Exception {

        // we just need to scrape new data for the given date (and previous day)
        Date checkinDate = getCheckinDate();
        Calendar dayBefore = Calendar.getInstance();
        dayBefore.setTime( checkinDate );
        dayBefore.add( Calendar.DATE, -1 );
        allocationScraper.dumpAllocationsBetween( getId(), dayBefore.getTime(), checkinDate, false );

        // and update the additional fields for the records
        for ( Date nextDate : dao.getCheckinDatesForAllocationScraperJobId( getId() ) ) {
            bookingsScraper.updateBookingsFor( getId(), nextDate, null );
        }

        // finally, don't forget about cancelled bookings
        bookingsScraper.insertCancelledBookingsFor( getId(), checkinDate, null );

        // now dump the data from hostelworld
        hwScraper.dumpBookingsForArrivalDate( getId(), checkinDate );
    }

    /**
     * Gets the checkin date to scrape the allocation data.
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    public Date getCheckinDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "checkin_date" ) );
    }

}
