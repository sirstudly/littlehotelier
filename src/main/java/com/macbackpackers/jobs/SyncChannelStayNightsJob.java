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
 * Refreshes {@code wp_lh_rpt_channel_stay_night} from Data Insights Channel Production for a
 * stay-date range. Replaces only {@code di_occupancy} rows inside the window — older history is kept.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncChannelStayNightsJob" )
public class SyncChannelStayNightsJob extends AbstractJob {

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
        LocalDate start = getStartDate();
        LocalDate end = getEndDate();
        int n = channelStayNightSyncService.syncStayDateRange( webClient, start, end );
        LOGGER.info( "SyncChannelStayNightsJob wrote {} row(s) for {} .. {}", n, start, end );
    }

    @Override
    public void finalizeJob() {
        webClient.close();
    }

    public LocalDate getStartDate() {
        return LocalDate.parse( getParameter( "start_date" ) );
    }

    public void setStartDate( LocalDate startDate ) {
        setParameter( "start_date", startDate.toString() );
    }

    public LocalDate getEndDate() {
        return LocalDate.parse( getParameter( "end_date" ) );
    }

    public void setEndDate( LocalDate endDate ) {
        setParameter( "end_date", endDate.toString() );
    }
}
