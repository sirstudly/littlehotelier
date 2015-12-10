
package com.macbackpackers.jobs;

import java.math.BigInteger;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.scrapers.BookingsPageScraper;

/**
 * Updates the report table with all reservations where the guest has left a comment with the
 * booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.GuestCommentsReportJob" )
public class GuestCommentsReportJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingsPageScraper bookingsScraper;

    @Override
    public void processJob() throws Exception {

        // get any new reservation IDs we haven't seen yet from the last allocation scraper job
        Integer allocScraperJobId = dao.getLastCompletedAllocationScraperJobId();
        if ( allocScraperJobId != null ) {
            List<BigInteger> reservationIds = dao.getReservationIdsWithoutEntryInGuestCommentsReport( allocScraperJobId );

            // now scrape any user comments from the reservation and save it
            for ( BigInteger reservationId : reservationIds ) {
                String comment = bookingsScraper.getGuestCommentsForReservation( reservationId );
                dao.updateGuestCommentsForReservation( reservationId, comment );
            }
        }
        else {
            LOGGER.warn( "AllocationScraperJob hasn't been run yet" );
        }
    }

}
