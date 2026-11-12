package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Phase 2 stub: Hostelworld consumer search position.
 * Not scheduled until a scraper exists.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncHostelworldSearchRankJob" )
public class SyncHostelworldSearchRankJob extends AbstractJob {

    @Override
    public void processJob() {
        LOGGER.info( "SyncHostelworldSearchRankJob is not implemented (Phase 2 stub); completing no-op" );
    }
}
