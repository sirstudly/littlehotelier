package com.macbackpackers.scrapers.datainsights;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
import com.google.gson.JsonParser;
import com.macbackpackers.scrapers.CloudbedsAccessTokenService;
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

    private static final DateTimeFormatter MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern( "MM-dd" );

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private CloudbedsAccessTokenService accessTokenService;

    @Autowired
    @Qualifier( "gsonForCloudbedsIdentity" )
    private Gson gson;

    private static final long TOKEN_EXPIRY_MARGIN_SEC = 300;

    private String cachedAccessToken;
    private long cachedAccessTokenExp;

    /**
     * Group columns for stay-night facts rolled up to Channel Production.
     * Stock report 191 allows at most 3 {@code group_rows} ("Length must be between 1 and 3").
     * Day × category × source is the finest CPR-compatible grain.
     */
    public static List<String> stayNightGroupColumns() {
        return Arrays.asList(
                "stay_date",
                "reservation_source_category",
                "reservation_source" );
    }

    /**
     * Group columns matching the default Channel Production UI (month × category × source).
     * Same three dimensions as {@link #stayNightGroupColumns()}; callers add {@code modifier:month}
     * on stay_date when needed.
     */
    public static List<String> monthlySourceGroupColumns() {
        return stayNightGroupColumns();
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

    /**
     * Queries Channel Production totals for a single calendar month of stay dates, grouped by
     * month × category × source (same as the default Channel Production UI).
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param month stay-date month
     * @return parsed JSON root of {@code /query/data}
     */
    public JsonObject queryChannelProductionByMonth( WebClient webClient, YearMonth month ) throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject body = buildChannelProductionBody( propertyId, month.atDay( 1 ), month.atEndOfMonth(),
                monthlySourceGroupColumns(), true );
        String url = DI_BASE + "/stock_reports/" + CHANNEL_PRODUCTION_STOCK_REPORT_ID
                + "/query/data?mode=Run&format=formatted";
        LOGGER.info( "Data Insights Channel Production monthly query property={} month={}", propertyId, month );
        return postJson( webClient, url, propertyId, body );
    }

    /**
     * Queries the classic Occupancy report for {@code [start, end]} (inclusive) within a single
     * {@code year}; results are broken down by month.
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param year report year (the API cannot span years)
     * @param start inclusive start within {@code year}
     * @param end inclusive end within {@code year}
     * @return parsed JSON root ({@code {"year":..., "results":[{"date":"MM","occupancy":..., ...}]}})
     */
    public JsonObject queryOccupancy( WebClient webClient, int year, MonthDay start, MonthDay end )
            throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject body = new JsonObject();
        body.addProperty( "report_year", String.valueOf( year ) );
        body.addProperty( "period_start", MONTH_DAY_FORMAT.format( start ) );
        body.addProperty( "period_end", MONTH_DAY_FORMAT.format( end ) );
        body.addProperty( "grouping", "month" );
        LOGGER.info( "Data Insights Occupancy query property={} year={} period=[{} .. {}]",
                propertyId, year, start, end );
        return postJson( webClient, DI_BASE + "/classic_reports/production_reports/rooms_sold", propertyId, body );
    }

    JsonObject buildChannelProductionBody( String propertyId, LocalDate startDate, LocalDate endDate,
            List<String> groupColumns ) {
        return buildChannelProductionBody( propertyId, startDate, endDate, groupColumns, false );
    }

    JsonObject buildChannelProductionBody( String propertyId, LocalDate startDate, LocalDate endDate,
            List<String> groupColumns, boolean groupStayDateByMonth ) {
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

        // Must match stock report 191 definition exactly ("Columns are not the same on stock report id 191").
        // Channel Production: room_revenue(sum), rooms_sold(sum), adr (no metrics — aggregated).
        JsonArray columns = new JsonArray();
        columns.add( metricColumn( "room_revenue", "sum" ) );
        columns.add( metricColumn( "rooms_sold", "sum" ) );
        columns.add( adrColumn() );
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
            if ( groupStayDateByMonth && "stay_date".equals( col ) ) {
                gr.addProperty( "modifier", "month" );
            }
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

    /** ADR on stock report 191 has no {@code metrics} array (server aggregates). */
    private static JsonObject adrColumn() {
        JsonObject c = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "adr" );
        c.add( "cdf", cdf );
        return c;
    }

    /**
     * Returns a cached access token, refreshing it (which rotates the one-time {@code rt} cookie)
     * only when missing or within {@link #TOKEN_EXPIRY_MARGIN_SEC} of expiry.
     */
    private synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis() / 1000;
        if ( cachedAccessToken == null || cachedAccessTokenExp < now + TOKEN_EXPIRY_MARGIN_SEC ) {
            CloudbedsAccessTokenService.AccessToken at = accessTokenService.refresh(
                    jsonRequestFactory.getCookies(), jsonRequestFactory.getUserAgent() );
            cachedAccessToken = at.getToken();
            cachedAccessTokenExp = parseJwtExp( cachedAccessToken );
        }
        return cachedAccessToken;
    }

    /** Reads the {@code exp} claim (epoch seconds) from a JWT, or 0 if it cannot be parsed. */
    static long parseJwtExp( String jwt ) {
        try {
            String[] parts = jwt.split( "\\." );
            String payload = new String( Base64.getUrlDecoder().decode( parts[1] ), StandardCharsets.UTF_8 );
            return JsonParser.parseString( payload ).getAsJsonObject().get( "exp" ).getAsLong();
        }
        catch ( RuntimeException e ) {
            LOGGER.warn( "Could not parse access token expiry: {}", e.getMessage() );
            return 0;
        }
    }

    private JsonObject postJson( WebClient webClient, String url, String propertyId, JsonObject body )
            throws IOException {
        // Data Insights (api.cloudbeds.com) authenticates with Bearer access token + X-Property-Id;
        // cookies alone → 401. The token is not a cookie (browser keeps it in localStorage).
        String accessToken = getAccessToken();

        WebRequest req = new WebRequest( new URL( url ), HttpMethod.POST );
        req.setAdditionalHeader( "Accept", "application/json" );
        req.setAdditionalHeader( "Content-Type", "application/json" );
        req.setAdditionalHeader( "Origin", "https://hotels.cloudbeds.com" );
        req.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/" );
        req.setAdditionalHeader( "X-Property-Id", propertyId );
        req.setAdditionalHeader( "Authorization", "Bearer " + accessToken );
        req.setAdditionalHeader( "User-Agent", jsonRequestFactory.getUserAgent() );
        req.setRequestBody( gson.toJson( body ) );

        Page page = webClient.getPage( req );
        String text = page.getWebResponse().getContentAsString( StandardCharsets.UTF_8 );
        int status = page.getWebResponse().getStatusCode();
        if ( status == 401 ) {
            synchronized ( this ) {
                cachedAccessToken = null;
            }
        }
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
