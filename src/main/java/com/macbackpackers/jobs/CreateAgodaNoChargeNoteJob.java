
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.macbackpackers.beans.JobStatus;

/**
 * Job that creates {@link AgodaNoChargeNoteJob}s.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAgodaNoChargeNoteJob" )
public class CreateAgodaNoChargeNoteJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.fetchAgodaBookingsMissingNoChargeNote()
                .stream()
                .forEach( p -> {
                    LOGGER.info( "Creating a AgodaNoChargeNoteJob for booking " + p.getBookingReference() );
                    AgodaNoChargeNoteJob noteJob = new AgodaNoChargeNoteJob();
                    noteJob.setStatus( JobStatus.submitted );
                    noteJob.setBookingRef( p.getBookingReference() );
                    noteJob.setBookedDate( p.getBookedDate() );
                    dao.insertJob( noteJob );
                } );
    }

}
