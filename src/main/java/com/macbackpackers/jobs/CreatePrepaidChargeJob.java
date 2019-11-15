
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.macbackpackers.beans.JobStatus;

/**
 * Job that creates {@link PrepaidChargeJob}s for all prepaid bookings
 * with virtual CCs that are currently chargeable.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreatePrepaidChargeJob" )
public class CreatePrepaidChargeJob extends AbstractJob {

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
    }

}
