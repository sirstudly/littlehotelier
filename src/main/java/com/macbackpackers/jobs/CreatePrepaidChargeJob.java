package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates {@link PrepaidChargeJob}s for prepaid bookings with virtual CCs that are
 * currently chargeable.
 * <p>
 * Sources: Cloudbeds today's prepaid BDC bookings, plus BDC VCC management entries whose
 * charge-before date is before today + 375 days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreatePrepaidChargeJob" )
public class CreatePrepaidChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {

        Set<String> reservationIds = cloudbedsService.getAllVCCBookingsThatCanBeCharged();
        // BDC VCC expire 1 year after checkout; include those that have recently checked out if there's a chargeable amount still
        cloudbedsService.getAllVCCBookingsThatCanBeCharged_LookupViaBDC( LocalDate.now().plusDays( 375 ) )
                .map( Reservation::getReservationId )
                .forEach( reservationIds::add );

        reservationIds.forEach( r -> {
            LOGGER.info( "Creating a PrepaidChargeJob for booking " + r );
            PrepaidChargeJob chargeJob = new PrepaidChargeJob();
            chargeJob.setStatus( JobStatus.submitted );
            chargeJob.setReservationId( r );
            dao.insertJob( chargeJob );
        } );
    }
}
