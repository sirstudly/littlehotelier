
package com.macbackpackers.jobs;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.CloudbedsService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

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
