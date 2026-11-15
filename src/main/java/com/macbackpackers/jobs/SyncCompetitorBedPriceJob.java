package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.AnalyticsSyncService;

/**
 * Daily Google Hotels Edinburgh competitor (and own) lowest bed prices for the next N stay-dates.
 * Scheduled on the CRH processor only; other profiles no-op.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncCompetitorBedPriceJob" )
public class SyncCompetitorBedPriceJob extends AbstractJob {

    @Autowired
    @Transient
    private AnalyticsSyncService analyticsSyncService;

    @Override
    public void processJob() throws Exception {
        if ( false == analyticsSyncService.isCrhProfile() ) {
            LOGGER.info( "Skipping SyncCompetitorBedPriceJob on profile {} (CRH-only)",
                    analyticsSyncService.resolvePropertyCode() );
            return;
        }
        String city = StringUtils.defaultIfBlank( getParameter( "city" ), "Edinburgh" );
        int horizonDays = NumberUtils.toInt( getParameter( "horizon_days" ), 30 );
        int n = analyticsSyncService.syncCompetitorBedPrices( city, horizonDays );
        LOGGER.info( "SyncCompetitorBedPriceJob upserted {} row(s) city={} horizon_days={}",
                n, city, horizonDays );
    }
}
