package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.AnalyticsSyncService;

/**
 * Daily Hostelworld consumer search position for CRH/HSH/RMB (Edinburgh).
 * Scheduled on the CRH processor only; other profiles no-op.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncHostelworldSearchRankJob" )
public class SyncHostelworldSearchRankJob extends AbstractJob {

    @Autowired
    @Transient
    private AnalyticsSyncService analyticsSyncService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        if ( false == analyticsSyncService.isCrhProfile() ) {
            LOGGER.info( "Skipping SyncHostelworldSearchRankJob on profile {} (CRH-only)",
                    analyticsSyncService.resolvePropertyCode() );
            return;
        }
        String city = StringUtils.defaultIfBlank( getParameter( "city" ), "Edinburgh" );
        int guests = NumberUtils.toInt( getParameter( "guests" ), 1 );
        int nights = NumberUtils.toInt( getParameter( "nights" ), 1 );
        int arrivalOffsetDays = NumberUtils.toInt( getParameter( "arrival_offset_days" ), 7 );
        try ( WebClient webClient = appContext.getBean( "webClientForHostelworld", WebClient.class ) ) {
            int n = analyticsSyncService.syncHostelworldSearchRanks(
                    webClient, city, guests, nights, arrivalOffsetDays );
            LOGGER.info(
                    "SyncHostelworldSearchRankJob upserted {} row(s) city={} guests={} nights={} offset={}",
                    n, city, guests, nights, arrivalOffsetDays );
        }
    }
}
