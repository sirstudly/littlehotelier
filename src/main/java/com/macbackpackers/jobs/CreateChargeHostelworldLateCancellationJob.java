
package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.time.Month;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import com.gargoylesoftware.htmlunit.WebClient;
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
        // temporarily disabled due to covid-19
//        if ( dao.getOption( "siteurl" ).contains( "castlerock" ) && LocalDate.now().getMonth() == Month.AUGUST ) {
//            cbService.createChargeHostelworldLateCancellationJobsForAugust( cbWebClient,
//                    LocalDate.now().minusDays( 5 ), LocalDate.now() );
//        }
//        else {
            cbService.createChargeHostelworldLateCancellationJobs( cbWebClient,
                    LocalDate.now().minusDays( 4 ), LocalDate.now() );
//        }
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

}
