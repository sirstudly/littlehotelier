
package com.macbackpackers.jobs;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.CloudbedsService;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Job that creates {@link PrepaidRefundJob}s for all prepaid bookings with virtual CCs that need
 * to be refunded.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreatePrepaidRefundJob" )
public class CreatePrepaidRefundJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {

        // refunds may take a few days to clear so make this a weekly job
        if (LocalDate.now().getDayOfWeek() != DayOfWeek.MONDAY) {
            LOGGER.info( "This job is currently configured to run only on Mondays, of which today is not. Nothing to do here." );
            return;
        }
        // login to BDC and check for any prepaid bookings we haven't charged yet
        cloudbedsService.getAllVCCBookingsThatMustBeRefunded()
                .stream()
                .forEach( r -> {
                    LOGGER.info( "Creating a PrepaidRefundJob for booking " + r );
                    PrepaidRefundJob refundJob = new PrepaidRefundJob();
                    refundJob.setStatus( JobStatus.submitted );
                    refundJob.setReservationId( r.getReservationId() );
                    refundJob.setAmount( r.getRefundAmount() );
                    refundJob.setReason( r.getReason() );
                    dao.insertJob( refundJob );
                } );
    }
}
