
package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.time.Month;

import com.macbackpackers.exceptions.UnrecoverableFault;
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
        final LocalDate now = LocalDate.now();
        if ( dao.getOption( "siteurl" ).contains( "castlerock" ) ) {
            // may need adjustment depending on year
            if ( now.getYear() > 2027 && now.getMonth() == Month.AUGUST ) {
                throw new UnrecoverableFault( "Fringe dates not set for year " + now.getYear() + ". Please update CreateChargeHostelworldLateCancellationJob." );
            }
            if ( ( now.getMonth() == Month.AUGUST
                    && now.getYear() == 2026
                    && now.getDayOfMonth() >= 2
                    && now.getDayOfMonth() <= 25 ) ||
                    ( now.getMonth() == Month.AUGUST
                            && now.getYear() == 2027
                            && now.getDayOfMonth() >= 7 /* && day <= 31 (implicit) */ ) ) {
                cbService.createChargeHostelworldLateCancellationJobsForAugust( cbWebClient,
                        now.minusDays( 5 ), now );
            }
            else {
                cbService.createChargeHostelworldLateCancellationJobs( cbWebClient,
                        now.minusDays( 4 ), now );
            }
        }
        else {
            cbService.createChargeHostelworldLateCancellationJobs( cbWebClient,
                    now.minusDays( 4 ), now );
        }
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

}
