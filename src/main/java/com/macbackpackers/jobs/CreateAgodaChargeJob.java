
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Job that creates Agoda charge jobs for the past X days.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateAgodaChargeJob" )
public class CreateAgodaChargeJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsScraper cbScraper;

    @Override
    public void processJob() throws Exception {

        if ( dao.isCloudbeds() ) {
            try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
                processJobForCloudbeds( webClient );
            }
        }
    }

    private void processJobForCloudbeds( WebClient webClient ) throws Exception {
        cbScraper.getReservationsForBookingSources( webClient, null, null,
                LocalDate.now().minusDays( getDaysBack() ), LocalDate.now(), "Agoda (Channel Collect Booking)" )
                .stream()
                .filter( p -> false == p.isPaid() )
                .filter( p -> "Agoda".equals( p.getSourceName() ) ) // sure to be sure
                .filter( p -> p.isChannelCollectBooking() )
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .filter( p -> p.isCheckinDateTodayOrInPast() )
                .forEach( p -> {
                    LOGGER.info( "Creating a PrepaidChargeJob for booking "
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + ")" );
                    LOGGER.info( p.getFirstName() + " " + p.getLastName() );
                    PrepaidChargeJob chargeJob = new PrepaidChargeJob();
                    chargeJob.setStatus( JobStatus.submitted );
                    chargeJob.setReservationId( p.getReservationId() );
                    dao.insertJob( chargeJob );
                } );
    }

    /**
     * Returns the number of days back to check for records.
     * 
     * @return number of days back to check
     */
    public int getDaysBack() {
        return Integer.parseInt( getParameter( "days_back" ) );
    }

    /**
     * Sets the number of days back to check for records.
     * 
     * @param daysBack number of days back to check
     */
    public void setDaysBack( int daysBack ) {
        setParameter( "days_back", String.valueOf( daysBack ) );
    }

}
