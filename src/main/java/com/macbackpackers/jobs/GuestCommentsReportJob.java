
package com.macbackpackers.jobs;

import java.util.Date;
import java.util.List;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.MissingGuestComment;
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
        LOGGER.info( "Last completed allocation job: " + allocScraperJobId );
        if ( allocScraperJobId != null ) {
            List<MissingGuestComment> allocations = dao.getAllocationsWithoutEntryInGuestCommentsReport( allocScraperJobId );
            LOGGER.info( "Found " + allocations.size() + " allocations with unprocessed guest comments" );

            // now create a job to scrape and save the comment
            for ( MissingGuestComment alloc : allocations ) {
                insertGuestCommentSaveJob(
                        alloc.getReservationId(), 
                        alloc.getBookingReference(), 
                        alloc.getCheckinDate());
            }
        }
        else {
            LOGGER.warn( "AllocationScraperJob hasn't been run yet" );
        }
    }

    /**
     * Creates an additional job to update a single booking.
     * @param reservationId ID of reservation
     * @param bookingRef booking reference
     * @param checkinDate checkin date of reservation
     */
    private void insertGuestCommentSaveJob(int reservationId, String bookingRef, Date checkinDate) {
        GuestCommentSaveJob saveJob = new GuestCommentSaveJob();
        saveJob.setStatus( JobStatus.submitted );
        saveJob.setReservationId( reservationId );
        saveJob.setBookingRef( bookingRef );
        saveJob.setCheckinDate( checkinDate );
        dao.insertJob( saveJob );
    }

}
