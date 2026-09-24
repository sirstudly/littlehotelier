package com.macbackpackers.services;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.macbackpackers.beans.ChannelStayNight;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;
import com.macbackpackers.scrapers.datainsights.DataInsightsStockReportParser;

/**
 * Syncs Channel Production stay-night facts from Data Insights into
 * {@code wp_lh_rpt_channel_stay_night}.
 */
@Service
public class ChannelStayNightSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger( ChannelStayNightSyncService.class );

    @Autowired
    private CloudbedsDataInsightsClient dataInsightsClient;

    @Autowired
    private DataInsightsStockReportParser parser;

    @Autowired
    private WordPressDAO dao;

    /**
     * Fetches stay-night facts for {@code [startDate, endDate]} and replaces existing
     * {@code di_occupancy} rows in that stay-date window (does not truncate older history).
     *
     * @return number of rows written
     */
    public int syncStayDateRange( WebClient webClient, LocalDate startDate, LocalDate endDate )
            throws IOException {
        if ( startDate == null || endDate == null || endDate.isBefore( startDate ) ) {
            throw new IllegalArgumentException( "Invalid stay date range: " + startDate + " .. " + endDate );
        }

        List<String> groups = CloudbedsDataInsightsClient.stayNightGroupColumns();
        JsonObject response = dataInsightsClient.queryChannelProduction(
                webClient, startDate, endDate, groups );

        List<String> groupColumns = CloudbedsDataInsightsClient.groupColumnsFromResponse( response );
        if ( groupColumns.isEmpty() ) {
            groupColumns = groups;
        }

        List<ChannelStayNight> rows = parser.parseQueryData(
                response, groupColumns, ChannelStayNight.ORIGIN_DI_OCCUPANCY );
        Timestamp fetchedAt = new Timestamp( System.currentTimeMillis() );
        for ( ChannelStayNight row : rows ) {
            row.setFetchedAt( fetchedAt );
        }

        LOGGER.info( "Replacing {} di_occupancy channel stay-night row(s) for stay_date [{} .. {}]",
                rows.size(), startDate, endDate );
        dao.replaceChannelStayNights( startDate, endDate, ChannelStayNight.ORIGIN_DI_OCCUPANCY, rows );
        return rows.size();
    }
}
