package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Phase 2 stub: competitor lowest bed prices (e.g. Google Hotel Search).
 * Not scheduled until a scraper exists.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncCompetitorBedPriceJob" )
public class SyncCompetitorBedPriceJob extends AbstractJob {

    @Override
    public void processJob() {
        LOGGER.info( "SyncCompetitorBedPriceJob is not implemented (Phase 2 stub); completing no-op" );
    }
}
