package com.macbackpackers.scrapers.datainsights;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;

/**
 * Client for Cloudbeds Data Insights stock reports (Channel Production = stock report 191).
 */
@Component
public class CloudbedsDataInsightsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsDataInsightsClient.class );

    /** Channel Production stock report (Occupancy dataset). */
    public static final int CHANNEL_PRODUCTION_STOCK_REPORT_ID = 191;

    public static final String MULTI_LEVEL_SOURCE = "4";

    private static final String DI_BASE = "https://api.cloudbeds.com/datainsights/v1.1";

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    @Qualifier( "gsonForCloudbedsIdentity" )
    private Gson gson;

    /**
     * Group columns for stay-night raw facts (day × category × source × booking × room).
     */
    public static List<String> stayNightGroupColumns() {
        return Arrays.asList(
                "stay_date",
                "reservation_source_category",
                "reservation_source",
                "booking_id",
                "room_id" );
    }

    /**
     * Group columns matching the default Channel Production UI (month × category × source).
     */
    public static List<String> monthlySourceGroupColumns() {
        return Arrays.asList(
                "stay_date",
                "reservation_source_category",
                "reservation_source" );
    }

    /**
     * Queries Channel Production for stay dates in {@code [startDate, endDate]} (inclusive)
     * using absolute ISO filters (same pattern as classic cancellation report).
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param startDate inclusive
     * @param endDate inclusive
     * @param groupColumns DI group_rows column names (no month modifier — day grain)
     * @return parsed JSON root of {@code /query/data}
     */
    public JsonObject queryChannelProduction( WebClient webClient, LocalDate startDate, LocalDate endDate,
            List<String> groupColumns ) throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject body = buildChannelProductionBody( propertyId, startDate, endDate, groupColumns );
        String url = DI_BASE + "/stock_reports/" + CHANNEL_PRODUCTION_STOCK_REPORT_ID
                + "/query/data?mode=Run&format=formatted";
        LOGGER.info( "Data Insights Channel Production query property={} stay=[{} .. {}] groups={}",
                propertyId, startDate, endDate, groupColumns );
        return postJson( webClient, url, propertyId, body );
    }

    JsonObject buildChannelProductionBody( String propertyId, LocalDate startDate, LocalDate endDate,
            List<String> groupColumns ) {
        JsonObject body = new JsonObject();
        JsonArray propertyIds = new JsonArray();
        propertyIds.add( propertyId );
        body.add( "property_ids", propertyIds );

        JsonObject filters = new JsonObject();
        JsonArray and = new JsonArray();
        and.add( stayDateFilter( "greater_than_or_equal", startDate.toString() ) );
        // exclusive upper bound on the day after endDate
        and.add( stayDateFilter( "less_than", endDate.plusDays( 1 ).toString() ) );
        and.add( sourceFilter( "reservation_source" ) );
        and.add( sourceFilter( "reservation_source_category" ) );
        and.add( statusFilterAll() );
        filters.add( "and", and );
        body.add( "filters", filters );

        JsonArray columns = new JsonArray();
        columns.add( metricColumn( "room_revenue", "sum" ) );
        columns.add( metricColumn( "rooms_sold", "sum" ) );
        columns.add( metricColumn( "room_rate", "sum" ) );
        body.add( "columns", columns );

        JsonObject settings = new JsonObject();
        settings.addProperty( "details", false );
        settings.addProperty( "totals", false );
        settings.addProperty( "subtotals", false );
        settings.addProperty( "transpose", false );
        body.add( "settings", settings );

        JsonArray groupRows = new JsonArray();
        for ( String col : groupColumns ) {
            JsonObject gr = new JsonObject();
            JsonObject cdf = new JsonObject();
            cdf.addProperty( "type", "default" );
            cdf.addProperty( "column", col );
            if ( col.startsWith( "reservation_source" ) ) {
                cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
            }
            gr.add( "cdf", cdf );
            // no "modifier":"month" — keep calendar-day grain when stay_date is grouped
            groupRows.add( gr );
        }
        body.add( "group_rows", groupRows );
        return body;
    }

    private static JsonObject stayDateFilter( String operator, String value ) {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "stay_date" );
        f.add( "cdf", cdf );
        f.addProperty( "value", value );
        f.addProperty( "operator", operator );
        return f;
    }

    private static JsonObject sourceFilter( String column ) {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", column );
        cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
        f.add( "cdf", cdf );
        f.addProperty( "value", "" );
        f.addProperty( "operator", "all" );
        return f;
    }

    private static JsonObject statusFilterAll() {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "reservation_status" );
        cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
        f.add( "cdf", cdf );
        f.add( "value", new JsonArray() );
        f.addProperty( "operator", "all" );
        return f;
    }

    private static JsonObject metricColumn( String column, String metric ) {
        JsonObject c = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", column );
        c.add( "cdf", cdf );
        JsonArray metrics = new JsonArray();
        metrics.add( metric );
        c.add( "metrics", metrics );
        return c;
    }

    private JsonObject postJson( WebClient webClient, String url, String propertyId, JsonObject body )
            throws IOException {
        WebRequest req = new WebRequest( new URL( url ), HttpMethod.POST );
        req.setAdditionalHeader( "Accept", "application/json" );
        req.setAdditionalHeader( "Content-Type", "application/json" );
        req.setAdditionalHeader( "Origin", "https://hotels.cloudbeds.com" );
        req.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/" );
        req.setAdditionalHeader( "X-Property-Id", propertyId );
        req.setAdditionalHeader( "User-Agent", jsonRequestFactory.getUserAgent() );
        String cookies = jsonRequestFactory.getCookies();
        if ( StringUtils.isNotBlank( cookies ) ) {
            req.setAdditionalHeader( "Cookie", cookies );
        }
        req.setRequestBody( gson.toJson( body ) );

        Page page = webClient.getPage( req );
        String text = page.getWebResponse().getContentAsString( StandardCharsets.UTF_8 );
        int status = page.getWebResponse().getStatusCode();
        if ( status < 200 || status >= 300 ) {
            throw new IOException( "Data Insights HTTP " + status + ": " + StringUtils.left( text, 500 ) );
        }
        return gson.fromJson( text, JsonObject.class );
    }

    /**
     * Extracts ordered group column names from a request body or response {@code group_rows}.
     */
    public static List<String> groupColumnsFromResponse( JsonObject root ) {
        List<String> cols = new ArrayList<>();
        if ( root == null || !root.has( "group_rows" ) || !root.get( "group_rows" ).isJsonArray() ) {
            return cols;
        }
        for ( var el : root.getAsJsonArray( "group_rows" ) ) {
            if ( el.isJsonPrimitive() ) {
                cols.add( el.getAsString() );
            }
            else if ( el.isJsonObject() ) {
                JsonObject o = el.getAsJsonObject();
                if ( o.has( "cdf" ) && o.getAsJsonObject( "cdf" ).has( "column" ) ) {
                    cols.add( o.getAsJsonObject( "cdf" ).get( "column" ).getAsString() );
                }
            }
        }
        return cols;
    }

    /** Convenience for tests / logging: flatten expected month totals from a parsed response. */
    public static Map<String, BigDecimalPair> sumByNormalizedSource(
            List<com.macbackpackers.beans.ChannelStayNight> rows,
            com.macbackpackers.services.BookingSourceNormalizer normalizer ) {
        Map<String, BigDecimalPair> map = new LinkedHashMap<>();
        for ( var row : rows ) {
            String src = row.getBookingSource();
            if ( src == null ) {
                continue;
            }
            BigDecimalPair p = map.computeIfAbsent( src, k -> new BigDecimalPair() );
            p.revenue = p.revenue.add( row.getRoomRevenue() );
            p.roomsSold += row.getRoomsSold();
        }
        return map;
    }

    public static final class BigDecimalPair {
        public java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
        public int roomsSold;
    }
}
