package com.macbackpackers.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;

/**
 * Ensures a pending {@link CalculateEdinburghVisitorLevyForBookingJob} exists when EVL is enabled,
 * so other jobs (e.g. non-refundable charge) can depend on it.
 */
@Component
public class EdinburghVisitorLevyJobSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger( EdinburghVisitorLevyJobSupport.class );

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private EdinburghVisitorLevyService edinburghVisitorLevyService;

    /**
     * When EVL is enabled, returns a pending EVL job for the reservation, creating one if needed.
     * When EVL is disabled, returns {@code null}.
     */
    public CalculateEdinburghVisitorLevyForBookingJob findOrCreatePendingJob( String reservationId ) {
        if ( false == edinburghVisitorLevyService.isEvlEnabled() ) {
            return null;
        }
        CalculateEdinburghVisitorLevyForBookingJob pending =
                dao.findPendingCalculateEdinburghVisitorLevyJobForReservation( reservationId );
        if ( pending != null ) {
            return pending;
        }
        LOGGER.info( "Creating CalculateEdinburghVisitorLevyForBookingJob for reservation {} (charge dependency)",
                reservationId );
        CalculateEdinburghVisitorLevyForBookingJob job = new CalculateEdinburghVisitorLevyForBookingJob();
        job.setStatus( JobStatus.submitted );
        job.setReservationId( reservationId );
        int jobId = dao.insertJob( job );
        return dao.fetchJobById( jobId, CalculateEdinburghVisitorLevyForBookingJob.class );
    }
}
