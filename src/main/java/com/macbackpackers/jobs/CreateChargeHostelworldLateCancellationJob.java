
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Creates {@link ChargeHostelworldLateCancellationJob} for all applicable bookings cancelled today
 * or yesterday.
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
        cbService.createChargeHostelworldLateCancellationJobs( cbWebClient,
                LocalDate.now().minusDays( 5 ), LocalDate.now() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

}
