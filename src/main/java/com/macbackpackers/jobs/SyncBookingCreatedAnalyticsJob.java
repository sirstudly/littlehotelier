package com.macbackpackers.jobs;

import java.time.LocalDate;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.AnalyticsSyncService;

/**
 * Upserts {@code mac_analytics.fact_booking_created} for reservations booked in
 * {@code [start_date, end_date]} (inclusive, {@code yyyy-MM-dd}).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SyncBookingCreatedAnalyticsJob" )
public class SyncBookingCreatedAnalyticsJob extends AbstractJob {

    @Autowired
    @Transient
    private AnalyticsSyncService analyticsSyncService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            LOGGER.info( "Skipping booking-created analytics sync (not Cloudbeds)" );
            return;
        }
        LocalDate start = getStartDate();
        LocalDate end = getEndDate();
        if ( end.isBefore( start ) ) {
            throw new IllegalArgumentException( "end_date before start_date: " + start + " .. " + end );
        }
        try ( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            int n = analyticsSyncService.syncBookingCreated( webClient, start, end );
            LOGGER.info( "SyncBookingCreatedAnalyticsJob upserted {} row(s) for [{} .. {}]", n, start, end );
        }
    }

    public LocalDate getStartDate() {
        String raw = getParameter( "start_date" );
        if ( StringUtils.isBlank( raw ) ) {
            return LocalDate.now().minusDays( 1 );
        }
        return LocalDate.parse( raw );
    }

    public LocalDate getEndDate() {
        String raw = getParameter( "end_date" );
        if ( StringUtils.isBlank( raw ) ) {
            return getStartDate();
        }
        return LocalDate.parse( raw );
    }
}
