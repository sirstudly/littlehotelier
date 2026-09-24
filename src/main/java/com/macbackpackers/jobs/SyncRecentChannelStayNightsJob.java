package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.ChannelStayNightSyncService;

/**
 * Sliding-window refresh of channel stay-night facts (default: 60 days back, 90 days ahead).
 * Safe to schedule daily; does not wipe history outside the window.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncRecentChannelStayNightsJob" )
public class SyncRecentChannelStayNightsJob extends AbstractJob {

    private static final int DEFAULT_LOOKBACK_DAYS = 60;
    private static final int DEFAULT_LOOKAHEAD_DAYS = 90;

    @Autowired
    @Transient
    private ChannelStayNightSyncService channelStayNightSyncService;

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays( getLookbackDays() );
        LocalDate end = today.plusDays( getLookaheadDays() );
        int n = channelStayNightSyncService.syncStayDateRange( webClient, start, end );
        LOGGER.info( "SyncRecentChannelStayNightsJob wrote {} row(s) for {} .. {}", n, start, end );
    }

    @Override
    public void finalizeJob() {
        webClient.close();
    }

    public int getLookbackDays() {
        String v = getParameter( "lookback_days" );
        return v == null ? DEFAULT_LOOKBACK_DAYS : Integer.parseInt( v );
    }

    public void setLookbackDays( int days ) {
        setParameter( "lookback_days", String.valueOf( days ) );
    }

    public int getLookaheadDays() {
        String v = getParameter( "lookahead_days" );
        return v == null ? DEFAULT_LOOKAHEAD_DAYS : Integer.parseInt( v );
    }

    public void setLookaheadDays( int days ) {
        setParameter( "lookahead_days", String.valueOf( days ) );
    }
}
