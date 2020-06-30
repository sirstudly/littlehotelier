
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates {@link PrepaidChargeJob}s for all prepaid bookings
 * with virtual CCs that are currently chargeable.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreatePrepaidChargeJob" )
public class CreatePrepaidChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {

        // find all BDC bookings with unpaid deposits that use a virtual CC
        // and create a job for each of them if they're currently chargeable
        dao.fetchPrepaidBDCBookingsWithOutstandingBalance()
                .stream()
                // include bookings that are about to arrive (in case we couldn't charge them yet)
                .filter( p -> p.isChargeableDateInPast() || p.isCheckinDateTodayOrTomorrow() )
                .forEach( a -> {
                    LOGGER.info( "Creating a PrepaidChargeJob for booking " + a.getBookingReference() 
                            + " chargeable on " + a.getEarliestChargeDate() );
                    PrepaidChargeJob chargeJob = new PrepaidChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( String.valueOf( a.getReservationId() ) );
                    dao.insertJob( chargeJob );
                } );

        // also, login to BDC and check for any prepaid bookings we haven't charged yet
        try {
            cloudbedsService.createBDCPrepaidChargeJobs();
        }
        catch ( Exception ex ) {
            setRetryCount( 1 ); // don't retry if we fail at this point
            throw ex;
        }
    }

}
