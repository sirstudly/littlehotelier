
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
import com.macbackpackers.scrapers.HostelbookersScraper;
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
    private HostelbookersScraper hbScraper;

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Override
    @Transactional
    public void processJob() throws Exception {

        // we just need to scrape new data for the given date (include the previous week)
        Date checkinDate = getCheckinDate();
        Calendar weekBefore = Calendar.getInstance();
        weekBefore.setTime( checkinDate );
        weekBefore.add( Calendar.DATE, -7 );
        allocationScraper.dumpAllocationsBetween( getId(), weekBefore.getTime(), checkinDate, false );

        // and update the additional fields for the records
        for ( Date nextDate : dao.getCheckinDatesForAllocationScraperJobId( getId() ) ) {
            bookingsScraper.updateBookingsFor( getId(), nextDate, null );
        }

        // finally, don't forget about cancelled bookings
        // include all cancelled bookings +/- 2 weeks
        Calendar twoWeeksBefore = Calendar.getInstance();
        twoWeeksBefore.setTime( checkinDate );
        twoWeeksBefore.add( Calendar.DATE, -14 );

        Calendar twoWeeksAfter = Calendar.getInstance();
        twoWeeksAfter.setTime( checkinDate );
        twoWeeksAfter.add( Calendar.DATE, 14 );

        // split into 2 searches to avoid having too many results
        bookingsScraper.insertCancelledBookingsFor( getId(), twoWeeksBefore.getTime(), twoWeeksAfter.getTime(), null );

        // now dump the data from HW/HB
        hwScraper.dumpBookingsForArrivalDate( checkinDate );
        hbScraper.dumpBookingsForArrivalDate( checkinDate );
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
