
package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.time.Month;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.services.CloudbedsService;

/**
 * Creates {@link ChargeHostelworldLateCancellationJob} for all applicable bookings cancelled
 * in the last few days.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateChargeHostelworldLateCancellationJob" )
public class CreateChargeHostelworldLateCancellationJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsService cbService;

    @Override
    @Transactional
    public void processJob() throws Exception {
        if ( dao.getOption( "siteurl" ).contains( "castlerock" )
                // may need adjustment depending on year
                && LocalDate.now().getMonth() == Month.AUGUST
                && LocalDate.now().getDayOfMonth() >= 2
                && LocalDate.now().getDayOfMonth() <= 26 ) {
            cbService.createChargeHostelworldLateCancellationJobsForAugust( cbWebClient,
                    LocalDate.now().minusDays( 5 ), LocalDate.now() );
        }
        else {
            cbService.createChargeHostelworldLateCancellationJobs( cbWebClient,
                    LocalDate.now().minusDays( 4 ), LocalDate.now() );
        }
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

}
