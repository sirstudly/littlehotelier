
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates {@link PrepaidChargeJob}s for all prepaid bookings with virtual CCs that are
 * currently chargeable.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreatePrepaidChargeJob" )
public class CreatePrepaidChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {

        // login to BDC and check for any prepaid bookings we haven't charged yet
        cloudbedsService.getAllVCCBookingsThatCanBeCharged()
                .stream()
                .forEach( r -> {
                    LOGGER.info( "Creating a PrepaidChargeJob for booking " + r );
                    PrepaidChargeJob chargeJob = new PrepaidChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( r );
                    dao.insertJob( chargeJob );
                } );
    }

    @Override
    public int getRetryCount() {
        return 1;
    }
}
